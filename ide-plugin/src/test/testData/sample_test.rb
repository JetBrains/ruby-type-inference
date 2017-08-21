require 'date'

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
def foo(a)
  B.new
end
Foo

x = A.new.foo(C.new).<caret>