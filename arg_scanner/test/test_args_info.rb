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

  def foo3(**rest)

  end

  def foo4(kw: 1, **rest)

  end

  def foo5(kw:, **rest)

  end


  attr_accessor :args_info
  attr_accessor :trace

  def handle_call(tp)

    ArgScanner.get_args_info
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

  def test_simple_kwrest
    @args_info_wrapper.foo3(a: Date.new, kkw: 'hi')
    @args_info_wrapper.trace.disable

    assert_not_nil @args_info_wrapper.args_info
    assert @args_info_wrapper.args_info.size == 1
    assert @args_info_wrapper.args_info[0] == "KEYREST,Hash"

  end

  def test_req_and_opt_arg
    @args_info_wrapper.foo2(Date.new)
    @args_info_wrapper.trace.disable

    assert_not_nil @args_info_wrapper.args_info
    assert @args_info_wrapper.args_info.size == 2
    assert @args_info_wrapper.args_info[0] == "REQ,Date"
    assert @args_info_wrapper.args_info[1] = "OPT,Fixnum"

  end

  def test_optkw_and_empty_kwrest
    @args_info_wrapper.foo4(kw: Date.new)
    @args_info_wrapper.trace.disable

    assert_not_nil @args_info_wrapper.args_info
    assert @args_info_wrapper.args_info.size == 2
    assert @args_info_wrapper.args_info[0] == "KEY,Date,kw"
    assert @args_info_wrapper.args_info[1] = "KEYREST,Nil"

  end

  def test_reqkw_and_empty_kwrest
    @args_info_wrapper.foo5(kw: Date.new)
    @args_info_wrapper.trace.disable

    assert_not_nil @args_info_wrapper.args_info
    assert @args_info_wrapper.args_info.size == 2
    assert @args_info_wrapper.args_info[0] == "KEYREQ,Date,kw"
    assert @args_info_wrapper.args_info[1] = "KEYREST,Nil"

  end

  def test_reqkw_and_kwrest
    @args_info_wrapper.foo5(kw: Date.new, aa: 1, bb: '1')
    @args_info_wrapper.trace.disable

    assert_not_nil @args_info_wrapper.args_info
    p @args_info_wrapper.args_info
    assert @args_info_wrapper.args_info.size == 2
    assert @args_info_wrapper.args_info[0] == "KEYREQ,Date,kw"
    assert @args_info_wrapper.args_info[1] = "KEYREST,Hash"

  end

  def test_optkw_and_kwrest
    @args_info_wrapper.foo4(aa: 1, bb: '1')
    @args_info_wrapper.trace.disable

    assert_not_nil @args_info_wrapper.args_info
    assert @args_info_wrapper.args_info.size == 2
    assert @args_info_wrapper.args_info[0] == "KEY,Fixnum,kw"
    assert @args_info_wrapper.args_info[1] = "KEYREST,Hash"

  end

end
