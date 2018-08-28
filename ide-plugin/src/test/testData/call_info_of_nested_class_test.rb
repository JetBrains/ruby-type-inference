module M
  class A
    def foo(a)
      a
    end
  end
end

a = M::A.new
a.foo(a)