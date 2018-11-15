def foo(a)
  if a == "str"
    return a
  end
  false
end

foo("str")
foo("not str")
3.times { foo("str") }
3.times { foo(false) }
