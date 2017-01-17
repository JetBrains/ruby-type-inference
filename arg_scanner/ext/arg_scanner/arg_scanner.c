#include "arg_scanner.h"

VALUE rb_mArgScanner;

void
Init_arg_scanner(void)
{
  rb_mArgScanner = rb_define_module("ArgScanner");
}
