require 'set'
require 'socket'
require 'singleton'
require 'thread'

require_relative 'options'

module ArgScanner
  class TypeTracker
    include Singleton

    GEM_PATH_REVERSED_REGEX = /((?:[0-9A-Za-z]+\.)+\d+)-([A-Za-z0-9_-]+)/

    def initialize
      @cache = Set.new
      @socket = TCPSocket.new('127.0.0.1', 7777)
      @mutex = Mutex.new
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
    attr_accessor :mutex


    # @param [String] path
    def self.extract_gem_name_and_version(path)
      if OPTIONS.project_roots && path.start_with?(*OPTIONS.project_roots)
        return OPTIONS.no_local ? ['', ''] : ['LOCAL', OPTIONS.local_version]
      end

      reversed = path.reverse
      return ['', ''] unless GEM_PATH_REVERSED_REGEX =~ reversed

      name_and_version = Regexp.last_match
      return name_and_version[2].reverse, name_and_version[1].reverse
    end

    def signatures
      Thread.current[:signatures] ||= Array.new
    end

    def at_exit
      socket.close
    end

    def put_to_socket(message)
      mutex.synchronize {
        socket.puts(message)
      }
    end

    private
    def handle_call(tp)
      #handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path)
      signature = ArgScanner.handle_call(tp.lineno, tp.method_id, tp.path)
      signatures.push(signature)
    end

    def handle_return(tp)
      sigi = signatures
      unless sigi.empty?
        signature = sigi.pop

        defined_class = tp.defined_class
        return if !defined_class || defined_class.singleton_class?

        receiver_name = defined_class.name ? defined_class : defined_class.ancestors.first
        return_type_name = tp.return_value.class

        return if !receiver_name || !receiver_name.to_s || receiver_name.to_s.length > 200

        json = ArgScanner.handle_return(signature, return_type_name) +
            "\"receiver_name\":\"#{receiver_name}\",\"return_type_name\":\"#{return_type_name}\","

        if cache.add?(json)
          gem_name, gem_version = TypeTracker.extract_gem_name_and_version(tp.path)
          json += '"gem_name":"' + gem_name.to_s + '","gem_version":"' + gem_version.to_s + '"}'
          put_to_socket(json)
        end
      end
    end

  end
end
