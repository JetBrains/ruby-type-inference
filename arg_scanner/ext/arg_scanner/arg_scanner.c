#include "arg_scanner.h"
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#define DEBUG_ARG_SCANNER 1

#ifdef DEBUG_ARG_SCANNER
    #define LOG(f, args...) { fprintf(stderr, "DEBUG: '%s'=", #args); fprintf(stderr, f, ##args); fflush(stderr); }
#else
    #define LOG(...) {}
#endif

#define ruby_current_thread ((rb_thread_t *)RTYPEDDATA_DATA(rb_thread_current()))
typedef struct rb_trace_arg_struct rb_trace_arg_t;

VALUE mArgScanner = Qnil;

static VALUE c_signature;

typedef struct
{
    int call_info_argc;
    char* call_info_kw_args;
} call_info_t;

typedef struct
{
    char* method_name;
    char* args_info;
    char* path;
    int call_info_argc;
    char* call_info_kw_args;
    int lineno;
    char* receiver_name;
    char* return_type_name;
    char* visibility;

    char* gem_name;
    char* gem_version;
} signature_t;

static void signature_t_free(void *s)
{
    LOG("free5\n");
    free(s);
}

static rb_trace_arg_t *
get_trace_arg(void)
{
    rb_trace_arg_t *trace_arg = GET_THREAD()->trace_arg;
    if (trace_arg == 0) {
        fprintf(stderr, "access from outside\n");
	    rb_raise(rb_eRuntimeError, "access from outside");
    }
    return trace_arg;
}

static void
fill_path_and_lineno(rb_trace_arg_t *trace_arg)
{
    if (trace_arg->path == Qundef) {
	rb_control_frame_t *cfp = rb_vm_get_ruby_level_next_cfp(trace_arg->th, trace_arg->cfp);

	if (cfp) {
	    trace_arg->path = rb_iseq_path(cfp->iseq);
	    trace_arg->lineno = rb_vm_get_sourceline(cfp);
	}
	else {
	    trace_arg->path = Qnil;
	    trace_arg->lineno = 0;
	}
    }
}

void Init_arg_scanner();
static call_info_t* get_call_info();
static char* get_args_info();
static bool is_call_info_needed();
static VALUE handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path);
static VALUE handle_return(VALUE self, VALUE signature, VALUE receiver_name, VALUE return_type_name);

void Init_arg_scanner() {
    mArgScanner = rb_define_module("ArgScanner");
    rb_define_module_function(mArgScanner, "handle_call", handle_call, 3);
    rb_define_module_function(mArgScanner, "handle_return", handle_return, 3);
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
handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path)
{
    rb_trace_arg_t *trace_arg = get_trace_arg();

    //VALUE method_sym = rb_tracearg_method_id(trace_arg);
    ID method_id = SYM2ID(method_name);
    //fill_path_and_lineno(trace_arg);
    //VALUE path = trace_arg->path;
    char* c_method_name = rb_id2name(method_id);
    char* c_path = StringValuePtr(path);
    int c_lineno = FIX2INT(lineno);//trace_arg->lineno;

    signature_t *sign;
    sign = ALLOC(signature_t);

    sign->lineno = c_lineno;
    sign->method_name = c_method_name;
    sign->path = c_path;

    sign->args_info = get_args_info();

    if (is_call_info_needed())
    {
        call_info_t *info = get_call_info();

        sign->call_info_argc = info->call_info_argc;
        sign->call_info_kw_args = info->call_info_kw_args;

    } else {
        sign->call_info_argc = -1;
        sign->call_info_kw_args = "";
    }

    return Data_Wrap_Struct(c_signature, NULL, signature_t_free, sign);
}


static VALUE
handle_return(VALUE self, VALUE signature, VALUE receiver_name, VALUE return_type_name)
{
    signature_t *sign;
    Data_Get_Struct(signature, signature_t, sign);

    sign->receiver_name = StringValuePtr(receiver_name);
    sign->return_type_name = StringValuePtr(return_type_name);
    sign->visibility = "PUBLIC";

    char* json_mes[100];

    LOG("%d \n", sign->method_name);
    LOG("%d \n", sign->call_info_argc);
    LOG("%d \n", sign->call_info_kw_args);
    LOG("%d \n", sign->receiver_name);
    LOG("%d \n", sign->args_info);
    LOG("%d \n", sign->return_type_name);
    LOG("%d \n", sign->visibility);
    LOG("%d \n", sign->path);
    LOG("%d \n", sign->lineno);


    sprintf(json_mes,
    "{\"method_name\":\"%s\",\"call_info_argc\":\"%d\",\"call_info_kw_args\":\"%s\",\"receiver_name\":\"%s\",\"args_info\":\"%s\",\"return_type_name\":\"%s\",\"visibility\":\"%s\",\"path\":\"%s\",\"lineno\":\"%d\",",
    sign->method_name,
    sign->call_info_argc,
    sign->call_info_kw_args,
    sign->receiver_name,
    sign->args_info,
    sign->return_type_name,
    sign->visibility,
    sign->path,
    sign->lineno);

    return rb_str_new_cstr(json_mes);
}

static call_info_t*
get_call_info()
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;
    call_info_t *info;
    info = ALLOC(call_info_t);

    info->call_info_argc = -1;
    info->call_info_kw_args = "";

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

                    int kwArgSize = kw_args->keyword_len;

                    VALUE kw_ary = rb_ary_new_from_values(kw_args->keyword_len, kw_args->keywords);
                    char **c_kw_ary = (char**)malloc(kwArgSize * sizeof(char*));

                    int ans_size = 0;
                    int j;

                    for(j = kwArgSize - 1; j >= 0; j--)
                    {
                        VALUE kw = rb_ary_pop(kw_ary);
                        char* kw_name = rb_id2name(SYM2ID(kw));

                        c_kw_ary[j] = kw_name;
                        ans_size += strlen(kw_name);

                        if(j + 1 < kwArgSize)
                            ans_size++;
                    }

                    info->call_info_kw_args = (char*)malloc(ans_size + 1);

                    if(kwArgSize > 0)
                    {
                        strcpy(info->call_info_kw_args, c_kw_ary[0]);

                        if(kwArgSize > 1)
                            strcat(info->call_info_kw_args, ",");
                    }

                    for(j = 1; j < kwArgSize; j++)
                    {
                        strcat(info->call_info_kw_args, c_kw_ary[j]);

                        if(j + 1 < kwArgSize)
                            strcat(info->call_info_kw_args, ",");
                    }
                    LOG("free1\n");
                    free(c_kw_ary);
                }
                return info;
            }
        }
    }
    return info;
}


static char*
get_args_info()
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;

    cfp += 3;

    VALUE *ep = cfp->ep;
    ep -= cfp->iseq->body->local_table_size;

    int param_size = cfp->iseq->body->param.size;
    int lead_num = cfp->iseq->body->param.lead_num;
    int opt_num = cfp->iseq->body->param.opt_num;
    int rest_start = cfp->iseq->body->param.rest_start;
    int post_start = cfp->iseq->body->param.post_start;
    int post_num = cfp->iseq->body->param.post_num;
    int block_start = cfp->iseq->body->param.block_start;

    unsigned int has_lead = cfp->iseq->body->param.flags.has_lead;
    unsigned int has_opt = cfp->iseq->body->param.flags.has_opt;
    unsigned int has_rest = cfp->iseq->body->param.flags.has_rest;
    unsigned int has_post = cfp->iseq->body->param.flags.has_post;
    unsigned int has_kw = cfp->iseq->body->param.flags.has_kw;
    unsigned int has_kwrest = cfp->iseq->body->param.flags.has_kwrest;
    unsigned int has_block = cfp->iseq->body->param.flags.has_block;

    unsigned int ambiguous_param0 = cfp->iseq->body->param.flags.has_lead;

    if(param_size == 0)
    {
        return "";
    }

    char **types = (char** )malloc(param_size * sizeof(char*));

    int i, types_iterator = 0, ans_iterator = 0;

    for(i = param_size - 1; i >= 0; i--, types_iterator++)
    {
        VALUE klass = rb_class_real(CLASS_OF(*(ep + i - 1)));
        const char* klass_name = rb_class2name(klass);

        types[types_iterator] = klass_name;
    }
    types_iterator--;

    if(has_kw)
        param_size--;

    char **ans = (char** )malloc(param_size * sizeof(char*));


    for(i = 0; i < lead_num; i++, ans_iterator++)
    {
        char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        if(name)
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (6 + strlen(types[types_iterator]) + strlen(name)));

            strcpy(ans[ans_iterator], "REQ,");
            strcat(ans[ans_iterator], types[types_iterator]);

            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], name);

            assert(6 + strlen(types[types_iterator]) + strlen(name) == strlen(ans[ans_iterator]) + 1);
        }
        else
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (5 + strlen(types[types_iterator])));

            strcpy(ans[ans_iterator], "REQ,");
            strcat(ans[ans_iterator], types[types_iterator]);

            assert(5 + strlen(types[types_iterator]) == strlen(ans[ans_iterator]) + 1);
        }

        types_iterator--;
    }

    for(i = 0; i < opt_num; i++, ans_iterator++)
    {
        char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        if(name)
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (6 + strlen(types[types_iterator]) + strlen(name)));

            strcpy(ans[ans_iterator], "OPT,");
            strcat(ans[ans_iterator], types[types_iterator]);

            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], name);

            assert(6 + strlen(types[types_iterator]) + strlen(name) == strlen(ans[ans_iterator]) + 1);
        }
        else
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (5 + strlen(types[types_iterator])));

            strcpy(ans[ans_iterator], "OPT,");
            strcat(ans[ans_iterator], types[types_iterator]);

            assert(5 + strlen(types[types_iterator]) == strlen(ans[ans_iterator]) + 1);
        }

        types_iterator--;
    }

    for(i = 0; i < has_rest; i++, ans_iterator++)
    {
        char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        if(name)
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (7 + strlen(types[types_iterator]) + strlen(name)));

            strcpy(ans[ans_iterator], "REST,");
            strcat(ans[ans_iterator], types[types_iterator]);
            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], name);

             assert(7 + strlen(types[types_iterator]) + strlen(name) == strlen(ans[ans_iterator]) + 1);
       }
        else
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (6 + strlen(types[types_iterator])));

            strcpy(ans[ans_iterator], "REST,");
            strcat(ans[ans_iterator], types[types_iterator]);

            assert(6 + strlen(types[types_iterator]) == strlen(ans[ans_iterator]) + 1);
        }

        types_iterator--;
    }

    for(i = 0; i < post_num; i++, ans_iterator++)
    {
        char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        if(name)
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (7 + strlen(types[types_iterator]) + strlen(name)));

            strcpy(ans[ans_iterator], "POST,");
            strcat(ans[ans_iterator], types[types_iterator]);
            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], name);

            assert(7 + strlen(types[types_iterator]) + strlen(name) == strlen(ans[ans_iterator]) + 1);
        }
        else
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (6 + strlen(types[types_iterator])));

            strcpy(ans[ans_iterator], "POST,");
            strcat(ans[ans_iterator], types[types_iterator]);

            assert(6 + strlen(types[types_iterator]) == strlen(ans[ans_iterator]) + 1);
        }

        types_iterator--;
    }


    if(cfp->iseq->body->param.keyword != NULL)
    {
        const ID *keywords = cfp->iseq->body->param.keyword->table;
        int kw_num = cfp->iseq->body->param.keyword->num;
        int required_num = cfp->iseq->body->param.keyword->required_num;

        LOG("%d %d\n", kw_num, required_num)

        const VALUE * const default_values = cfp->iseq->body->param.keyword->default_values;

        for(i = 0; i < required_num; i++, ans_iterator++)
        {
            ID key = keywords[i];

            ans[ans_iterator] = (char*)malloc(sizeof(char) * (9 + strlen(types[types_iterator]) + strlen(rb_id2name(key))));

            strcpy(ans[ans_iterator], "KEYREQ,");
            strcat(ans[ans_iterator], types[types_iterator]);
            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], rb_id2name(key));

            assert(9 + strlen(types[types_iterator]) + strlen(rb_id2name(key)) == strlen(ans[ans_iterator]) + 1);

            types_iterator--;
        }
        for(i = required_num; i < kw_num; i++, ans_iterator++)
        {
            ID key = keywords[i];

            ans[ans_iterator] = (char*)malloc(sizeof(char) * (6 + strlen(types[types_iterator]) + strlen(rb_id2name(key))));

            strcpy(ans[ans_iterator], "KEY,");
            strcat(ans[ans_iterator], types[types_iterator]);
            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], rb_id2name(key));

            assert(6 + strlen(types[types_iterator]) + strlen(rb_id2name(key)) == strlen(ans[ans_iterator]) + 1);

            types_iterator--;
        }
    }

    for(i = 0; i < has_kwrest; i++, ans_iterator++)
    {
        char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        if(name)
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (10 + strlen(types[types_iterator]) + strlen(name)));

            strcpy(ans[ans_iterator], "KEYREST,");
            strcat(ans[ans_iterator], types[types_iterator]);
            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], name);

            assert(10 + strlen(types[types_iterator]) + strlen(name) == strlen(ans[ans_iterator]) + 1);
        }
        else
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (9 + strlen(types[types_iterator])));

            strcpy(ans[ans_iterator], "KEYREST,");
            strcat(ans[ans_iterator], types[types_iterator]);

            assert(9 + strlen(types[types_iterator]) == strlen(ans[ans_iterator]) + 1);
        }

        types_iterator--;
    }

    for(i = 0; i < has_block; i++, ans_iterator++)
    {
        char* name = rb_id2name(cfp->iseq->body->local_table[ans_iterator]);
        if(name)
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (8 + strlen(types[types_iterator]) + strlen(name)));

            strcpy(ans[ans_iterator], "BLOCK,");
            strcat(ans[ans_iterator], types[types_iterator]);
            strcat(ans[ans_iterator], ",");
            strcat(ans[ans_iterator], name);

            assert(8 + strlen(types[types_iterator]) + strlen(name) == strlen(ans[ans_iterator]) + 1);
        }
        else
        {
            ans[ans_iterator] = (char*)malloc(sizeof(char) * (7 + strlen(types[types_iterator])));

            strcpy(ans[ans_iterator], "BLOCK,");
            strcat(ans[ans_iterator], types[types_iterator]);

            assert(7 + strlen(types[types_iterator]) == strlen(ans[ans_iterator]) + 1);
        }

        types_iterator--;
    }

    int answer_size = 0;

    for(i = 0; i < ans_iterator; i++)
    {
        answer_size += strlen(ans[i]);
        if(i + 1 < ans_iterator)
            answer_size++;
    }

    char *answer = (char*)malloc(answer_size + 1);

    if(ans_iterator > 0)
        strcpy(answer, ans[0]);

    for(i = 1; i < ans_iterator; i++)
    {
        strcat(answer, ";");
        strcat(answer, ans[i]);
    }

    for(i = 0; i < ans_iterator; i++) {
        LOG("free2 %d %d =%s= \n", ans[i], strlen(ans[i]), ans[i]);
        free(ans[i]);
    }

    assert(ans_iterator == param_size);
    LOG("%d\n", has_kw)
    if (types_iterator >= 0)
    {
        LOG("%d =%s=\n", types_iterator, types[types_iterator]);
        LOG("%d: %d %d %d %d %d %d %d\n", param_size
                 ,has_lead
                 ,has_opt
                 ,has_rest
                 ,has_post
                 ,has_kw
                 ,has_kwrest
                 ,has_block);
        LOG("%d %d %d %d %d %d %d\n",
            param_size,
            lead_num,
            opt_num,
            rest_start,
            post_start,
            post_num,
            block_start)
    }
    assert(types_iterator - has_kw == -1);

    LOG("free3\n");
    free(types);
    LOG("free4\n");
    free(ans);

    return answer;
}

static bool
is_call_info_needed()
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;


    cfp += 3;

    unsigned int has_opt = cfp->iseq->body->param.flags.has_opt;
    unsigned int has_kw = cfp->iseq->body->param.flags.has_kw;
    unsigned int has_kwrest = cfp->iseq->body->param.flags.has_kwrest;
    unsigned int has_rest = cfp->iseq->body->param.flags.has_rest;

    int required_num = 0;

    return (has_opt || has_kwrest || has_rest || (cfp->iseq->body->param.keyword != NULL && cfp->iseq->body->param.keyword->required_num == 0));
}