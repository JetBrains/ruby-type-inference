#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))
require 'date'

class TestArgsInfoWrapper

  def foo(a)

  end

  def foo2(a, b = 1)

  end

  def foo3(**rest)

  end

  def foo4(kw: 1, **rest1)

  end

  def foo5(kw:, **rest)

  end

  def foo6(a, *rest, b)

  end

  def initialize

    # @trace = TracePoint.new(:call) do |tp|
    #   case tp.event
    #     when :call
    #       tp.binding.local_variables.each { |v| p tp.binding.eval v.to_s }
    #       ArgScanner.handle_call(tp.lineno, tp.method_id, tp.path)
    #       @args_info = ArgScanner.get_args_info
    #       p @args_info
    #   end
    # end
  end
end

class TestArgsInfo < Test::Unit::TestCase

  # @!attribute [r] type_tracker
  #   @return [TestTypeTracker]
  attr_reader :type_tracker


  def setup
    @args_info_wrapper = TestArgsInfoWrapper.new
    @type_tracker = TestTypeTracker.instance
  end

  def teardown

  end

  def test_simple_kwrest
    type_tracker.enable do
      @args_info_wrapper.foo3(a: Date.new, kkw: 'hi')
    end

    assert_equal ["KEYREST,Hash,rest"], type_tracker.last_args_info
  end

  def test_req_and_opt_arg
    type_tracker.enable do
      @args_info_wrapper.foo2(Date.new)
    end

    assert type_tracker.last_args_info[0] == "REQ,Date,a"
    assert type_tracker.last_args_info[1] == "OPT,Fixnum,b" || type_tracker.last_args_info[1] == "OPT,Integer,b"
  end

  def test_optkw_and_empty_kwrest
    type_tracker.enable do
      @args_info_wrapper.foo4(kw: Date.new)
    end

    assert_equal ["KEY,Date,kw", "KEYREST,Hash,rest1"], type_tracker.last_args_info
  end

  def test_reqkw_and_empty_kwrest
    type_tracker.enable do
      @args_info_wrapper.foo5(kw: Date.new)
    end

    assert_equal ["KEYREQ,Date,kw", "KEYREST,Hash,rest"], type_tracker.last_args_info
  end

  def test_reqkw_and_kwrest
    type_tracker.enable do
      @args_info_wrapper.foo5(kw: Date.new, aa: 1, bb: '1')
    end

    assert_equal ["KEYREQ,Date,kw", "KEYREST,Hash,rest"], type_tracker.last_args_info
  end

  def test_optkw_and_kwrest
    type_tracker.enable do
      @args_info_wrapper.foo4(aa: 1, bb: '1')
    end

    assert type_tracker.last_args_info[0] == "KEY,Fixnum,kw" || type_tracker.last_args_info[0] == "KEY,Integer,kw"
    assert type_tracker.last_args_info[1] == "KEYREST,Hash,rest1"
  end

  def test_rest
    type_tracker.enable do
      @args_info_wrapper.foo6(1, 'hi', Date.new, '1')
    end

    assert type_tracker.last_args_info[0] == "REQ,Fixnum,a" || type_tracker.last_args_info[0] == "REQ,Integer,a"
    assert type_tracker.last_args_info[1] == "REST,Array,rest"
    assert type_tracker.last_args_info[2] == "POST,String,b"
  end

  def test_empty_rest
    type_tracker.enable do
      @args_info_wrapper.foo6(1, '1')
    end

    assert type_tracker.last_args_info[0] == "REQ,Fixnum,a" || type_tracker.last_args_info[0] == "REQ,Integer,a"
    assert type_tracker.last_args_info[1] == "REST,Array,rest"
    assert type_tracker.last_args_info[2] == "POST,String,b"
  end
end
