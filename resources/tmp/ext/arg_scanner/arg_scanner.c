#include <stdio.h>
#include <stddef.h>
#include <stdint.h>
#include "ruby.h"
#include "vm_core.h"
#include "iseq.h"
#include "vm_insnhelper.h"
#include "method.h"


#define MAX_POSBUF 128
#define ruby_current_thread ((rb_thread_t *)RTYPEDDATA_DATA(rb_thread_current()))


#define VM_CFP_CNT(th, cfp) \
  ((rb_control_frame_t *)((th)->stack + (th)->stack_size) - (rb_control_frame_t *)(cfp))

VALUE mArgScanner = Qnil;
vm_call_handler *destination = NULL;

void Init_arg_scanner();
VALUE method_test(VALUE self);


void Init_arg_scanner() {
  mArgScanner = rb_define_module("ArgScanner");
  rb_define_module_function(mArgScanner, "getCallinfo", reinterpret_cast(method_test), 0);
}

VALUE method_test(VALUE self) {
    //return Qnil;
    //if(flag == Qtrue)
    //    fprintf(stderr, "BLOCK_GIVEN\n");
    rb_thread_t *thread;

    thread = ruby_current_thread;
    rb_control_frame_t *cfp = thread->cfp;

    int cnt = 0;

//    fprintf(stderr, "---go1---\n");

    int blockFlag = 0;

    while(cfp != NULL)
    {
        cnt++;
//        if(cnt == 4 && cfp->iseq != NULL)
//        {
//            if(cfp->iseq->body->param.flags.has_block == 1)
//                blockFlag = 1;
//            VALUE ans = rb_str_new(0, 0);
//
//            ans = rb_iseq_disasm(cfp->iseq);
//            fprintf(stderr, "Ans(%d) : %s\n", cnt, StringValueCStr(ans));
//            const struct iseq_line_info_entry *table = cfp->iseq->body->line_info_table;
//            int size = cfp->iseq->body->catch_table->size;
//            if(cfp->iseq->body != NULL && cfp->iseq->body->ci_entries != NULL)
//                        {
//                            struct rb_call_info *ci = (struct rb_call_info *)cfp->iseq->body->ci_entries;
//                            if(ci->flag & VM_CALL_SUPER)
//                            {
//                                fprintf(stderr, "Pc11\n");
//                                //if(blockFlag == 0)
//                                //    blockFlag = 1;
//                            }
//                            if(ci->flag & VM_CALL_FCALL)
//                            {
//                                fprintf(stderr, "Pc12\n");
//                                //if(blockFlag == 0)
//                                //    blockFlag = 1;
//                            }
//                            if(ci->flag & VM_CALL_VCALL)
//                            {
//                                fprintf(stderr, "Pc13\n");
//                                //if(blockFlag == 0)
//                                //    blockFlag = 1;
//                            }
//                        }
//        }

        if(cnt >= 5 && cfp->iseq != NULL)
        {
//            fprintf(stderr, "--ISEQ NOT NULL--\n");
//            if(VM_FRAME_TYPE(cfp) == VM_FRAME_MAGIC_IFUNC)
//            {
//                return Qnil;
//            }
//
            if(cfp->pc == NULL || cfp->iseq->body == NULL)
            {
                cfp++;
                continue;
            }

            const rb_iseq_t *iseq = cfp->iseq;
//            fprintf(stderr, "--ISEQ NOT NULL1--\n");
            VALUE ans = rb_str_new(0, 0);
//            ans = rb_iseq_disasm(iseq);
//            fprintf(stderr, "Ans : %s\n", StringValueCStr(ans));
//


              ptrdiff_t pc = cfp->pc - cfp->iseq->body->iseq_encoded;
//              fprintf(stderr, "--ISEQ NOT NULL2--\n");
//            fprintf(stderr, "PcORIG : %d\n", cfp->pc - iseq->body->iseq_encoded);
//
//            const struct iseq_line_info_entry *table = iseq->body->line_info_table;
//
//            const struct iseq_catch_table *ct;
//                        ct = cfp->iseq->body->catch_table;
//                        const struct iseq_catch_table_entry *entry;
//                        //entry = &ct->entries[i];
//                        if (ct) for (int i = 0; i < ct->size; i++) {
//                                    			    entry = &ct->entries[i];
//                                    			   fprintf(stderr, "ENTRY : %d %d %d %d\n", entry->start, entry->end, entry->cont, entry->sp);
//                                    			}
//                        fprintf(stderr, "ENTRY : %d %d %d %d\n", entry->start, entry->end, entry->cont, entry->sp);
//            for(int j = 0; j < iseq->body->line_info_size; j++)
//                fprintf(stderr, "info_table[%d] = %d %d\n", j, table[j].position, table[j].line_no);
//
//            if(iseq->body != NULL && iseq->body->ci_entries != NULL)
//            {
//                struct rb_call_info *ci = (struct rb_call_info *)iseq->body->ci_entries;
//                if(ci->flag & VM_CALL_SUPER)
//                {
//                    fprintf(stderr, "Pc1\n");
//                    if(blockFlag == 0)
//                        blockFlag = 1;
//                }
//                if(ci->flag & VM_CALL_FCALL)
//                {
//                    fprintf(stderr, "Pc2\n");
//                    //if(blockFlag == 0)
//                    //    blockFlag = 1;
//                }
//                if(ci->flag & VM_CALL_VCALL)
//                {
//                    fprintf(stderr, "Pc3\n");
//                    if(blockFlag == 0)
//                        blockFlag = 1;
//                }
//            }
//
//
//
//            if(flag == Qtrue)
//            {
//                fprintf(stderr, "Pc2 : %d\n", pc);
//                if(blockFlag == 0)
//                {
//                    blockFlag = 1;
//                }
//                    //pc--;
//            }
//
            const VALUE *iseq_original = rb_iseq_original_iseq((rb_iseq_t *)iseq);
//            fprintf(stderr, "--ISEQ NOT NULL3--\n");
            int pos = 0;
            ans = rb_str_new(0, 0);
//            ans = rb_iseq_disasm(iseq);
//            fprintf(stderr, "--go--\n");
//            fprintf(stderr, "Ans(%d)%d : %s\n", cnt, blockFlag, StringValueCStr(ans));
            for (int n = 0; n < pc;) {
                ans = rb_str_new(0, 0);
                n += rb_iseq_disasm_insn(ans, iseq_original, n, iseq, 0);
                //if(n < pc)
                //    pos = n;
            }
//            pc -= blockFlag;
//            fprintf(stderr, "Pc : %d %d\n", pc, pos);
//            fprintf(stderr, "Ans(%d) : %s\n", cnt, StringValueCStr(ans));
            //rb_iseq_disasm_insn(ans, iseq_original, pos, iseq, 0);
            return ans;
        }

        if(cnt >= 6)
        {
            //fprintf(stderr, "%d\n", cnt);
            return Qnil;
        }

        cfp++;
    }

}
