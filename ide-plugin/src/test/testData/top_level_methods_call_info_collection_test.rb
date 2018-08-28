def foo(a, b)
    if a == "str"
      return /some regex/
    end
    a || b
end

foo(true, false)
foo(false, :symbol)
foo("str", true)
foo("not str", true)