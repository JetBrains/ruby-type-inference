require 'set'
require 'socket'
require 'singleton'
require 'thread'

require_relative 'options'

module ArgScanner

  class TypeTrackerPerformanceMonitor
    def initialize
      @enable_debug = ENV["ARG_SCANNER_DEBUG"]
      @call_counter = 0
      @handled_call_counter = 0
      @submitted_call_counter = 0
      @old_handled_call_counter = 0
      @time = Time.now
    end


    def on_call
      @submitted_call_counter += 1
    end

    def on_return
      @call_counter += 1

      if enable_debug && call_counter % 100000 == 0
        $stderr.puts("calls #{call_counter} handled #{handled_call_counter} submitted #{submitted_call_counter}"\
                     "delta  #{handled_call_counter - old_handled_call_counter} time #{Time.now - @time}")
        @old_handled_call_counter = handled_call_counter
        @time = Time.now
      end
    end

    def on_handled_return
      @handled_call_counter += 1
    end

    private

    attr_accessor :submitted_call_counter
    attr_accessor :handled_call_counter
    attr_accessor :old_handled_call_counter
    attr_accessor :call_counter
    attr_accessor :enable_debug

  end


  class TypeTracker
    include Singleton

    GEM_PATH_REVERSED_REGEX = /((?:[0-9A-Za-z]+\.)+\d+)-([A-Za-z0-9_-]+)/

    def initialize
      @cache = Set.new
      @method_cache = Hash.new
      @class_cache = Hash.new

      @socket = TCPSocket.new('127.0.0.1', 7777)
      @mutex = Mutex.new
      @prefix = ENV["ARG_SCANNER_PREFIX"]
      @enable_debug = ENV["ARG_SCANNER_DEBUG"]
      @performance_monitor = if @enable_debug then TypeTrackerPerformanceMonitor.new else nil end
      TracePoint.trace(:call, :return) do |tp|
        case tp.event
          when :call
            handle_call(tp)
          when :return
            handle_return(tp)
        end
      end
    end

    attr_accessor :enable_debug
    attr_accessor :performance_monitor
    attr_accessor :cache
    attr_accessor :method_cache
    attr_accessor :class_cache
    attr_accessor :socket
    attr_accessor :mutex
    attr_accessor :prefix



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
      mutex.synchronize { socket.puts(message) }
    end

    private
    def handle_call(tp)
      #handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path)
      if prefix.nil? || tp.path.start_with?(prefix)
        performance_monitor.on_call unless performance_monitor.nil?

        method_id = tp.method_id

        defined_class = tp.defined_class
        receiver_name = defined_class.name ? defined_class : defined_class.ancestors.first
        return if !receiver_name || !receiver_name.to_s || receiver_name.to_s.length > 200

        key = "\"method_name\":\"#{method_id}\",\"receiver_name\":\"#{receiver_name}\""

        if !method_cache.has_key? key
          new_method_cached_id = method_cache.size
          method_cache[key] = new_method_cached_id
          receiver_name = defined_class.name ? defined_class : defined_class.ancestors.first

          json = "{\"id\":\"#{new_method_cached_id}\",#{key},\"param_info\":\"#{ArgScanner.get_param_info}\","
          gem_name, gem_version = TypeTracker.extract_gem_name_and_version(tp.path)
          json += '"gem_name":"' + gem_name.to_s + '","gem_version":"' + gem_version.to_s +
              '","path":"' + tp.path + '","lineno":"' + tp.lineno.to_s + '"}'

          #puts json
          put_to_socket(json)
        end

        signature = ArgScanner.handle_call(tp.lineno, method_cache[key], tp.path)
        signatures.push(signature)
      else
        signatures.push(false)
      end
    end

    def handle_return(tp)
      sigi = signatures
      performance_monitor.on_return unless performance_monitor.nil?

      unless sigi.empty?
        signature = sigi.pop
        return unless signature

        performance_monitor.on_handled_return unless performance_monitor.nil?

        return_type_name = tp.return_value.class
        json = ArgScanner.handle_return(signature, return_type_name) +
            "\"return_type_name\":\"#{return_type_name}\"}"

        if cache.add?(json)
          #puts json
          #gem_name, gem_version = TypeTracker.extract_gem_name_and_version(tp.path)
          #json += '"gem_name":"' + gem_name.to_s + '","gem_version":"' + gem_version.to_s + '"}'
          put_to_socket(json)
        end
      end
    end

  end
end
