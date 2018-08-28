class AClass
    def foo(a)
        if a.kind_of? String
            :symbol
        else
            true
        end
    end
end

AClass.new.foo("String")
