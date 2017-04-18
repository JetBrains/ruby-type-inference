require 'date'
require '../../../resources/type_tracker.rb'

class A

end

class C

end

class B
  def test1

  end

  def test2

  end
end

A.class_eval <<Foo
def foo1(kw: a)
  p '!'
  B.new
end
Foo

x = A.new.foo1(kw: C.new).<caret>