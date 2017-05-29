#!/usr/bin/env ruby

require 'set'
require 'socket'
require 'singleton'

require 'arg_scanner'


class TypeTracker
  include Singleton

  def initialize


    @signatures = Array.new
    @cache = Set.new
    @socket = TCPSocket.new('127.0.0.1', 7777)

    TracePoint.trace(:call, :return) do |tp|

        case tp.event
          when :call
            handle_call(tp)
          when :return
            handle_return(tp)
        end

    end


  end

  attr_accessor :cache
  attr_accessor :socket
  attr_accessor :signatures

  def at_exit
    socket.close
  end

  def putToSocket(messege)
    socket.puts(messege)
  end


  private
  def handle_call(tp)

    signature = ArgScanner.handle_call()
    signatures.push(signature)
  end

  private
  def handle_return(tp)
    if(!signatures.empty?)
        signature = signatures.pop

        receiver_name = tp.defined_class.to_s
        return_type_name = tp.return_value.class.to_s

        json = ArgScanner.handle_return(signature, receiver_name, return_type_name)

        if cache.add?(json)
            matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
            gem_name, gem_version = matches[0] ? matches[0].split('-') : ['', '']
            json += '"gem_name":"' + gem_name.to_s + '","gem_version":"' + gem_version.to_s + '"}'
            putToSocket(json)
        end
    end

  end

end


type_tracker = TypeTracker.instance
