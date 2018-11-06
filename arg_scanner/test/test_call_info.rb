#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))

class TestCallInfoWrapper

  def sqr(z1 = 10, z2 = 11, z3 = 13, z4 = 14, z5, z6, z7, z8, y: '0', x: "40")

  end

  def sqr2(z0, z1 = 2, z2 = 10, z3 = 2, z4 = 0, y: 1, x: 30, z: '40')

  end

  def foo(a, b, c, *d, e)

  end

  def foo2(*args)

  end

  def foo3(b: 2, c: '3', **args)

  end

  def foo4(b: 2, c:, d: "1", dd: 1, ddd: '111', **args)

  end

  def foo5(b)

  end

end

class TestCallInfo < Test::Unit::TestCase

  # @!attribute [r] type_tracker
  #   @return [TestTypeTracker]
  attr_reader :type_tracker

  def setup
    @call_info_wrapper = TestCallInfoWrapper.new
    @type_tracker = TestTypeTracker.instance
  end

  def teardown

  end

  def test_simple
    type_tracker.enable do
      @call_info_wrapper.sqr2(10, 11)
    end

    assert_not_nil type_tracker.last_call_info
    #assert type_tracker.last_call_info.size == 2
    #assert type_tracker.last_call_info[0] == "sqr2"
    assert_equal 2, type_tracker.last_call_info[0]
  end

  def test_simple_req_arg
    type_tracker.enable do
      @call_info_wrapper.foo5(10)
    end

    assert_nil type_tracker.last_call_info
  end

  def test_simple_kw
    type_tracker.enable do
      @call_info_wrapper.sqr2(10, 11, x: 10, y: 1)
    end

    assert_not_nil type_tracker.last_call_info
    #assert type_tracker.last_call_info.size == 3
    #assert type_tracker.last_call_info[0] == "sqr2"
    assert_equal 4, type_tracker.last_call_info[0]
    assert_equal "x,y", type_tracker.last_call_info[1]
  end

  def test_rest
    type_tracker.enable do
      @call_info_wrapper.foo2(1, 2, 3, 4, 5, 6, 7, 8)
    end

    assert_not_nil type_tracker.last_call_info
    #assert type_tracker.last_call_info.size == 2
    #assert type_tracker.last_call_info[0] == "foo2"
    assert_equal 8, type_tracker.last_call_info[0]
  end

  def test_post_and_rest
    type_tracker.enable do
      @call_info_wrapper.foo(1, 2, 3, 4, 5, 6, 7, 8)
    end

    #coz it is obvious that all the arguments were passed (they are all required)
    assert_not_nil type_tracker.last_call_info
    #assert type_tracker.last_call_info.size == 2
    #assert type_tracker.last_call_info[0] == "foo"
    #assert type_tracker.last_call_info[0] == 8
  end

  def test_kwrest
    type_tracker.enable do
      @call_info_wrapper.foo3(a: 1, b: 2, c: 3, d: 4)
    end

    assert_not_nil type_tracker.last_call_info
    #assert type_tracker.last_call_info.size == 3
    #assert type_tracker.last_call_info[0] == "foo3"
    assert_equal 4, type_tracker.last_call_info[0]
    assert_equal "a,b,c,d", type_tracker.last_call_info[1]
  end

  def test_rest_and_reqkw_args
    type_tracker.enable do
      @call_info_wrapper.foo4(b: "hello", c: 'world', e: 1, f: "not")
    end

    assert_not_nil type_tracker.last_call_info
    #assert type_tracker.last_call_info.size == 3
    #assert type_tracker.last_call_info[0] == "foo4"
    assert_equal 4, type_tracker.last_call_info[0]
    assert_equal "b,c,e,f", type_tracker.last_call_info[1]

  end
end