#!/usr/bin/env ruby

require 'logger'
require 'set'
require 'sqlite3'

class TypeTracker
  @@logger = Logger.new($stdout)
  @@signatures = Hash.new { |h, k| h[k] = Array.new }
  @@db_connection = SQLite3::Database.open(__dir__ + '/CallStat.db')

  @@INSERT = "INSERT OR REPLACE INTO signatures VALUES('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')"

  @@trace = TracePoint.trace(:call, :return, :raise) do |tp|
    @@trace.disable do
      case tp.event
        when :call
          binding = tp.binding

          params = tp.defined_class.instance_method(tp.method_id).parameters
          args_type = params.inject([]) do |pt, p|
            val = binding.local_variable_get(p[1]) if p[1] && binding.local_variable_defined?(p[1])
            pt << (p[0] == :rest && val ? val.map { |rest_p| rest_p.class } : val.class)
          end
          args_type_name = args_type.flatten.map {|arg_type| arg_type }.join(';')

          args_info = params.inject([]) do |pt, p|
            pt << "#{p[0]},#{p[1]}"
          end
          args_info = args_info.join(';')

          @@signatures[Thread.current.object_id].push([args_type_name, args_info])
        when :return
          args_type_name, args_info = @@signatures[Thread.current.object_id].pop

          if args_type_name
            method_name = tp.method_id
            receiver_name = tp.defined_class.name ? tp.defined_class : tp.defined_class.superclass
            return_type_name = tp.return_value.class

            matches = tp.path.scan(/\w+-\d+\.\d+\.\d+/)
            gem_name, gem_version = matches[2] ? matches[2].split('-') : ['', '']

            if tp.defined_class.public_method_defined?(tp.method_id)
              visibility = 'PUBLIC'
            elsif tp.defined_class.protected_method_defined?(tp.method_id)
              visibility = 'PROTECTED'
            else
              visibility = 'PRIVATE'
            end

            sql = @@INSERT % [method_name, receiver_name, args_type_name, args_info, return_type_name,
                              gem_name, gem_version, visibility]
            @@logger.info(sql)
            @@db_connection.execute(sql)
          end
        when :raise
          @@signatures[Thread.current.object_id].pop
      end
    end
  end
  @@trace.disable

  def self.disable
    @@trace.disable
  end

  def self.enable
    @@trace.enable
  end
end

if ARGV.empty?
  puts 'Must specify a script to run'
  exit(1)
end

abs_prog_script = File.expand_path(ARGV.shift)

TypeTracker.enable
load abs_prog_script