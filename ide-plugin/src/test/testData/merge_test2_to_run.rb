class A

end

class C

end

class B1
  def test1

  end

  def test2

  end
end

class B2
  def test3

  end

  def test4

  end
end

A.class_eval <<Foo
def doo2(a, b)
    B2.new
end
Foo


A.new.doo2(C.new, C.new)
A.new.doo2(C.new, C.new)