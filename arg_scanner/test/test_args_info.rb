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

  def foo2(a, b = 1)

  end

  attr_accessor :args_info
  attr_accessor :trace

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
      end
    end
  end

end

class TestArgsInfo < Test::Unit::TestCase
  def setup
    @args_info_wrapper = TestArgsInfoWrapper.new
  end

  def teardown

  end

  def test_simple_req_arg
    @args_info_wrapper.foo(Date.new)
    @args_info_wrapper.trace.disable

    assert @args_info_wrapper.args_info == nil
  end

  def test_req_and_opt_arg
    @args_info_wrapper.foo2(Date.new)
    @args_info_wrapper.trace.disable

    p @args_info_wrapper.args_info

    assert @args_info_wrapper.args_info.size == 2
    assert @args_info_wrapper.args_info[0] == "REQ,Date"
    assert @args_info_wrapper.args_info[1] = "OPT,Fixnum"

  end

end

