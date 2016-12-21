#!/usr/bin/env ruby

require 'logger'
require 'set'
require 'sqlite3'

class TypeTracker
  @@INSERT = "INSERT OR REPLACE INTO rsignature VALUES('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')"

  def initialize
    @logger = Logger.new($stdout)
    @signatures = Array.new
    @cache = Set.new
    @db_connection = SQLite3::Database.open(__dir__ + '/CallStat.db')

    @trace = TracePoint.trace(:call, :return, :raise) do |tp|
      begin
        case tp.event
          when :call
            handle_call(tp)
          when :return
            handle_return(tp)
          else
            @signatures.pop
        end
      rescue NameError, NoMethodError
        @signatures.push([nil, nil, nil]) if tp.event == :call
      end
    end
  end

  private
  def handle_call(tp)
    binding = tp.binding
    method = tp.defined_class.instance_method(tp.method_id)
    args_type_name = method.parameters.inject([]) do |pt, p|
      pt << (p[1] ? binding.local_variable_get(p[1]).class : NilClass)
    end.join(';')

    key = [method, args_type_name].hash
    if @cache.add?(key)
      args_info = method.parameters.inject([]) do |pt, p|
        pt << "#{p[0]},#{p[1]},#{p[1] ? binding.local_variable_get(p[1]).class : NilClass}"
      end.join(';')
      @signatures.push([method, args_type_name, args_info])
    else
      @signatures.push([nil, nil, nil])
    end
  end

  private
  def handle_return(tp)
    method, args_type_name, args_info = @signatures.pop

    if method
      method_name = tp.method_id
      receiver_name = tp.defined_class.name ? tp.defined_class : tp.defined_class.superclass
      return_type_name = tp.return_value.class

      matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
      gem_name, gem_version = matches[1] ? matches[1].split('-') : ['', '']

      if tp.defined_class.public_method_defined?(tp.method_id)
        visibility = 'PUBLIC'
      elsif tp.defined_class.protected_method_defined?(tp.method_id)
        visibility = 'PROTECTED'
      else
        visibility = 'PRIVATE'
      end

      sql = @@INSERT % [method_name, receiver_name, args_type_name, args_info, return_type_name,
                        gem_name, gem_version, visibility]
      # @logger.info(sql)
      @db_connection.execute(sql)
    end
  end
end

type_tracker = TypeTracker.new
