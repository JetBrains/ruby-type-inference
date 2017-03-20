#include <stdio.h>
#include <stddef.h>
#include <stdint.h>
#include "arg_scanner.h"

#define MAX_POSBUF 128
#define ruby_current_thread ((rb_thread_t *)RTYPEDDATA_DATA(rb_thread_current()))

typedef enum { false, true } bool;

#define VM_CFP_CNT(th, cfp) \
  ((rb_control_frame_t *)((th)->stack + (th)->stack_size) - (rb_control_frame_t *)(cfp))

VALUE mArgScanner = Qnil;

void Init_arg_scanner();
VALUE get_args_info(VALUE self);
VALUE get_call_info(VALUE self);
VALUE is_call_info_needed(VALUE self);


void Init_arg_scanner() {
  mArgScanner = rb_define_module("ArgScanner");
  rb_define_module_function(mArgScanner, "get_call_info", get_call_info, 0);
  rb_define_module_function(mArgScanner, "get_args_info", get_args_info, 0);
  rb_define_module_function(mArgScanner, "is_call_info_needed", is_call_info_needed, 0);
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

static rb_control_frame_t *
my_vm_get_ruby_level_caller_cfp(const rb_thread_t *th, const rb_control_frame_t *cfp)
{
    if (RUBY_VM_NORMAL_ISEQ_P(cfp->iseq)) {
	return (rb_control_frame_t *)cfp;
    }

    cfp = RUBY_VM_PREVIOUS_CONTROL_FRAME(cfp);

    while (!RUBY_VM_CONTROL_FRAME_STACK_OVERFLOW_P(th, cfp)) {
	if (RUBY_VM_NORMAL_ISEQ_P(cfp->iseq)) {
	    return (rb_control_frame_t *)cfp;
	}

	if ((cfp->flag & VM_FRAME_FLAG_PASSED) == 0) {
	    break;
	}
	cfp = RUBY_VM_PREVIOUS_CONTROL_FRAME(cfp);
    }
    return 0;
}

rb_control_frame_t *
my_rb_vm_get_ruby_level_next_cfp(const rb_thread_t *th, const rb_control_frame_t *cfp)
{
    while (!RUBY_VM_CONTROL_FRAME_STACK_OVERFLOW_P(th, cfp)) {
	if (RUBY_VM_NORMAL_ISEQ_P(cfp->iseq)) {
	    return (rb_control_frame_t *)cfp;
	}
	cfp = RUBY_VM_PREVIOUS_CONTROL_FRAME(cfp);
    }
    return 0;
}

VALUE get_call_info(VALUE self)
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;

    cfp += 4;
    cfp = my_rb_vm_get_binding_creatable_next_cfp(thread, cfp);

    if(cfp->iseq != NULL)
    {
        if(cfp->pc == NULL || cfp->iseq->body == NULL)
        {
            return Qnil;
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
                    return Qnil;

                struct rb_call_info *ci = (struct rb_call_info *)iseq_original[pc - indent + 1];


                VALUE ans = rb_ary_new();
                rb_ary_push(ans, rb_id2str(ci->mid));
                rb_ary_push(ans, INT2NUM(ci->orig_argc));

                if (ci->flag & VM_CALL_KWARG)
                {
                    struct rb_call_info_kw_arg *kw_args = ((struct rb_call_info_with_kwarg *)ci)->kw_arg;

                    int kwArgSize = kw_args->keyword_len;

                    VALUE kw_ary = rb_ary_new_from_values(kw_args->keyword_len, kw_args->keywords);

                    rb_ary_push(ans, kw_ary);
                }
                return ans;
            }
        }
    }

    return Qnil;
}


VALUE get_args_info(VALUE self)
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;


    cfp += 3;

    VALUE *ep = cfp->ep;

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

    if(has_kw)
        param_size--;

    VALUE ans = rb_str_new(0, 0);
    VALUE types = rb_ary_new();

    bool flag = false;

    for(int i = param_size - 1; i >= 0; i--)
    {
        VALUE klass = rb_class_real(CLASS_OF(*(ep - i - 2)));
        char* klass_name = rb_class2name(klass);

        rb_ary_push(types, rb_str_new_cstr(klass_name));
    }

    for(int i = 0; i < lead_num; i++)
    {
        if(flag)
            ans = rb_str_concat(ans, rb_str_new_cstr(";"));

        ans = rb_str_concat(ans, rb_str_new_cstr("REQ,"));
        ans = rb_str_concat(ans, rb_ary_pop(types));

        flag = true;
    }

    for(int i = 0; i < opt_num; i++)
    {
        if(flag)
            ans = rb_str_concat(ans, rb_str_new_cstr(";"));

        ans = rb_str_concat(ans, rb_str_new_cstr("OPT,"));
        ans = rb_str_concat(ans, rb_ary_pop(types));

        flag = true;
    }

    for(int i = 0; i < has_rest; i++)
    {
        if(flag)
            ans = rb_str_concat(ans, rb_str_new_cstr(";"));

        ans = rb_str_concat(ans, rb_str_new_cstr("REST,"));
        ans = rb_str_concat(ans, rb_ary_pop(types));

        flag = true;
    }

    for(int i = 0; i < post_num; i++)
    {
        if(flag)
            ans = rb_str_concat(ans, rb_str_new_cstr(";"));

        ans = rb_str_concat(ans, rb_str_new_cstr("POST,"));
        ans = rb_str_concat(ans, rb_ary_pop(types));

        flag = true;
    }


    if(cfp->iseq->body->param.keyword != NULL)
    {
        const ID *keywords = cfp->iseq->body->param.keyword->table;
        int kw_num = cfp->iseq->body->param.keyword->num;

        for(int i = 0; i < kw_num; i++)
        {
            if(flag)
                ans = rb_str_concat(ans, rb_str_new_cstr(";"));

            ans = rb_str_concat(ans, rb_str_new_cstr("KEY,"));
            ans = rb_str_concat(ans, rb_ary_pop(types));
            ans = rb_str_concat(ans, rb_str_new_cstr(","));
            ID key = keywords[i];
            VALUE kwName = rb_id2str(key);

            ans = rb_str_concat(ans, kwName);

            flag = true;
        }
    }

    for(int i = 0; i < has_kwrest; i++)
    {
        if(flag)
            ans = rb_str_concat(ans, rb_str_new_cstr(";"));

        ans = rb_str_concat(ans, rb_str_new_cstr("KWREST,"));
        ans = rb_str_concat(ans, rb_ary_pop(types));

        flag = true;
    }

    for(int i = 0; i < has_block; i++)
    {
        if(flag)
            ans = rb_str_concat(ans, rb_str_new_cstr(";"));

        ans = rb_str_concat(ans, rb_str_new_cstr("BLOCK,"));
        ans = rb_str_concat(ans, rb_ary_pop(types));

        flag = true;
    }


    return ans;
}

VALUE is_call_info_needed(VALUE self)
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;


    cfp += 3;

    unsigned int has_opt = cfp->iseq->body->param.flags.has_opt;
    unsigned int has_kw = cfp->iseq->body->param.flags.has_kw;

    if(has_opt || has_kw)
        return Qtrue;
    else
        return Qfalse;
}