#include "arg_scanner.h"
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdarg.h>

//#define DEBUG_ARG_SCANNER 1

#if RUBY_API_VERSION_CODE >= 20500
    #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->ec.cfp)
#else
    #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->cfp)
#endif

#ifdef DEBUG_ARG_SCANNER
    #define LOG(f, args...) { fprintf(stderr, "DEBUG: '%s'=", #args); fprintf(stderr, f, ##args); fflush(stderr); }
#else
    #define LOG(...) {}
#endif


#if RUBY_API_VERSION_CODE >= 20500
    #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->ec.cfp)
#else
    #define TH_CFP(thread) ((rb_control_frame_t *)(thread)->cfp)
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
    long method_name_id;
    char* args_info;
    VALUE path;
    char* call_info_kw_args;
    ssize_t call_info_argc;
    int lineno;
} signature_t;

void Init_arg_scanner();

static char* get_args_info();
static VALUE handle_call(VALUE self, VALUE lineno, VALUE rb_method_name_id, VALUE path);
static VALUE handle_return(VALUE self, VALUE signature, VALUE return_type_name);
static VALUE get_param_info_rb(VALUE self);

// For testing
static VALUE get_args_info_rb(VALUE self);
static VALUE get_call_info_rb(VALUE self);

static call_info_t* get_call_info();
static bool is_call_info_needed();

static void call_info_t_free(void *s)
{
    if (((call_info_t *)s)->call_info_kw_args != 0)
        free(((call_info_t *)s)->call_info_kw_args);
    free(s);
}

static void signature_t_free(void *s)
{
    if (((signature_t *)s)->args_info != 0)
        free(((signature_t *)s)->args_info);
    if (((signature_t *)s)->call_info_kw_args != 0)
        free(((signature_t *)s)->call_info_kw_args);
    free(s);
}

static void
signature_t_mark(signature_t *sig)
{
    rb_gc_mark(sig->path);
}

void Init_arg_scanner() {
    mArgScanner = rb_define_module("ArgScanner");
    rb_define_module_function(mArgScanner, "handle_call", handle_call, 3);
    rb_define_module_function(mArgScanner, "handle_return", handle_return, 2);
    rb_define_module_function(mArgScanner, "get_args_info", get_args_info_rb, 0);
    rb_define_module_function(mArgScanner, "get_param_info", get_param_info_rb, 0);
    rb_define_module_function(mArgScanner, "get_call_info", get_call_info_rb, 0);
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
handle_call(VALUE self, VALUE lineno, VALUE rb_method_name_id, VALUE path)
{
    //VALUE method_sym = rb_tracearg_method_id(trace_arg);
    //VALUE path = trace_arg->path;
    long method_name_id = FIX2LONG(rb_method_name_id);
    VALUE c_path = path;
    int c_lineno = FIX2INT(lineno);//trace_arg->lineno;

    signature_t *sign;
    sign = ALLOC(signature_t);

    sign->lineno = c_lineno;
    sign->method_name_id = method_name_id;
    sign->path = c_path;

#ifdef DEBUG_ARG_SCANNER
    LOG("Getting args info for %d %s %d \n", sign->method_name_id, StringValuePtr(sign->path), sign->lineno);
#endif
    sign->args_info = get_args_info();

    if (is_call_info_needed())
    {
        call_info_t *info = get_call_info();

        sign->call_info_argc = info->call_info_argc;
        sign->call_info_kw_args = info->call_info_kw_args;

        free(info);
    } else {
        sign->call_info_argc = -1;
        sign->call_info_kw_args = 0;
    }

    //return Data_Wrap_Struct(c_signature, signature_t_mark, signature_t_free, sign);
    return Data_Wrap_Struct(c_signature, signature_t_mark, xfree, sign);
}

static VALUE
handle_return(VALUE self, VALUE signature, VALUE return_type_name)
{
    signature_t *sign;
    const char *args_info;
    const char *call_info_kw_args;
    char json_mes[2000];

    Data_Get_Struct(signature, signature_t, sign);

    args_info = sign->args_info;
    if (!args_info)
        args_info = "";
    call_info_kw_args = sign->call_info_kw_args;
    if (!call_info_kw_args)
        call_info_kw_args = "";


#ifdef DEBUG_ARG_SCANNER
    LOG("%d \n", sign->method_name_id);
    LOG("%d \n", sign->call_info_argc);
    LOG("%s \n", call_info_kw_args);
    LOG("%s \n", args_info);
    LOG("%s \n", StringValuePtr(sign->path));
    LOG("%d \n", sign->lineno);
#endif

    assert(strlen(args_info) < 1000);

    snprintf(json_mes, 2000,
        "{\"method_id\":\"%d\",\"call_info_argc\":\"%d\",\"call_info_kw_args\":\"%s\",\"args_info\":\"%s\",",
        sign->method_name_id,
        sign->call_info_argc,
        call_info_kw_args,
        args_info);

    LOG("%s \n", json_mes);

    return rb_str_new_cstr(json_mes);
}

static call_info_t*
get_call_info()
{
    rb_thread_t *thread;
    rb_control_frame_t *cfp;
    call_info_t *info;

    thread = ruby_current_thread;
    cfp = TH_CFP(thread);
    info = malloc(sizeof(call_info_t));
    //info = ALLOC(call_info_t);
    //info = malloc;

    info->call_info_argc = -1;
    info->call_info_kw_args = 0;

    cfp += 4;
    cfp = my_rb_vm_get_binding_creatable_next_cfp(thread, cfp);

    if(cfp->iseq != NULL)
    {
        if(cfp->pc == NULL || cfp->iseq->body == NULL)
        {
            return info;
        }

        const rb_iseq_t *iseq = cfp->iseq;

        ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;

        const VALUE *iseq_original = rb_iseq_original_iseq((rb_iseq_t *)iseq);

        int tmp = 0;
        int indent = 1;

        for(; indent < 6; indent++)
        {
            VALUE insn = iseq_original[pc - indent];
            tmp = (int)insn;

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

    result = (char*)malloc(sizeof(char) * (1 + lengths[count]));

    for (i = 0; i < count; i++)
    {
        const char *str = strings[i];
        if (str)
        {
            int start = lengths[i];
            if (i > 0)
                result[start++] = sep;

            memcpy(result + sizeof(char) * start, str, sizeof(char) * (lengths[i + 1] - start));
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

static VALUE
get_param_info_rb(VALUE self)
{
    rb_thread_t *thread;
    rb_control_frame_t *cfp;

    thread = ruby_current_thread;
    cfp = TH_CFP(thread);

    cfp += 3;

    size_t param_size = cfp->iseq->body->param.size;
    size_t lead_num = cfp->iseq->body->param.lead_num;
    size_t opt_num = cfp->iseq->body->param.opt_num;
    size_t post_num = cfp->iseq->body->param.post_num;

    unsigned int has_rest = cfp->iseq->body->param.flags.has_rest;
    unsigned int has_kw = cfp->iseq->body->param.flags.has_kw;
    unsigned int has_kwrest = cfp->iseq->body->param.flags.has_kwrest;
    unsigned int has_block = cfp->iseq->body->param.flags.has_block;

    const char **ans = (const char **)malloc(param_size * sizeof(const char*));

    if(param_size == 0)
        return 0;

    size_t i, ans_iterator;

    ans_iterator = 0;

    if(has_kw)
        param_size--;

    for(i = 0; i < lead_num; i++, ans_iterator++)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 2, "REQ", name);
    }

    for(i = 0; i < opt_num; i++, ans_iterator++)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 2, "OPT", name);
    }

    for(i = 0; i < has_rest; i++, ans_iterator++)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 2, "REST", name);
    }

    for(i = 0; i < post_num; i++, ans_iterator++)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 2, "POST", name);
    }


    if(cfp->iseq->body->param.keyword != NULL)
    {
        const ID *keywords = cfp->iseq->body->param.keyword->table;
        size_t kw_num = cfp->iseq->body->param.keyword->num;
        size_t required_num = cfp->iseq->body->param.keyword->required_num;

        for(i = 0; i < required_num; i++, ans_iterator++)
        {
            ID key = keywords[i];
            ans[ans_iterator] = fast_join(',', 2, "KEYREQ", rb_id2name(key));
        }
        for(i = required_num; i < kw_num; i++, ans_iterator++)
        {
            ID key = keywords[i];
            ans[ans_iterator] = fast_join(',', 2, "KEY", rb_id2name(key));
        }
    }

    for(i = 0; i < has_kwrest; i++, ans_iterator++)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);

        ans[ans_iterator] = fast_join(',', 2, "KEYREST", name);
    }

    for(i = 0; i < has_block; i++, ans_iterator++)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        ans[ans_iterator] = fast_join(',', 2, "BLOCK", name);
    }

    char *answer = fast_join_array(';', ans_iterator, ans);

    for(i = 0; i < ans_iterator; i++) {
        free(ans[i]);
    }
    free(ans);

    VALUE rb_answer = rb_str_new_cstr(answer);

    free(answer);

    return rb_answer;
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

    const char **types = (const char **)malloc(param_size * sizeof(const char*));
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

    char **ans = (char** )malloc(param_size * sizeof(char*));

    for(i = 0; i < lead_num; i++, ans_iterator++, types_iterator--)
    {
        ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
    }

    for(i = 0; i < opt_num; i++, ans_iterator++, types_iterator--)
    {
        ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
    }

    for(i = 0; i < has_rest; i++, ans_iterator++, types_iterator--)
    {
        ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
    }

    for(i = 0; i < post_num; i++, ans_iterator++, types_iterator--)
    {
        ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
    }


    if(cfp->iseq->body->param.keyword != NULL)
    {
        const ID *keywords = cfp->iseq->body->param.keyword->table;
        size_t kw_num = cfp->iseq->body->param.keyword->num;
        size_t required_num = cfp->iseq->body->param.keyword->required_num;

        LOG("%d %d\n", kw_num, required_num)

        for(i = 0; i < required_num; i++, ans_iterator++, types_iterator--)
        {
            ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
        }
        for(i = required_num; i < kw_num; i++, ans_iterator++, types_iterator--)
        {
            ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
        }
    }

    if(param_size - has_block > 1 && has_kwrest && TYPE(*(ep + types_ids[types_iterator])) == T_FIXNUM)
        types_iterator--;

    for(i = 0; i < has_kwrest; i++, ans_iterator++, types_iterator--)
    {
        const char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        char* type;

        type = types[types_iterator];

        ans[ans_iterator] = fast_join(',', 1, type, name);
    }

    for(i = 0; i < has_block; i++, ans_iterator++, types_iterator--)
    {
        ans[ans_iterator] = fast_join(',', 1, types[types_iterator]);
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
    return args_info ? rb_str_new_cstr(args_info) : Qnil;
}

static VALUE
get_call_info_rb(VALUE self)
{
    if(is_call_info_needed())
    {
        call_info_t *info = get_call_info();

        VALUE ans;
        ans = rb_ary_new();
        rb_ary_push(ans, LONG2FIX(info->call_info_argc));
        if(info->call_info_kw_args != 0)
            rb_ary_push(ans, rb_str_new_cstr(info->call_info_kw_args));

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