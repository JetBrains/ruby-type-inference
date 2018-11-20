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
      pipe_file_path = ENV['ARG_SCANNER_PIPE_FILE_PATH']
      ArgScanner.set_pipe_file_path(pipe_file_path)

      @catch_only_every_n_call = ENV['ARG_SCANNER_CATCH_ONLY_EVERY_N_CALL']
      @catch_only_every_n_call = @catch_only_every_n_call ? @catch_only_every_n_call.to_i : 1

      @project_root = ENV['ARG_SCANNER_PROJECT_ROOT']
      @method_ids_cache = Set.new
      @enable_debug = ENV["ARG_SCANNER_DEBUG"]
      @performance_monitor = if @enable_debug then TypeTrackerPerformanceMonitor.new else nil end
      TracePoint.trace(:call, :return) do |tp|
        if @project_root != nil && !tp.path.start_with?(@project_root)
          next
        end

        case tp.event
          when :call
            handle_call(tp)
          when :return
            handle_return(tp)
        end
      end

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

    def signatures
      Thread.current[:signatures] ||= Array.new
    end

    private
    def handle_call(tp)
      if @catch_only_every_n_call == 1 || @method_ids_cache.add?(tp.method_id) || rand(@catch_only_every_n_call) == 0
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
        defined_class = tp.defined_class.name
        # if defined_class is nil then it means that method is invoked from anonymous module.
        # Then trying to extract name of it's anonymous module. For more details see
        # CallStatCompletionTest#testAnonymousModuleMethodCall
        if defined_class == nil
          this = tp.binding.eval('self')
          defined_class = this.to_s
        end
        ArgScanner.handle_return(signature, defined_class, tp.return_value.class.to_s)
      end
    end

  end
end
