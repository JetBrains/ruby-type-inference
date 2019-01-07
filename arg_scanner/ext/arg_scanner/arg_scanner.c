#include "arg_scanner.h"
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdarg.h>
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

/**
 * Contains info related to explicitly passed args
 * For example:
 * def foo(a, b = 1); end
 *
 * `b` passed here implicitly:
 * foo(1)
 *
 * But here explicitly:
 * foo(1, 10)
 */
typedef struct
{
    ssize_t call_info_explicit_argc;   // Number of arguments that was explicitly passed by user
    char **call_info_kw_explicit_args; // kw arguments names that was explicitly passed by user (null terminating array)
} call_info_t;

typedef struct
{
    char *receiver_name;
    char *method_name;
    char *args_info;
    char *path;
    char *return_type_name;
    ssize_t explicit_argc; // Number of arguments that was explicitly passed by user
    int lineno;
    int is_in_project_root; // Can be 0, 1 or -1 when project_root is not specified
} signature_t;

void Init_arg_scanner();

static const char *ARG_SCANNER_EXIT_COMMAND = "EXIT";
static const char *EMPTY_VALUE = "";
static const int MAX_NUMBER_OF_MISSED_CALLS = 10;
/**
 * There we keep information about signatures that have already been sent to server in order to not sent them again
 */
static GTree *sent_to_server_tree;
/**
 * Here we store map with key: signature_t and value: int number (how many times method was called with the same args)
 * If we got that any method is called with the same args more than MAX_NUMBER_OF_MISSED_CALLS times in a row then
 * we will ignore it.
 */
static GTree *number_missed_calls_tree;
static GSList *call_stack = NULL;
static char *get_args_info(const char *const *explicit_kw_args);
static VALUE handle_call(VALUE self, VALUE tp);
static VALUE handle_return(VALUE self, VALUE tp);
static VALUE destructor(VALUE self);
static const char *calc_sane_class_name(VALUE ptr);

// returns Qnil if ready; or string containing error message otherwise 
static VALUE check_if_arg_scanner_ready(VALUE self);

// For testing
static VALUE get_args_info_rb(VALUE self);
static VALUE get_call_info_rb(VALUE self);

static call_info_t get_call_info();
static bool is_call_info_needed();

static void call_info_t_free(call_info_t s)
{
    free(s.call_info_kw_explicit_args);
}

static void signature_t_free(signature_t *s)
{
    free(s->receiver_name);
    free(s->method_name);
    free(s->args_info);
    free(s->path);
    free(s->return_type_name);
    free(s);
}

// Free signature_t partially leaving parts that are used in sent_to_server_tree_comparator
// @see_also sent_to_server_tree_comparator
static void signature_t_free_partially(signature_t *s)
{
    free(s->receiver_name);
    s->receiver_name = NULL;

    free(s->method_name);
    s->method_name = NULL;
}

// Comparator for number_missed_calls_tree.
static gint
number_missed_calls_tree_comparator(gconstpointer x, gconstpointer y, gpointer user_data_ignored) {
    const signature_t *a = x;
    const signature_t *b = y;
    int ret;

    // Comparison using lineno and path theoretically should guarantees us unique.
    // And compare lineno firstly because it's faster O(1) than comparing path which is O(path_len)
    ret = a->lineno - b->lineno;
    if (ret != 0) return ret;

    ret = strcmp(a->path, b->path);
    if (ret != 0) return ret;

    return 0;
}

// Comparator for sent_to_server_tree.
// If you want to change the way it compare then don't forget to
// change signature_t_free_partially accordingly
// @see_also signature_t_free_partially
static gint
sent_to_server_tree_comparator(gconstpointer x, gconstpointer y, gpointer user_data_ignored) {
    const signature_t *a = x;
    const signature_t *b = y;
    int ret;

    ret = number_missed_calls_tree_comparator(x, y, user_data_ignored);
    if (ret != 0) return ret;

    if (a->args_info != NULL && b->args_info != NULL) {
        ret = strcmp(a->args_info, b->args_info);
        if (ret != 0) return ret;
    }

    ret = strcmp(a->return_type_name, b->return_type_name);
    if (ret != 0) return ret;

    return 0;
}

inline int start_with(const char *str, const char *prefix) {
    if (str == NULL || prefix == NULL) {
        return -1;
    }
    while (*str != '\0' && *prefix != '\0') {
        if (*str != *prefix) {
            return 0;
        }
        str++;
        prefix++;
    }
    return 1;
}

FILE *pipe_file = NULL;
static char *project_root = NULL;
static int catch_only_every_n_call = 1;

static int file_exists(const char *file_path) {
    return access(file_path, F_OK) != -1;
}

static VALUE init(VALUE self, VALUE pipe_file_path, VALUE buffering,
                  VALUE project_root_local, VALUE catch_only_every_n_call_local) {
    if (pipe_file_path != Qnil) {
        pipe_file_path = rb_file_s_expand_path(1, &pipe_file_path); // https://ruby-doc.org/core-2.2.0/File.html#method-c-expand_path
        const char *pipe_file_path_c = StringValueCStr(pipe_file_path);
        if (!file_exists(pipe_file_path_c)) {
            fprintf(stderr, "Specified pipe file: %s doesn't exists\n", pipe_file_path_c);
            exit(1);
        }
        pipe_file = fopen(pipe_file_path_c, "w");
        if (pipe_file == NULL) {
            fprintf(stderr, "Cannot open pipe file \"%s\" with write access\n", pipe_file_path_c);
            exit(1);
        }

        int buffering_disabled = buffering == Qnil;
        if (buffering_disabled) {
            setbuf(pipe_file, NULL);
        }
    }
    if (project_root_local != Qnil) {
        project_root = strdup(StringValueCStr(project_root_local));
    }
    if (catch_only_every_n_call_local != Qnil) {
        if (sscanf(StringValueCStr(catch_only_every_n_call_local), "%d", &catch_only_every_n_call) != 1) {
            fprintf(stderr, "Please specify number in --catch-only-every-N-call arg\n");
            exit(1);
        }
        srand(time(0));
    }
    return Qnil;
}

void Init_arg_scanner() {
    mArgScanner = rb_define_module("ArgScanner");
    rb_define_module_function(mArgScanner, "handle_call", handle_call, 1);
    rb_define_module_function(mArgScanner, "handle_return", handle_return, 1);
    rb_define_module_function(mArgScanner, "get_args_info", get_args_info_rb, 0);
    rb_define_module_function(mArgScanner, "get_call_info", get_call_info_rb, 0);
    rb_define_module_function(mArgScanner, "destructor", destructor, 0);
    rb_define_module_function(mArgScanner, "check_if_arg_scanner_ready", check_if_arg_scanner_ready, 0);
    rb_define_module_function(mArgScanner, "init", init, 4);

    sent_to_server_tree = g_tree_new_full(/*key_compare_func =*/sent_to_server_tree_comparator,
                                          /*key_compare_data =*/NULL,
                                          /*key_destroy_func =*/(GDestroyNotify)signature_t_free,
                                          /*value_destroy_func =*/NULL);

    // key_destroy_func is NULL because we will use the same keys for number_missed_calls_tree
    // and sent_to_server_tree. And all memory management is done by sent_to_server_tree
    number_missed_calls_tree = g_tree_new_full(/*key_compare_func =*/number_missed_calls_tree_comparator,
                                               /*key_compare_data =*/NULL,
                                               /*key_destroy_func =*/NULL,
                                               /*value_destroy_func =*/NULL);
}

inline void push_to_call_stack(signature_t *signature) {
    call_stack = g_slist_prepend(call_stack, (gpointer) signature);
}

inline signature_t *pop_from_call_stack() {
    if (call_stack == NULL) {
        return NULL;
    }
    signature_t *ret = (signature_t *) call_stack->data;

    GSList *old_head = call_stack;
    call_stack = g_slist_remove_link(call_stack, old_head);
    g_slist_free_1(old_head);
    return ret;
}

inline int is_call_stack_empty() {
    return call_stack == NULL;
}

/**
 * Looks at the object at the top of this stack without removing it from the stack.
 */
inline signature_t *top_of_call_stack() {
    if (call_stack == NULL) {
        return NULL;
    }
    return (signature_t *) call_stack[0].data;
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

static VALUE exit_from_handle_call_skipping_call() {
    push_to_call_stack(NULL);
    return Qnil;
}

static VALUE
handle_call(VALUE self, VALUE tp)
{
    signature_t sign_temp;
    memset(&sign_temp, 0, sizeof(sign_temp));
    sign_temp.lineno = FIX2INT(rb_funcall(tp, rb_intern("lineno"), 0)); // Convert Ruby's Fixnum to C language int
    VALUE path = rb_funcall(tp, rb_intern("path"), 0);
    path = rb_file_s_expand_path(1, &path); // https://ruby-doc.org/core-2.2.0/File.html#method-c-expand_path
    sign_temp.path = StringValueCStr(path);

    int is_in_project_root = start_with(sign_temp.path, project_root);

    if (project_root != NULL && !is_in_project_root) {
        signature_t *peek = top_of_call_stack();

        if (!is_call_stack_empty() && (peek == NULL || !(peek->is_in_project_root))) {
            return exit_from_handle_call_skipping_call();
        }
    }

    if (project_root == NULL || !is_in_project_root) {
        int number_of_missed_calls = (int)g_tree_lookup(number_missed_calls_tree, &sign_temp);
        if (number_of_missed_calls > MAX_NUMBER_OF_MISSED_CALLS) {
            return exit_from_handle_call_skipping_call();
        }
    }

    if (catch_only_every_n_call != 1 && rand() % catch_only_every_n_call != 0) {
        return exit_from_handle_call_skipping_call();
    }

    signature_t *sign = (signature_t *) calloc(1, sizeof(*sign));

    sign->is_in_project_root = is_in_project_root;
    sign->lineno = sign_temp.lineno;
    sign->path = strdup(sign_temp.path);
    sign->method_name = strdup(rb_id2name(SYM2ID(rb_funcall(tp, rb_intern("method_id"), 0))));
    sign->explicit_argc = -1;

#ifdef DEBUG_ARG_SCANNER
    LOG("Getting args info for %s %s %d \n", sign->method_name, sign->path, sign->lineno);
#endif
    call_info_t info;
    info.call_info_kw_explicit_args = NULL;
    if (is_call_info_needed()) {
        info = get_call_info();
        sign->explicit_argc = info.call_info_explicit_argc;
    }

    sign->args_info = get_args_info(info.call_info_kw_explicit_args);
    call_info_t_free(info);

    if (sign->args_info != NULL && strlen(sign->args_info) >= 1000) {
        signature_t_free(sign);
        return exit_from_handle_call_skipping_call();
    }

    push_to_call_stack(sign);
    return Qnil;
}

static VALUE
handle_return(VALUE self, VALUE tp)
{
    signature_t *sign = pop_from_call_stack();
    if (sign == NULL) {
        return Qnil;
    }
    VALUE defined_class = rb_funcall(tp, rb_intern("defined_class"), 0);

    VALUE receiver_name = rb_mod_name(defined_class);

    // if defined_class is nil then it means that method is invoked from anonymous module.
    // Then trying to extract name of it's anonymous module. For more details see
    // CallStatCompletionTest#testAnonymousModuleMethodCall
    if (receiver_name == Qnil) {
        VALUE this = rb_funcall(tp, rb_intern("self"), 0);
        receiver_name = rb_funcall(this, rb_intern("to_s"), 0);
    }

    VALUE return_type_name = rb_funcall(tp, rb_intern("return_value"), 0);

    sign->receiver_name = strdup(StringValueCStr(receiver_name));
    sign->return_type_name = strdup(calc_sane_class_name(return_type_name));

    signature_t *sign_in_sent_to_server_tree = g_tree_lookup(sent_to_server_tree, sign);
    if (sign_in_sent_to_server_tree == NULL) {
        // Resets number of missed calls to 0
        g_tree_insert(number_missed_calls_tree, /*key = */sign, /*value = */0);

        // GTree will free memory allocated by sign by itself
        g_tree_insert(sent_to_server_tree, /*key = */sign, /*value = */sign);

        if (pipe_file != NULL && !is_call_stack_empty()) {
            signature_t *prev = top_of_call_stack();
            if (prev != NULL) {
                fprintf(pipe_file, "%s %s:%d -> %s %s:%d\n", prev->method_name, prev->path, prev->lineno,
                    sign->method_name, sign->path, sign->lineno);
            }
        }

        signature_t_free_partially(sign);
    } else if (project_root == NULL || !sign->is_in_project_root) {
        signature_t_free(sign);

        int found = (int) g_tree_lookup(number_missed_calls_tree, sign_in_sent_to_server_tree);
        g_tree_insert(number_missed_calls_tree, /*key = */sign_in_sent_to_server_tree, /*value = */found + 1);
    }
    return Qnil;
}

static call_info_t
get_call_info() {
    rb_thread_t *thread = ruby_current_thread;
    rb_control_frame_t *cfp = TH_CFP(thread);

    call_info_t empty;
    empty.call_info_kw_explicit_args = NULL;
    empty.call_info_explicit_argc = -1;

    cfp += 3;
    cfp = my_rb_vm_get_binding_creatable_next_cfp(thread, cfp);

    if(cfp->iseq == NULL || cfp->pc == NULL || cfp->iseq->body == NULL) {
        return empty;
    }

    const rb_iseq_t *iseq = (const rb_iseq_t *) cfp->iseq;

    ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;

    const VALUE *iseq_original = rb_iseq_original_iseq(iseq);

    int indent;
    for (indent = 1; indent < 6; indent++) {
        VALUE insn = iseq_original[pc - indent];
        int tmp = (int)insn;
        if(0 < tmp && tmp < 256) {
            if(indent < 3) {
                return empty;
            }
            call_info_t info;
            struct rb_call_info *ci = (struct rb_call_info *)iseq_original[pc - indent + 1];
            info.call_info_explicit_argc = ci->orig_argc;
            info.call_info_kw_explicit_args = NULL;

            if (ci->flag & VM_CALL_KWARG) {
                struct rb_call_info_kw_arg *kw_args = ((struct rb_call_info_with_kwarg *)ci)->kw_arg;

                size_t kwArgSize = kw_args->keyword_len;

                VALUE kw_ary = rb_ary_new_from_values(kw_args->keyword_len, kw_args->keywords);

                info.call_info_kw_explicit_args = (char **) malloc((kwArgSize + 1)*sizeof(*(info.call_info_kw_explicit_args)));

                int i;
                for (i = kwArgSize -1 ; i >= 0; --i) {
                    VALUE kw = rb_ary_pop(kw_ary);
                    const char *kw_name = rb_id2name(SYM2ID(kw));

                    info.call_info_kw_explicit_args[i] = kw_name;
                }
                info.call_info_kw_explicit_args[kwArgSize] = NULL;
            } else {
                info.call_info_kw_explicit_args = malloc(sizeof(*info.call_info_kw_explicit_args));
                info.call_info_kw_explicit_args[0] = NULL;
            }
            return info;
        }
    }
    return empty;
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

/**
 * Checks that `container` contains `element`
 */
static int contains(const char *const *container, const char *element) {
    if (container == NULL || element == NULL) {
        return 0;
    }
    const char *const *iterator = container;
    while (*iterator != NULL) {
        if (strcmp(*iterator, element) == 0) {
            return 1;
        }
        ++iterator;
    }
    return 0;
}

#define JOIN_KW_NAMES_AND_TYPES_BUF_SIZE 2048
static char join_kw_names_and_types_buf[JOIN_KW_NAMES_AND_TYPES_BUF_SIZE];
/**
 * Null terminating array which contains strings of explicitly passed kw args.
 * It's used for join_kw_names_and_types
 */
static const char *const *join_kw_names_and_types_explicit_kw_args = NULL;

/**
 * This function is used for concatenating hash keys and value's types.
 * Be sure that buf is at least JOIN_KW_NAMES_AND_TYPES_BUF_SIZE bytes.
 * If join_kw_names_and_types_buf size = JOIN_KW_NAMES_AND_TYPES_BUF_SIZE
 * isn't enough then this buf will contain invalid information
 */
static int join_kw_names_and_types(VALUE key, VALUE val, VALUE ignored) {
    const char *kw_name = rb_id2name(SYM2ID(key));
    const char *kw_type = calc_sane_class_name(val);

    const char *const *explicit_kw_args_iterator = join_kw_names_and_types_explicit_kw_args;

    // Just such behaviour: when join_kw_names_and_types_explicit_kw_args is
    // not provided then consider every kw arg as explicitly passed by user
    int is_explicit = explicit_kw_args_iterator == NULL;

    if (explicit_kw_args_iterator != NULL) {
        while(*explicit_kw_args_iterator != NULL) {
            if (strcmp(*explicit_kw_args_iterator, kw_name) == 0) {
                is_explicit = 1;
                break;
            }
            ++explicit_kw_args_iterator;
        }
    }

    if (is_explicit) {
        // Check that buf is not empty
        if (join_kw_names_and_types_buf[0] != '\0') {
            strncat(join_kw_names_and_types_buf, ";", JOIN_KW_NAMES_AND_TYPES_BUF_SIZE - 1);
        }
        strncat(join_kw_names_and_types_buf, "KEYREST,", JOIN_KW_NAMES_AND_TYPES_BUF_SIZE - 1);
        strncat(join_kw_names_and_types_buf, kw_type, JOIN_KW_NAMES_AND_TYPES_BUF_SIZE - 1);
        strncat(join_kw_names_and_types_buf, ",", JOIN_KW_NAMES_AND_TYPES_BUF_SIZE - 1);
        strncat(join_kw_names_and_types_buf, kw_name, JOIN_KW_NAMES_AND_TYPES_BUF_SIZE - 1);
    }
    return ST_CONTINUE;
}

static char*
get_args_info(const char *const *explicit_kw_args)
{
    rb_thread_t *thread;
    rb_control_frame_t *cfp;

    thread = ruby_current_thread;
    cfp = TH_CFP(thread);

    cfp += 2;

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

    if (param_size == 0) {
        return 0;
    }

    const char **types = (const char **)malloc(param_size * sizeof(*types));
    size_t i, ans_iterator;
    int types_iterator;

    ans_iterator = 0;

    int new_version_flag = strcmp(RUBY_VERSION, "2.4.0") >= 0 ? 1 : 0;
    LOG("%d\n", new_version_flag);

    for(i = param_size - 1 - new_version_flag, types_iterator = 0; (size_t)types_iterator < param_size; i--, types_iterator++)
    {
        types[types_iterator] = calc_sane_class_name(ep[i - 1]);
        types_ids[types_iterator] = i - 1;
        LOG("Type #%d=%s\n", types_iterator, types[types_iterator])
    }

    types_iterator--;

    if(has_kw) {
        param_size--;
    }

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
        for(i = required_num; i < kw_num; i++, types_iterator--)
        {
            ID key = keywords[i];
            const char *name = rb_id2name(key);
            if (explicit_kw_args == NULL || contains(explicit_kw_args, name)) {
                ans[ans_iterator++] = fast_join(',', 3, "KEY", types[types_iterator], name);
            }
        }

        if (param_size - has_block > 1 && has_kwrest && TYPE(ep[types_ids[types_iterator]]) == T_FIXNUM) {
            types_iterator--;
        }

        if (has_kwrest)
        {
            char *buf = malloc(JOIN_KW_NAMES_AND_TYPES_BUF_SIZE * sizeof(*buf));
            buf[0] = '\0';
            join_kw_names_and_types_buf[0] = '\0';
            join_kw_names_and_types_explicit_kw_args = explicit_kw_args;

            // This function call will concatenate info into join_kw_names_and_types_buf
            rb_hash_foreach(ep[types_ids[types_iterator]], join_kw_names_and_types, Qnil);

            // Checking that join_kw_names_and_types_buf isn't possibly containing invalid info.
            // See join_kw_names_and_types documentation to understand why it can be invalid
            size_t len = strlen(join_kw_names_and_types_buf);
            if (len > 0 && len < JOIN_KW_NAMES_AND_TYPES_BUF_SIZE - 1) {
                strncpy(buf, join_kw_names_and_types_buf, JOIN_KW_NAMES_AND_TYPES_BUF_SIZE);
                ans[ans_iterator++] = buf;
            }
            join_kw_names_and_types_explicit_kw_args = NULL;
            types_iterator--;
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
    assert(types_iterator <= 0);

    free(types);
    free(ans);

    return answer;
}

static VALUE
get_args_info_rb(VALUE self)
{
    call_info_t info;
    info.call_info_kw_explicit_args = NULL;
    if (is_call_info_needed()) {
        info = get_call_info();
    }

    char *args_info = get_args_info(info.call_info_kw_explicit_args);
    call_info_t_free(info);
    VALUE ret = args_info ? rb_str_new_cstr(args_info) : Qnil;
    free(args_info);
    return ret;
}

static VALUE
get_call_info_rb(VALUE self)
{
    if (is_call_info_needed())
    {
        call_info_t info = get_call_info();

        VALUE ans;
        ans = rb_ary_new();
        rb_ary_push(ans, LONG2FIX(info.call_info_explicit_argc));
        if (info.call_info_kw_explicit_args != NULL) {
            const char *const *kwarg = info.call_info_kw_explicit_args;
            int explicit_kw_count = 0;
            while (*kwarg != NULL) {
                ++explicit_kw_count;
                ++kwarg;
            }
            char *answer = fast_join_array(',', explicit_kw_count, info.call_info_kw_explicit_args);
            rb_ary_push(ans, rb_str_new_cstr(answer));
            free(answer);
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
    cfp += 2;

    return (cfp->iseq->body->param.flags.has_opt
        || cfp->iseq->body->param.flags.has_kwrest
        || cfp->iseq->body->param.flags.has_rest
        || (cfp->iseq->body->param.keyword != NULL && cfp->iseq->body->param.keyword->required_num == 0));
}

static VALUE 
check_if_arg_scanner_ready(VALUE self) {
    char error_msg[1024];
    if (pipe_file == NULL) {
        snprintf(error_msg, sizeof(error_msg)/sizeof(*error_msg), "Pipe file is not specified");
        return rb_str_new_cstr(error_msg);
    }
    return Qnil;
}

static VALUE
destructor(VALUE self) {
    g_tree_destroy(sent_to_server_tree);
    g_tree_destroy(number_missed_calls_tree);
    fprintf(pipe_file, "%s\n", ARG_SCANNER_EXIT_COMMAND);
    fclose(pipe_file);
    free(project_root);
    return Qnil;
}
