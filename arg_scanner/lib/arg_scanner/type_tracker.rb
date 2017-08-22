require 'set'
require 'socket'
require 'singleton'

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

  def put_to_socket(message)
    socket.puts(message)
  end

  private
  def handle_call(tp)
    #handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path)
    signature = ArgScanner.handle_call(tp.lineno, tp.method_id, tp.path)
    signatures.push(signature)
  end

  def handle_return(tp)
    unless signatures.empty?
      signature = signatures.pop

      receiver_name = tp.defined_class.to_s
      return_type_name = tp.return_value.class.to_s

      json = ArgScanner.handle_return(signature, receiver_name, return_type_name)

      if cache.add?(json)
        matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
        gem_name, gem_version = matches[0] ? matches[0].split('-') : ['', '']
        json += '"gem_name":"' + gem_name.to_s + '","gem_version":"' + gem_version.to_s + '"}'
        put_to_socket(json)
      end
    end
  end

end
