#!/usr/bin/env ruby

require 'logger'
require 'set'
require 'socket'
require 'json'
require 'json'
require 'thread'
require 'singleton'

require 'arg_scanner'

include ArgScanner


class RSignature

  def to_json

    return JSON.generate({
                             :method_name => @method_name.to_s,
                             :call_info_argc => @call_info_argc.to_s,
                             :call_info_mid => @call_info_mid.to_s,
                             :call_info_kw_args => @call_info_kw_args.to_s,
                             :receiver_name => @receiver_name.to_s,
                             :args_type_name => @args_type_name.to_s,
                             :args_info => @args_info.to_s,
                             :return_type_name => @return_type_name.to_s,
                             :gem_name => @gem_name.to_s,
                             :gem_version => @gem_version.to_s,
                             :visibility => @visibility.to_s
                         })
  end

  def initialize(method_name, receiver_name, args_type_name, args_info, return_type_name, gem_name, gem_version, visibility, call_info_mid, call_info_argc, call_info_kw_args)
    @method_name = method_name
    @receiver_name = receiver_name
    @args_type_name = args_type_name
    @args_info = args_info
    @return_type_name = return_type_name
    @gem_name = gem_name
    @gem_version = gem_version
    @visibility = visibility
    @call_info_mid = call_info_mid
    @call_info_argc = call_info_argc
    @call_info_kw_args = call_info_kw_args
  end

end

class TypeTracker
  include Singleton

  def initialize


    @signatures = Array.new
    @cache = Set.new
    @socket = TCPSocket.new('127.0.0.1', 7777)
    @mutex = Mutex.new


    TracePoint.trace(:call, :return, :raise) do |tp|
      begin
        case tp.event
          when :call
            handle_call(tp)
          when :return
            handle_return(tp)
          else
            signatures.pop
        end
      rescue NameError, NoMethodError
        signatures.push([nil, nil, nil]) if tp.event == :call
      end
    end


  end

  def with_mutex
    @mutex.synchronize { yield }
  end

  def signatures
    with_mutex { @signatures }
  end

  def cache
    with_mutex { @cache }
  end

  def socket
    with_mutex { @socket }
  end

  def putToSocket(messege)
    with_mutex do
      @socket.puts(messege)
      #@socket.flush
    end
  end

  private
  def handle_call(tp)

    binding = tp.binding

    method = tp.defined_class.instance_method(tp.method_id)

    args_type_name = method.parameters.inject([]) do |pt, p|
      pt << (p[1] ? binding.local_variable_get(p[1]).class : NilClass)
    end.join(';')

    args_info = method.parameters.inject([]) do |pt, p|
      pt << "#{p[0]},#{p[1]},#{p[1] ? (binding.local_variable_get(p[1]).class.to_s) : NilClass}"
    end.join(';')

    if ((args_info.include? 'opt,') || (args_info.include? 'key,'))
      call_info = getCallinfo

      call_info_mid = call_info[/\S*:/].chop
      call_info_argc = call_info[/\:\d*/]
      call_info_argc[0] = ''
      call_info_kw_args = call_info.partition('kw:[').last.chomp(']')
    else
      call_info_mid = 'nil'
      call_info_argc = 0
      call_info_kw_args = 'nil'
    end


    signatures.push([method, args_type_name, args_info, call_info_mid, call_info_argc, call_info_kw_args])

  end

  private
  def handle_return(tp)

    method, args_type_name, args_info, call_info_mid, call_info_argc, call_info_kw_args = signatures.pop

    if method
      method_name = tp.method_id
      receiver_name = tp.defined_class.name ? tp.defined_class : tp.defined_class.superclass
      return_type_name = tp.return_value.class.to_s

      key = [method, args_type_name, call_info_mid, return_type_name, call_info_argc, call_info_kw_args].hash


      if cache.add?(key)
        matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
        gem_name, gem_version = matches[0] ? matches[0].split('-') : ['', '']

        if tp.defined_class.public_method_defined?(tp.method_id)
          visibility = 'PUBLIC'
        elsif tp.defined_class.protected_method_defined?(tp.method_id)
          visibility = 'PROTECTED'
        else
          visibility = 'PRIVATE'
        end

        message = RSignature.new(method_name, receiver_name, args_type_name, args_info, return_type_name, gem_name, gem_version, visibility, call_info_mid, call_info_argc, call_info_kw_args)

        json_mes = message.to_json
        #puts Thread.current.object_id.to_s + json_mes
        putToSocket(json_mes)

      end

    end

  end

end


type_tracker = TypeTracker.instance
