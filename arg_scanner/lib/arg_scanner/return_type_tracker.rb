require 'set'

module ArgScanner
  class ReturnTypeTracker
    def initialize
      @result = Set.new
      TracePoint.new(:return) do |tp|
        @result.add( {
          def: tp.defined_class,
          name: tp.method_id,
          ret: tp.return_value.class
        })
      end.enable
      at_exit do
        dir = ENV["ARG_SCANNER_DIR"]
        dir = "." if dir.nil? || dir == ""
        path = dir + "/calls-#{Time.now.strftime('%Y-%m-%d_%H-%M-%S')}-#{Process.pid}.json"
        require 'json'
        File.open(path,"w") { |file| file.puts(JSON.dump(@result.to_a)) }
      end
    end
  end
end


