#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))

include ArgScanner

def sqr(z1 = 10, z2 = 11, z3 = 13, z4 = 14, z5, z6, z7, z8, y: '0', x: "40")

end

def sqr2(z0, z1 = 2, z2 = 10, z3 = 2, z4 = 0, y: 1, x: 30, z: 40)

end

def foo(a, b, c, *d, e)

end

def foo2(*args)

end

def foo3(b: 2, c: 3, **args)

end

def foo3(b: 2, c:, **args)

end

def foo4(b: 2, c:, d: "1", dd: 1, ddd: '111', **args)

end

def handle_call(tp)

  ArgScanner.get_call_info
end

@trace = TracePoint.trace(:call) do |tp|
  case tp.event
    when :call
      $last_call_info = handle_call(tp)
  end
end

$last_call_info

class TestArgScanner < Test::Unit::TestCase
  def test_simple
    sqr2(10, 11)
    ans = $last_call_info

    assert ans.size == 2
    assert ans[0] == "sqr2"
    assert ans[1] == 2
  end

  def test_simple_kw
    sqr2(10, 11, x: 10, y: 1)
    ans = $last_call_info

    assert ans.size == 3
    assert ans[0] == "sqr2"
    assert ans[1] == 4
    assert ans[2].join(',') == "x,y"
  end

  def test_args
    foo2(1, 2, 3, 4, 5, 6, 7, 8)
    ans = $last_call_info

    assert ans.size == 2
    assert ans[0] == "foo2"
    assert ans[1] == 8
  end

  def test_hard
    foo(1, 2, 3, 4, 5, 6, 7, 8)
    ans = $last_call_info

    assert ans.size == 2
    assert ans[0] == "foo"
    assert ans[1] == 8
  end

  def test_rest_args
    foo3(a: 1, b: 2, c: 3, d: 4)
    ans = $last_call_info

    assert ans.size == 3
    assert ans[0] == "foo3"
    assert ans[1] == 4
    assert ans[2].join(',') == "a,b,c,d"
  end

  def test_rest_and_reqkw_args
    foo4(b: "hello", c: 'world', e: 1, f: "not")
    ans = $last_call_info

    assert ans.size == 3
    assert ans[0] == "foo4"
    assert ans[1] == 4
    assert ans[2].join(',') == "b,c,e,f"

  end
end