#!/usr/bin/env ruby

require 'json'
require 'set'

if ARGV.length < 3
  puts("state_filter.rb <in-file> <out-file> [<list-of-names]")
  exit
end

json = JSON.parse(File.read(ARGV[0]))

modules2names = {}

json["modules"].each {|mod| modules2names[mod["name"]] = mod}

visited = Set.new
queue = Queue.new
ARGV[2..-1].each do |it|
  visited.add(it)
  queue.push(it)
end

until queue.empty? do
  elem = modules2names[queue.pop] || next
  (elem["singleton_class_included"] + elem["included"] + [elem["superclass"]]).each do |mod|
    queue.push(mod) if visited.add?(mod)
  end
end

output_modules = visited.map do |mod|
  modules2names[mod]
end.compact

File.write(ARGV[1], JSON.pretty_generate({:modules => output_modules, :load_path => json["load_path"]}))
