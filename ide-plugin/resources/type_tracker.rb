#!/usr/bin/env ruby

require 'set'
require 'socket'
require 'json'
require 'singleton'

require 'arg_scanner'


class RSignature

  def to_json

    return JSON.generate({
                             :method_name => @method_name.to_s,
                             :call_info_argc => @call_info_argc.to_s,
                             :call_info_mid => @call_info_mid.to_s,
                             :call_info_kw_args => @call_info_kw_args.to_s,
                             :receiver_name => @receiver_name.to_s,
                             :args_info => @args_info.to_s,
                             :return_type_name => @return_type_name.to_s,
                             :gem_name => @gem_name.to_s,
                             :gem_version => @gem_version.to_s,
                             :visibility => @visibility.to_s,
                             :path => @path.to_s,
                             :lineno => @lineno.to_s
                         })
  end

  def initialize(method_name, receiver_name, args_info, return_type_name, gem_name, gem_version, visibility, call_info_mid, call_info_argc, call_info_kw_args, path, lineno)
    @method_name = method_name
    @receiver_name = receiver_name
    @args_info = args_info
    @return_type_name = return_type_name
    @gem_name = gem_name
    @gem_version = gem_version
    @visibility = visibility
    @call_info_mid = call_info_mid
    @call_info_argc = call_info_argc
    @call_info_kw_args = call_info_kw_args
    @path = path
    @lineno = lineno
  end

end

class TypeTracker
  include Singleton

  def initialize


    @signatures = Array.new
    @cache = Set.new
    @socket = TCPSocket.new('127.0.0.1', 7777)

    TracePoint.trace(:call, :return, :raise) do |tp|
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
        @signatures.push([nil, nil, nil, nil, nil, nil, nil]) if tp.event == :call
      end
    end


  end

  def at_exit
    @socket.close
  end

  def putToSocket(messege)
    @socket.puts(messege)
  end


  private
  def handle_call(tp)

    method_name = tp.method_id

    lineno = tp.lineno
    path = tp.path

    args_info = ArgScanner.get_args_info

    if (ArgScanner.is_call_info_needed)
      call_info = ArgScanner.get_call_info

      call_info_mid = call_info[0]
      call_info_argc = call_info[1]
      call_info_kw_args = call_info[2]
    else
      call_info_mid = nil
      call_info_argc = nil
      call_info_kw_args = nil
    end


    #key = [method_name, args_info, call_info_mid, call_info_argc, call_info_kw_args].hash

    #if @cache.add?(key)
    @signatures.push([method_name, args_info, call_info_mid, call_info_argc, call_info_kw_args, path, lineno])
    #else
    #  @signatures.push([nil, nil, nil, nil, nil, nil, nil])
    #end
  end

  private
  def handle_return(tp)

    method_name, args_info, call_info_mid, call_info_argc, call_info_kw_args, path, lineno = @signatures.pop

    p method_name.to_s + ':' + args_info.to_s

    if method_name

      receiver_name = tp.defined_class.name ? tp.defined_class : tp.defined_class.superclass
      return_type_name = tp.return_value.class.to_s

      key = [method_name, args_info, call_info_mid, call_info_argc, call_info_kw_args, return_type_name].hash


      if @cache.add?(key)
        matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
        gem_name, gem_version = matches[0] ? matches[0].split('-') : ['', '']

        if tp.defined_class.public_method_defined?(tp.method_id)
          visibility = 'PUBLIC'
        elsif tp.defined_class.protected_method_defined?(tp.method_id)
          visibility = 'PROTECTED'
        else
          visibility = 'PRIVATE'
        end

        message = RSignature.new(method_name, receiver_name, args_info, return_type_name, gem_name, gem_version, visibility, call_info_mid, call_info_argc, call_info_kw_args, path, lineno)

        json_mes = message.to_json
        p json_mes
        putToSocket(json_mes)

      end

    end

  end

end


type_tracker = TypeTracker.instance
