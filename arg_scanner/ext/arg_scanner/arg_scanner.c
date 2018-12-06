#include "arg_scanner.h"
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdarg.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <glib.h>

//#define DEBUG_ARG_SCANNER 1

#if RUBY_API_VERSION_CODE >= 20500
  #if (RUBY_RELEASE_YEAR == 2017 && RUBY_RELEASE_MONTH == 10 && RUBY_RELEASE_DAY == 10) //workaround for 2.5.0-preview1
    #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->ec.cfp)
  #else
    #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->ec->cfp)
  #endif
#else
  #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->cfp)
#endif

#ifdef DEBUG_ARG_SCANNER
    #define LOG(f, args...) { fprintf(stderr, "DEBUG: '%s'=", #args); fprintf(stderr, f, ##args); fflush(stderr); }
#else
    #define LOG(...) {}
#endif

#define ruby_current_thread ((rb_thread_t *)RTYPEDDATA_DATA(rb_thread_current()))
typedef struct rb_trace_arg_struct rb_trace_arg_t;

VALUE mArgScanner = Qnil;
int types_ids[20];

static VALUE c_signature;

typedef struct
{
    ssize_t call_info_argc;
    char* call_info_kw_args;
} call_info_t;

typedef struct
{
    char *receiver_name;
    char *method_name;
    char *args_info;
    char *path;
    char *call_info_kw_args;
    ssize_t call_info_argc;
    int lineno;
    char *return_type_name;
} signature_t;

void Init_arg_scanner();

static const char *EMPTY_VALUE = "";
static const int SERVER_DEFAULT_PORT = 7777;
static GTree *sent_to_server_tree;
static int socket_fd = -1;
static char* get_args_info();
static VALUE set_server_port(VALUE self, VALUE server_port);
static VALUE handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path);
static VALUE handle_return(VALUE self, VALUE signature, VALUE receiver_name, VALUE return_type_name);
static VALUE destructor(VALUE self);

// returns Qnil if ready; or string containing error message otherwise 
static VALUE check_if_arg_scanner_ready(VALUE self);
// errno status after socket creation attempt
static int socket_errno = 0;

// For testing
static VALUE get_args_info_rb(VALUE self);
static VALUE get_call_info_rb(VALUE self);

static call_info_t* get_call_info();
static bool is_call_info_needed();

static void call_info_t_free(void *s)
{
    if (((call_info_t *)s)->call_info_kw_args != NULL)
        free(((call_info_t *)s)->call_info_kw_args);
    free(s);
}

static void signature_t_free(signature_t *s)
{
    free(s->receiver_name);
    free(s->method_name);
    free(s->args_info);
    free(s->path);
    free(s->call_info_kw_args);
    free(s->return_type_name);
    free(s);
}

// Free signature_t partially leaving parts that are used while comparison (compare_signature_t)
// @see_also compare_signature_t
static void signature_t_free_partially(signature_t *s)
{
    free(s->receiver_name);
    s->receiver_name = NULL;

    free(s->method_name);
    s->method_name = NULL;

    free(s->call_info_kw_args);
    s->call_info_kw_args = NULL;
}

// Just one possible comparator. Feel free to change the way it compare (but do it meaningfully and
// don't forget to change signature_t_free_partially accordingly)
// @see_also signature_t_free_partially
static int
compare_signature_t(const signature_t *a, const signature_t *b) {
    int ret;

    ret = a->lineno - b->lineno;
    if (ret != 0) return ret;

    ret = strcmp(a->path, b->path);
    if (ret != 0) return ret;

    ret = strcmp(a->args_info != NULL ? a->args_info : "", b->args_info != NULL ? b->args_info : "");
    if (ret != 0) return ret;

    ret = strcmp(a->return_type_name, b->return_type_name);
    if (ret != 0) return ret;

    return 0;
}

// returns zero if no errors occured
int init_socket(int server_port) {
    struct sockaddr_in serv_addr;
    socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_fd < 0) {
        return 1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(server_port);

    if(inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) != 1) {
        return 1;
    }

    return connect(socket_fd, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
}

void Init_arg_scanner() {
    mArgScanner = rb_define_module("ArgScanner");
    rb_define_module_function(mArgScanner, "handle_call", handle_call, 3);
    rb_define_module_function(mArgScanner, "handle_return", handle_return, 3);
    rb_define_module_function(mArgScanner, "get_args_info", get_args_info_rb, 0);
    rb_define_module_function(mArgScanner, "get_call_info", get_call_info_rb, 0);
    rb_define_module_function(mArgScanner, "destructor", destructor, 0);
    rb_define_module_function(mArgScanner, "check_if_arg_scanner_ready", check_if_arg_scanner_ready, 0);
    rb_define_module_function(mArgScanner, "set_server_port", set_server_port, 1);

    sent_to_server_tree = g_tree_new_full(/*key_compare_func =*/compare_signature_t,
                                          /*key_compare_data =*/NULL,
                                          /*key_destroy_func =*/signature_t_free,
                                          /*value_destroy_func =*/NULL);
}

rb_control_frame_t *
my_rb_vm_get_binding_creatable_next_cfp(const rb_thread_t *th, const rb_control_frame_t *cfp)
{
    while (!RUBY_VM_CONTROL_FRAME_STACK_OVERFLOW_P(th, cfp)) {
        if (cfp->iseq) {
            return (rb_control_frame_t *)cfp;
        }
	    cfp = RUBY_VM_PREVIOUS_CONTROL_FRAME(cfp);
    }
    return 0;
}

static VALUE
set_server_port(VALUE self, VALUE server_port)
{
    int server_port_c = FIX2INT(server_port);
    server_port_c = (server_port_c == 0 ? SERVER_DEFAULT_PORT : server_port_c);

    if (init_socket(server_port_c)) {
        socket_errno = errno;
    }
    return Qnil;
}

static VALUE
handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path)
{
    // Just for code safety
    if (lineno == Qnil || method_name == Qnil || path == Qnil) {
        return Qnil;
    }
    signature_t *sign = (signature_t *) calloc(1, sizeof(*sign));

    sign->lineno = FIX2INT(lineno); // Convert Ruby's Fixnum to C language int
    sign->method_name = strdup(StringValueCStr(method_name));
    sign->path = strdup(StringValueCStr(path));

#ifdef DEBUG_ARG_SCANNER
    LOG("Getting args info for %s %s %d \n", sign->method_name, sign->path, sign->lineno);
#endif
    sign->args_info = get_args_info();

    if (sign->args_info != NULL && strlen(sign->args_info) >= 1000) {
        signature_t_free(sign);
        return Qnil;
    }

    if (is_call_info_needed())
    {
        call_info_t *info = get_call_info();

        sign->call_info_argc = info->call_info_argc;
        sign->call_info_kw_args = info->call_info_kw_args;

        free(info);
    } else {
        sign->call_info_argc = -1;
        sign->call_info_kw_args = NULL;
    }

    return Data_Wrap_Struct(c_signature,
        NULL, /*free memory function is NULL because sent_to_server_tree will free it (see handle_return)*/NULL, sign);
}

static VALUE
handle_return(VALUE self, VALUE signature, VALUE receiver_name, VALUE return_type_name)
{
    if (signature == Qnil || receiver_name == Qnil || return_type_name == Qnil) {
        return Qnil;
    }

    signature_t *sign;
    Data_Get_Struct(signature, signature_t, sign);
    sign->receiver_name = strdup(StringValueCStr(receiver_name));
    sign->return_type_name = strdup(StringValueCStr(return_type_name));

    if (g_tree_lookup(sent_to_server_tree, sign) == NULL) {
        // GTree will free memory allocated by sign by itself
        g_tree_insert(sent_to_server_tree, /*key = */sign, /*value = */EMPTY_VALUE);

        char json[2048];
        size_t json_size = sizeof(json) / sizeof(*json);

        // json_len doesn't count terminating '\0'
        int json_len = snprintf(json, json_size,
            "{\"method_name\":\"%s\",\"call_info_argc\":\"%d\",\"call_info_kw_args\":\"%s\",\"args_info\":\"%s\""
            ",\"visibility\":\"%s\",\"path\":\"%s\",\"lineno\":\"%d\",\"receiver_name\":\"%s\",\"return_type_name\":\"%s\"}\n",
            sign->method_name,
            sign->call_info_argc,
            sign->call_info_kw_args != NULL ? sign->call_info_kw_args : "",
            sign->args_info != NULL ? sign->args_info : "",
            "PUBLIC",
            sign->path,
            sign->lineno,
            sign->receiver_name,
            sign->return_type_name);
        // if json_len >= json_size then it means that string in snprintf truncated output
        if (json_len >= json_size) {
            return Qnil;
        }

        signature_t_free_partially(sign);

        send(socket_fd, json, json_len, 0);
    } else {
        signature_t_free(sign);
    }
    return Qnil;
}

static call_info_t*
get_call_info()
{
    rb_thread_t *thread = ruby_current_thread;
    rb_control_frame_t *cfp = TH_CFP(thread);
    call_info_t *info = (call_info_t *) malloc(sizeof(*info));

    //info = ALLOC(call_info_t);
    //info = malloc;

    info->call_info_argc = -1;
    info->call_info_kw_args = NULL;

    cfp += 4;
    cfp = my_rb_vm_get_binding_creatable_next_cfp(thread, cfp);

    if(cfp->iseq != NULL)
    {
        if(cfp->pc == NULL || cfp->iseq->body == NULL)
        {
            return info;
        }

        const rb_iseq_t *iseq;
        iseq = (const rb_iseq_t *) cfp->iseq;

        ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;

        const VALUE *iseq_original = rb_iseq_original_iseq((rb_iseq_t *)iseq);

        int indent = 1;

        for(; indent < 6; indent++)
        {
            VALUE insn = iseq_original[pc - indent];
            int tmp = (int)insn;

            if(0 < tmp && tmp < 256)
            {
                if(indent < 3)
                    return info;

                struct rb_call_info *ci = (struct rb_call_info *)iseq_original[pc - indent + 1];

                info->call_info_argc = ci->orig_argc;

                if (ci->flag & VM_CALL_KWARG)
                {
                    struct rb_call_info_kw_arg *kw_args = ((struct rb_call_info_with_kwarg *)ci)->kw_arg;

                    size_t kwArgSize = kw_args->keyword_len;

                    VALUE kw_ary = rb_ary_new_from_values(kw_args->keyword_len, kw_args->keywords);
                    const char *c_kw_ary[kwArgSize];

                    size_t ans_size = 0;
                    int j;

                    for(j = kwArgSize - 1; j >= 0; j--)
                    {
                        VALUE kw = rb_ary_pop(kw_ary);
                        const char* kw_name = rb_id2name(SYM2ID(kw));

                        c_kw_ary[j] = kw_name;
                        ans_size += strlen(kw_name);

                        if((size_t)j + 1 < kwArgSize)
                            ans_size++;
                    }

                    info->call_info_kw_args = (char*)malloc(ans_size + 1);

                    if(kwArgSize > 0)
                    {
                        strcpy(info->call_info_kw_args, c_kw_ary[0]);

                        if(kwArgSize > 1)
                            strcat(info->call_info_kw_args, ",");
                    }

                    for(j = 1; (size_t)j < kwArgSize; j++)
                    {
                        strcat(info->call_info_kw_args, c_kw_ary[j]);

                        if((size_t)j + 1 < kwArgSize)
                            strcat(info->call_info_kw_args, ",");
                    }
                }
                return info;
            }
        }
    }
    return info;
}

static const char*
calc_sane_class_name(VALUE ptr)
{
    VALUE klass = rb_obj_class(ptr);

    const char* klass_name;
    // may be false, see `object.c#rb_class_get_superclass`
    if (klass == Qfalse) {
        klass_name = "<err>";
    }
    else
    {
        klass_name = rb_class2name(klass);
    }

    // returned value may be NULL, see `variable.c#rb_class2name`
    if (klass_name == NULL)
    {
        klass_name = "<err>";
    }

    return klass_name;
}

static char *
fast_join_array(char sep, size_t count, const char **strings)
{
    size_t lengths[count + 1];
    size_t i;
    char *result;

    lengths[0] = 0;

    for (i = 0; i < count; i++)
    {
        const char *str = strings[i];
        size_t length;
        if (!str)
            length = 0;
        else
            length = strlen(str) + (i > 0); // 1 for separator before

        lengths[i + 1] = lengths[i] + length;
    }

    result = (char *)malloc(sizeof(*result) * (1 + lengths[count]));

    for (i = 0; i < count; i++)
    {
        const char *str = strings[i];
        if (str)
        {
            int start = lengths[i];
            if (i > 0)
                result[start++] = sep;

            memcpy(result + start, str, sizeof(*result) * (lengths[i + 1] - start));
        }
    }

    result[lengths[count]] = 0;

    return result;
}

static char *
fast_join(char sep, size_t count, ...)
{
    char *strings[count];
    size_t i;
    va_list ap;

    va_start(ap, count);
    for (i = 0; i < count; i++)
    {
        strings[i] = va_arg(ap, char *);
    }
    va_end(ap);

    return fast_join_array(sep, count, strings);
}

static char*
get_args_info()
{
    rb_thread_t *thread;
    rb_control_frame_t *cfp;

    thread = ruby_current_thread;
    cfp = TH_CFP(thread);

    cfp += 3;

    VALUE *ep = cfp->ep;
    ep -= cfp->iseq->body->local_table_size;

    size_t param_size = cfp->iseq->body->param.size;
    size_t lead_num = cfp->iseq->body->param.lead_num;
    size_t opt_num = cfp->iseq->body->param.opt_num;
    size_t post_num = cfp->iseq->body->param.post_num;

    unsigned int has_rest = cfp->iseq->body->param.flags.has_rest;
    unsigned int has_kw = cfp->iseq->body->param.flags.has_kw;
    unsigned int has_kwrest = cfp->iseq->body->param.flags.has_kwrest;
    unsigned int has_block = cfp->iseq->body->param.flags.has_block;

    LOG("%d\n", param_size);
    LOG("%d\n", lead_num);
    LOG("%d\n", opt_num);
    LOG("%d\n", post_num);

    LOG("%d\n", has_rest);
    LOG("%d\n", has_kw);
    LOG("%d\n", has_kwrest);
    LOG("%d\n", has_block);

    if(param_size == 0)
        return 0;

    const char **types = (const char **)malloc(param_size * sizeof(*types));
    size_t i, ans_iterator;
    int types_iterator;

    ans_iterator = 0;

    int new_version_flag = strcmp(RUBY_VERSION, "2.4.0") >= 0 ? 1 : 0;
    LOG("%d\n", new_version_flag);

    for(i = param_size - 1 - new_version_flag, types_iterator = 0; (size_t)types_iterator < param_size; i--, types_iterator++)
    {
        types[types_iterator] = calc_sane_class_name(*(ep + i - 1));
        types_ids[types_iterator] = i - 1;
        LOG("Type #%d=%s\n", types_iterator, types[types_iterator])
    }

    types_iterator--;

    if(has_kw)
        param_size--;

    char **ans = (char **)malloc(param_size * sizeof(*ans));

    for(i = 0; i < lead_num; i++, ans_iterator++, types_iterator--)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 3, "REQ", types[types_iterator], name);
    }

    for(i = 0; i < opt_num; i++, ans_iterator++, types_iterator--)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 3, "OPT", types[types_iterator], name);
    }

    for(i = 0; i < has_rest; i++, ans_iterator++, types_iterator--)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 3, "REST", types[types_iterator], name);
    }

    for(i = 0; i < post_num; i++, ans_iterator++, types_iterator--)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 3, "POST", types[types_iterator], name);
    }


    if(cfp->iseq->body->param.keyword != NULL)
    {
        const ID *keywords = cfp->iseq->body->param.keyword->table;
        size_t kw_num = cfp->iseq->body->param.keyword->num;
        size_t required_num = cfp->iseq->body->param.keyword->required_num;
        size_t rest_start = cfp->iseq->body->param.keyword->rest_start;

        LOG("%d %d\n", kw_num, required_num)

        for(i = 0; i < required_num; i++, ans_iterator++, types_iterator--)
        {
            ID key = keywords[i];
            ans[ans_iterator] = fast_join(',', 3, "KEYREQ", types[types_iterator], rb_id2name(key));
        }
        for(i = required_num; i < kw_num; i++, ans_iterator++, types_iterator--)
        {
            ID key = keywords[i];
            ans[ans_iterator] = fast_join(',', 3, "KEY", types[types_iterator], rb_id2name(key));
        }

        if(param_size - has_block > 1 && has_kwrest && TYPE(*(ep + types_ids[types_iterator])) == T_FIXNUM)
            types_iterator--;

        for(i = 0; i < has_kwrest; i++, ans_iterator++, types_iterator--)
        {
            const char *name = rb_id2name(cfp->iseq->body->local_table[rest_start]);

            ans[ans_iterator] = fast_join(',', 3, "KEYREST", types[types_iterator], name);
        }
    }

    for(i = 0; i < has_block; i++, ans_iterator++, types_iterator--)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 3, "BLOCK", types[types_iterator], name);
    }

    LOG("%d\n", ans_iterator)
    char *answer = fast_join_array(';', ans_iterator, ans);

    for(i = 0; i < ans_iterator; i++) {
        LOG("free2 %d %d =%s= \n", ans[i], strlen(ans[i]), ans[i]);
        free(ans[i]);
    }

    LOG("%d %d %d", ans_iterator, param_size, types_iterator);
    assert(ans_iterator == param_size);
    assert(types_iterator <= 0);

    free(types);
    free(ans);

    return answer;
}

static VALUE
get_args_info_rb(VALUE self)
{
    char *args_info = get_args_info();
    VALUE ret = args_info ? rb_str_new_cstr(args_info) : Qnil;
    free(args_info);
    return ret;
}

static VALUE
get_call_info_rb(VALUE self)
{
    if (is_call_info_needed())
    {
        call_info_t *info = get_call_info();

        VALUE ans;
        ans = rb_ary_new();
        rb_ary_push(ans, LONG2FIX(info->call_info_argc));
        if (info->call_info_kw_args != NULL) {
            rb_ary_push(ans, rb_str_new_cstr(info->call_info_kw_args));
        }

        call_info_t_free(info);

        return ans;
    }
    else
    {
        return Qnil;
    }
}

static bool
is_call_info_needed()
{
    rb_thread_t *thread;
    rb_control_frame_t *cfp;

    thread = ruby_current_thread;
    cfp = TH_CFP(thread);
    cfp += 3;

    return (cfp->iseq->body->param.flags.has_opt
        || cfp->iseq->body->param.flags.has_kwrest
        || cfp->iseq->body->param.flags.has_rest
        || (cfp->iseq->body->param.keyword != NULL && cfp->iseq->body->param.keyword->required_num == 0));
}

static VALUE 
check_if_arg_scanner_ready(VALUE self) {
    if (socket_errno != 0) {
        char error_msg[1024];
        snprintf(error_msg, sizeof(error_msg)/sizeof(*error_msg), 
            "Are sure you've run server?, error message: %s", strerror(socket_errno));
        return rb_str_new_cstr(error_msg);
    }
    return Qnil;
}

static VALUE
destructor(VALUE self) {
    g_tree_destroy(sent_to_server_tree);
    close(socket_fd);
    return Qnil;
}
