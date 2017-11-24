require 'set'
require_relative "workspace"

module ArgScanner
  class ReturnTypeTracker
    def initialize
      @result = Set.new
      @workspace = Workspace.new
      @workspace.on_process_start
      @trace_point = TracePoint.new(:return) do |tp|
        @result.add( {
          def: tp.defined_class,
          name: tp.method_id,
          ret: tp.return_value.class
        })
      end
      @trace_point.enable
      at_exit do
        @trace_point.disable
        begin
          require 'json'
          @workspace.open_output_json("calls") { |file| file.puts(JSON.dump(@result.to_a)) }
        ensure
          @workspace.on_process_exit
        end
      end
    end
  end
end


