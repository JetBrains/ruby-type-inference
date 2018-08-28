class A
    def foo(a, b)
        a || b
    end

    def bar(a)
        a && A.new
    end
end

a = A.new

a.foo("Hey", String)

a.bar(true)
a.bar(false)
a.bar(:symbol)
