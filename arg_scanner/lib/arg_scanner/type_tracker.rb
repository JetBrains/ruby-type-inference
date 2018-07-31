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

      ObjectSpace.define_finalizer(self, proc { ArgScanner.destructor() })
    end

    attr_accessor :enable_debug
    attr_accessor :performance_monitor
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

    private
    def handle_call(tp)
      # tp.defined_class.name is `null` for anonymous modules
      if (@prefix.nil? || tp.path.start_with?(@prefix)) && tp.defined_class && !tp.defined_class.singleton_class?
        @performance_monitor.on_call unless @performance_monitor.nil?
        signatures << ArgScanner.handle_call(tp.lineno, tp.method_id.id2name, tp.path)
      else
        signatures << nil
      end
    end

    def handle_return(tp)
      @performance_monitor.on_return unless @performance_monitor.nil?
      signature = signatures.pop
      if signature
        @performance_monitor.on_handled_return unless @performance_monitor.nil?
        ArgScanner.handle_return(signature, tp.defined_class.name, tp.return_value.class.name)
      end
    end

  end
end
