def catch(a); end

def dont_catch_2(a); end

def catch_2(a)
  dont_catch_2(a)
end

def dont_catch_3(&a)
  yield(a)
end

def catch_3(&a)
  dont_catch_3(&a)
end
