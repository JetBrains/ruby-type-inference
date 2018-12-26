require_relative 'gem_like'

catch('hey')

catch_2('bro')

def foo(a); end

catch_3(&method(:foo))
