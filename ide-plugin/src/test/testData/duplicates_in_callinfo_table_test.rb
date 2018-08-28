def foo(a)
  if a == "str"
    return a
  end
  false
end

foo("str")
foo("not str")
10.times { foo("str") }
10.times { foo(false) }
