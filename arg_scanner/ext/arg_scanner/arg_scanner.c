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
VALUE method_test(VALUE self);


void Init_arg_scanner() {
  mArgScanner = rb_define_module("ArgScanner");
  rb_define_module_function(mArgScanner, "getCallinfo", method_test, 0);
}

VALUE method_test(VALUE self) 
{
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;

    int cnt = 0;

    // cfp++;

    // while(cnt <= 2)
    // {
    //     if(VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_METHOD || !VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_CFUNC)
    //         cnt++;

    //     // if(cfp->iseq != NULL)
    //     // {
    //     //     fprintf(stderr, "VM_FRAME_TYPE : %d; VM_FRAME_TYPE_FINISH_P : %d\n", VM_FRAME_TYPE(cfp), VM_FRAME_TYPE_FINISH_P(cfp));
    //     //     const rb_iseq_t *iseq = cfp->iseq;
    //     //     ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;
    //     //     VALUE ans = rb_str_new(0, 0);
    //     //     ans = rb_iseq_disasm(iseq);
    //     //     fprintf(stderr, "Ans : %d %s\n", pc, StringValueCStr(ans));
    //     //     fflush(stderr);
    //     // }
    //     cfp++;
    // }

    // cfp ++;

    cfp += 2;

    while(1)
    {
        // if(cfp->iseq != NULL)
        // {
        //     fprintf(stderr, "VM_FRAME_TYPE : %d; VM_FRAME_TYPE_FINISH_P : %d\n", VM_FRAME_TYPE(cfp), VM_FRAME_TYPE_FINISH_P(cfp));
        //     const rb_iseq_t *iseq = cfp->iseq;
        //     ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;
        //     VALUE ans = rb_str_new(0, 0);
        //     ans = rb_iseq_disasm(iseq);
        //     fprintf(stderr, "Ans : %d %s\n", pc, StringValueCStr(ans));
        //     fflush(stderr);
        // }

        if(VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_METHOD || VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_CFUNC)
            if(!VM_FRAME_TYPE_FINISH_P(cfp))
                break;
        if(VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_LAMBDA)
            break;
        cfp++;
    }

    cfp++;
    //if(VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_CFUNC)
    //    cfp++;

    if(cfp->iseq != NULL)
    {
        if(cfp->pc == NULL || cfp->iseq->body == NULL)
        {
            return Qnil;
        }

        //fprintf(stderr, "far\n");

        const rb_iseq_t *iseq = cfp->iseq;
            
        ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;

        // VALUE ans = rb_str_new(0, 0);
        // ans = rb_iseq_disasm(iseq);
        // fprintf(stderr, "Ans : %d %s\n", pc, StringValueCStr(ans));
        // fflush(stderr);

        const VALUE *iseq_original = rb_iseq_original_iseq((rb_iseq_t *)iseq);

        int tmp = 0;
        int indent = 1;

        for(; indent < 6; indent++)
        {
            VALUE insn = iseq_original[pc - indent];
            tmp = (int)insn;
            
            // fprintf(stderr, "PC : %d\n", pc - indent);
            // fflush(stderr);


            if(0 < tmp && tmp < 256)
            {
                if(indent < 3)
                    return Qnil;
                
                //fprintf(stderr, "MB here\n");
                struct rb_call_info *ci = (struct rb_call_info *)iseq_original[pc - indent + 1];
                
                //if(ci->mid == NULL || ci->orig_argc == NULL)
                //fprintf(stderr, "ARGC%d\n", ci->orig_argc);

                if (ci->flag & VM_CALL_KWARG) 
                {
                    struct rb_call_info_kw_arg *kw_args = ((struct rb_call_info_with_kwarg *)ci)->kw_arg;
                
                    VALUE kw_ary = rb_ary_new_from_values(kw_args->keyword_len, kw_args->keywords);
                    
                    return rb_sprintf("%"PRIsVALUE":%d, kw:[%"PRIsVALUE"]", rb_id2str(ci->mid), ci->orig_argc, rb_ary_join(kw_ary, rb_str_new2(",")));
                }

                //fflush(stderr);
                return rb_sprintf("%"PRIsVALUE":%d", rb_id2str(ci->mid), ci->orig_argc);
            }
        }
    }
        
    return Qnil;
}
