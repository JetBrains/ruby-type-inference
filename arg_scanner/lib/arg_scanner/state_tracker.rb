require "set"
require "tempfile"
require "require_all"

module ArgScanner
  class StateTracker
    def initialize
      at_exit do
        dir = ENV["ARG_SCANNER_STATE_TRACKER_DIR"]
        dir = "." if dir.nil? || dir == ""
        tmp_file = Tempfile.new(["classes-", ".json"], dir )
        path = tmp_file.path
        tmp_file.close!

        begin
          require_all Rails.root.join('lib')
        rescue => e
        end
        begin
          Rails.application.eager_load!
        rescue => e
        end

        File.open(path,"w") { |file| print_json(file) }
      end
    end

    private
    def print_json(file)
      file.puts("{")
      print_load_path(file)
      file.puts(",")
      print_all_modules(file)
      file.puts("}")
    end

    def get_constants_of_class(constants, parent, klass)
      begin
        constants.select {|const| parent.const_defined?(const)}.map do |const|
          begin
            parent.const_get(const)
          rescue Exception => e
            $stderr.puts(e)
          end
        end.select { |const| const.is_a? klass}
      rescue => e
        $stderr.puts(e)
        []
      end
    end

    def get_modules(mod)
      get_constants_of_class(mod.constants, mod, Module)
    end

    def get_all_modules
      queue = Queue.new
      visited = Set.new
      get_modules(Module).each {|mod| queue.push(mod); visited.add(mod)}

      until queue.empty? do
        mod = queue.pop
        get_modules(mod).each do |child|
          unless visited.include?(child)
            queue.push(child)
            visited.add(child)
          end
        end
      end
      visited
    end

    def array_to_json(array, stream)
      stream.print("[ ")
      is_first = true
      array.each do |elem|
        stream.print(", ") unless is_first
        is_first = false
        stream.print("\"#{elem}\"")
      end
      stream.print(" ]")
    end

    def method_to_json(method, stream)
      stream.print("{")
      stream.print("\"name\": \"#{method.name}\"")
      unless method.source_location.nil?
        stream.print(", \"path\": \"#{method.source_location[0]}\"")
        stream.print(", \"line\": \"#{method.source_location[1]}\"")
      end
      stream.print("}")
    end

    def methods_to_json(methods, stream)
      stream.puts("[ ")
      is_first = true
      methods.each do |method|
        stream.puts(", ") unless is_first
        stream.print("\t\t")
        is_first = false
        method_to_json(method, stream)
      end
      stream.puts("\t\t]")
    end

    def module_to_json(mod, stream)
      stream.puts("\t{")
      stream.puts("\t\t\"name\":\"#{mod}\",")
      stream.puts("\t\t\"type\":\"#{mod.class}\",")
      stream.puts("\t\t\"superclass\": \"#{mod.superclass}\",") if mod.is_a? Class
      stream.print("\t\t\"singleton_class_included\": ")
      array_to_json(mod.singleton_class.included_modules, stream)
      stream.puts(",")
      stream.print("\t\t\"included\": ")
      array_to_json(mod.included_modules, stream)
      stream.puts(",")
      stream.puts("\t\t\"class_methods\":")
      methods_to_json(mod.methods(false).map {|method| mod.method(method)}, stream)
      stream.puts(",")
      stream.puts("\t\t\"instance_methods\":")
      methods_to_json(mod.instance_methods(false).map {|method| mod.instance_method(method)}, stream)
      stream.print("\t}")
    end

    def print_load_path(stream)
      stream.puts("\"load_path\" :")
      stream.puts(array_to_json($:, stream))
    end

    def print_all_modules(stream)
      is_first = true
      stream.puts("\"modules\": [")
      get_all_modules.each do |mod|
        begin
          stream.puts(",") unless is_first
          is_first = false
          module_to_json(mod, stream)
        rescue Exception => e
          $stderr.puts(e.backtrace)
        end
      end
      stream.puts("]")
    end
  end
end
