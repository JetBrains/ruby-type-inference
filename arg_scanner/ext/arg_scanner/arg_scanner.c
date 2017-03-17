#include <stdio.h>
#include <stddef.h>
#include <stdint.h>
#include "arg_scanner.h"

#define MAX_POSBUF 128
#define ruby_current_thread ((rb_thread_t *)RTYPEDDATA_DATA(rb_thread_current()))


#define VM_CFP_CNT(th, cfp) \
  ((rb_control_frame_t *)((th)->stack + (th)->stack_size) - (rb_control_frame_t *)(cfp))

VALUE mArgScanner = Qnil;

void Init_arg_scanner();
VALUE get_args_info(VALUE self);
VALUE get_call_info(VALUE self);


void Init_arg_scanner() {
  mArgScanner = rb_define_module("ArgScanner");
  rb_define_module_function(mArgScanner, "get_call_info", get_call_info, 0);
  rb_define_module_function(mArgScanner, "get_args_info", get_args_info, 0);
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
    //ep -= 2;

    int param_size = cfp->iseq->body->param.size;

    int lead_num = cfp->iseq->body->param.lead_num;
    int opt_num = cfp->iseq->body->param.opt_num;
    int rest_start = cfp->iseq->body->param.rest_start;
    int post_start = cfp->iseq->body->param.post_start;
    int post_num = cfp->iseq->body->param.post_num;
    int block_start = cfp->iseq->body->param.block_start;

    int kw_num = 0;

    if(cfp->iseq->body->param.keyword != NULL)
    {
        const ID *keywords = cfp->iseq->body->param.keyword->table;
        kw_num = cfp->iseq->body->param.keyword->num;
    }


    VALUE ans = rb_ary_new();

//    for(int i = 0; i < cfp->iseq->body->param.keyword->num; i++)
//    {
//        ID key = keywords[i];
//        VALUE kwName = rb_id2str(key);
//        rb_ary_push(ans, kwName);
//    }

//    return ans;
//
    unsigned int has_lead = cfp->iseq->body->param.flags.has_lead;
    unsigned int has_opt = cfp->iseq->body->param.flags.has_opt;
    unsigned int has_rest = cfp->iseq->body->param.flags.has_rest;
    unsigned int has_post = cfp->iseq->body->param.flags.has_post;
    unsigned int has_kw = cfp->iseq->body->param.flags.has_kw;
    unsigned int has_kwrest = cfp->iseq->body->param.flags.has_kwrest;
    unsigned int has_block = cfp->iseq->body->param.flags.has_block;

    unsigned int ambiguous_param0 = cfp->iseq->body->param.flags.has_lead;

    //fprintf(stdout, "param_size:%d\nlead_num:%d\n opt_num:%d\n rest_start:%d\n post_start:%d\n post_num:%d\n block_start:%d\n kw_num:%d\n", param_size, lead_num, opt_num, rest_start, post_start, post_num, block_start, kw_num);
    //fprintf(stdout, "has_lead:%d\nhas_opt:%d\n has_rest:%d\n has_post:%d\n has_kw:%d\n has_kwrestd\n has_block:%d\n ambiguous_param0:%d\n", has_lead, has_opt, has_rest, has_post, has_kw, has_kwrest, has_block, ambiguous_param0);
    //fflush(stdout);

    if(has_kw)
        param_size--;

    for(int i = param_size - 1; i >= 0; i--)
    {
        //VALUE klass = rb_any_to_s(ep);//rb_class_real(CLASS_OF(ep));
        VALUE klass = rb_class_real(CLASS_OF(*(ep - i - 2)));
        rb_ary_push(ans, klass);

        //ep--;
    }

    return ans;
}