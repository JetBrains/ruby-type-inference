def foo(a); end

foo("string")

Kernel.exec("ruby", "#{File.expand_path("..", __FILE__)}/ruby_exec_part_2.rb")
