#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))
require 'date'

include ArgScanner

#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))

include ArgScanner

class TestArgsInfoWrapper

  def foo(a)

  end

  def foo2(a = 1)

  end

  attr_accessor :args_info

  def handle_call(tp)

    if ArgScanner.is_call_info_needed
      ArgScanner.get_args_info
    else
      nil
    end
  end

  def initialize

    @trace = TracePoint.trace(:call) do |tp|
      case tp.event
        when :call
          @args_info = handle_call(tp)
          @trace.disable
      end
    end
  end

end

class TestArgScanner < Test::Unit::TestCase
  def test_simple_req_arg
    wrapper = TestArgsInfoWrapper.new
    wrapper.foo(Date.new)
    ans = wrapper.args_info

    assert ans == nil
  end

  def test_simple_opt_arg
    wrapper = TestArgsInfoWrapper.new
    wrapper.foo2(Date.new)
    ans = wrapper.args_info

    assert ans.size == 1
    assert ans[0] == "OPT,Date"
  end

end

