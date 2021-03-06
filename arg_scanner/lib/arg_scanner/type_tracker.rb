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

    def initialize
      ArgScanner.init(ENV['ARG_SCANNER_PIPE_FILE_PATH'], ENV['ARG_SCANNER_BUFFERING'],
                      ENV['ARG_SCANNER_PROJECT_ROOT'], ENV['ARG_SCANNER_CATCH_ONLY_EVERY_N_CALL'])

      @enable_debug = ENV["ARG_SCANNER_DEBUG"]
      @performance_monitor = if @enable_debug then TypeTrackerPerformanceMonitor.new else nil end

      TracePoint.trace(:call, &ArgScanner.method(:handle_call))

      TracePoint.trace(:return, &ArgScanner.method(:handle_return))

      error_msg = ArgScanner.check_if_arg_scanner_ready()
      if error_msg != nil
        STDERR.puts error_msg
        Process.exit(1)
      end

      ObjectSpace.define_finalizer(self, proc { ArgScanner.destructor() })
    end

    attr_accessor :enable_debug
    attr_accessor :performance_monitor
    attr_accessor :prefix

  end
end
