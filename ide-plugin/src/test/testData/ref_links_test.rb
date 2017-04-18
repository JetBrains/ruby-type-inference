require '../../../resources/type_tracker.rb'

class A

end


class B
  def test1

  end

  def test2

  end
end

A.class_eval <<Foo
def doo(a, b, c)
  a
end
Foo

A.new.doo('1', '2', '3')
A.new.doo(1, '2', 3)
A.new.doo(B.new, A.new, B.new).<caret>