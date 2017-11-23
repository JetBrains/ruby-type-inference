require "set"
require_relative "require_all"


module ArgScanner
  class StateTracker
    def initialize
      at_exit do
        dir = ENV["ARG_SCANNER_DIR"]
        dir = "." if dir.nil? || dir == ""
        path = dir + "/" + "classes-#{Time.now.strftime('%Y-%m-%d_%H-%M-%S')}-#{Process.pid}.json"
        begin
          RequireAll.require_all Rails.root.join('lib')
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
      result = {
        :top_level_constants => parse_top_level_constants,
        :modules => modules_to_json,
        :load_path => $:
      }
      require "json"
      file.puts(JSON.dump(result))
    end

    def parse_top_level_constants
      Module.constants.select { |const| Module.const_defined?(const)}.map do |const|
        begin
          value = Module.const_get(const)
          (!value.is_a? Module) ? {
              :name => const,
              :class_name => value.class,
              :extended => get_extra_methods(value)} : nil
        rescue Exception => e
        end
      end.compact
    end

    def get_extra_methods(value)
      begin
       (value.methods - value.class.public_instance_methods).map do |method_name|
           method = value.public_method(method_name)
           method.owner
       end.uniq
      rescue Exception => e
        value.methods - value.class.instance_methods
      end

    end

    def get_constants_of_class(constants, parent, klass)
        constants.select {|const| parent.const_defined?(const)}.map do |const|
          begin
            parent.const_get(const)
          rescue Exception => e
          end
        end.select { |const| const.is_a? klass}
    rescue => e
      $stderr.puts(e)
      []
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

    def method_to_json(method)
      ret = {
          :name => method.name,
          :parameters => method.parameters
      }
      unless method.source_location.nil?
        ret[:path] = method.source_location[0]
        ret[:line] = method.source_location[1]
      end
      ret
    end

    def module_to_json(mod)
      ret = {
        :name => mod.to_s,
        :type => mod.class.to_s,
        :singleton_class_included => mod.singleton_class.included_modules,
        :included => mod.included_modules,
        :class_methods => mod.methods(false).map {|method| method_to_json(mod.method(method))},
        :instance_methods => mod.instance_methods(false).map {|method| method_to_json(mod.instance_method(method))}
      }
      ret[:superclass] = mod.superclass if mod.is_a? Class
      ret
    end

    def modules_to_json
      get_all_modules.map {|mod| module_to_json(mod)}
    end
  end
end
