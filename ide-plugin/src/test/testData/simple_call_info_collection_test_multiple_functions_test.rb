class A
    def foo(a, b)
    end

    def bar(a)
    end
end

a = A.new

a.foo("Hey", String)

a.bar(true)
a.bar(false)
a.bar(:symbol)
