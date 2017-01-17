#!/usr/bin/env ruby

require 'logger'
require 'set'
require 'socket'
require 'json'

require_relative 'arg_scanner/arg_scanner'

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
                             :visibility => @visibility.to_s,
                             :path => @path.to_s,
                             :lineno => @lineno.to_s
                         })
  end

  def initialize(method_name, receiver_name, args_type_name, args_info, return_type_name, gem_name, gem_version, visibility, call_info_mid, call_info_argc, call_info_kw_args, path, lineno)
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
    @path = path
    @lineno = lineno
  end

end

class TypeTracker


  def start_control(host, port)
    socket_thread = Thread.new do
      begin
        # 127.0.0.1 seemingly works with all systems and with IPv6 as well.
        # "localhost" and nil have problems on some systems.
        host ||= '127.0.0.1'
        socket = TCPSocket.new(host, port)

        while (true) do

          while(!@socketQueue.empty?())
            val = @socketQueue.pop

            socket.puts(val)
          end
          sleep(0.5)

        end
      rescue
        p 'Error'
      end
    end
    socket_thread
  end


  def initialize

    @signatures = Array.new
    @cache = Set.new
    @socketQueue = Queue.new

    @socket_thread = start_control('127.0.0.1', 7777)
    #ObjectSpace.define_finalizer( self, self.class.finalize(@socket_thread) )

    at_exit do
      sleep(0.5)
      Thread.kill(@socket_thread)
      socket = TCPSocket.new('127.0.0.1', 7777)
      socket.puts('break connection')
    end

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

    #p 'Method name is ' + method.to_s
    #STDOUT.flush

    call_info = getCallinfo


    call_info_mid = call_info[/\S*:/].chop
    call_info_argc = call_info[/\:\d*/]
    call_info_argc[0] = ''
    call_info_kw_args = call_info.partition('kw:[').last.chomp(']')
    #end

    path = tp.path
    lineno = tp.lineno

    args_type_name = method.parameters.inject([]) do |pt, p|
      pt << (p[1] ? binding.local_variable_get(p[1]).class : NilClass)
    end.join(';')

    # this.myReceiverName = org.jetbrains.ruby.runtime.signature.getReceiverName();
    # this.myGemName = org.jetbrains.ruby.runtime.signature.getGemName();
    # this.myGemVersion = org.jetbrains.ruby.runtime.signature.getGemVersion();
    # this.myVisibility = org.jetbrains.ruby.runtime.signature.getVisibility();


    key = [method, args_type_name, call_info_mid, call_info_argc, call_info_kw_args, path, lineno].hash
    if @cache.add?(key)
      args_info = method.parameters.inject([]) do |pt, p|
        pt << "#{p[0]},#{p[1]},#{p[1] ? binding.local_variable_get(p[1]).class : NilClass}"
      end.join(';')

      @signatures.push([method, args_type_name, args_info, call_info_mid, call_info_argc, call_info_kw_args, path, lineno])
    else
      @signatures.push([nil, nil, nil, nil, nil, nil, nil, nil])
    end

  end

  private
  def handle_return(tp)
    method, args_type_name, args_info, call_info_mid, call_info_argc, call_info_kw_args, path, lineno = @signatures.pop

    if method
      method_name = tp.method_id

      receiver_name = tp.defined_class.name ? tp.defined_class : tp.defined_class.superclass
      return_type_name = tp.return_value.class

      matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
      gem_name, gem_version = matches[0] ? matches[0].split('-') : ['', '']

      if tp.defined_class.public_method_defined?(tp.method_id)
        visibility = 'PUBLIC'
      elsif tp.defined_class.protected_method_defined?(tp.method_id)
        visibility = 'PROTECTED'
      else
        visibility = 'PRIVATE'
      end

      message = RSignature.new(method_name, receiver_name, args_type_name, args_info, return_type_name, gem_name, gem_version, visibility, call_info_mid, call_info_argc, call_info_kw_args, path, lineno)

      @socketQueue << message.to_json
    end
  end
end

type_tracker = TypeTracker.new