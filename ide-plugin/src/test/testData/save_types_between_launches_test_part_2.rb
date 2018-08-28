class A
    def foo(a)
        if a == "str1"
            return :symbol
        end
        if a == "str2"
            return /some regex/
        end
        if a.kind_of? Class
            return A.new
        end
        if a.kind_of? TrueClass
            return false
        end
    end
end

a = A.new

a.foo(true)
a.foo("str2")
