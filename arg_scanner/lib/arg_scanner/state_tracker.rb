require "set"
require_relative "require_all"
require_relative "workspace"


module ArgScanner
  class StateTracker
    def initialize
      @workspace = Workspace.new
      @workspace.on_process_start
      at_exit do
        begin
          require_extra_libs
          @workspace.open_output_json("classes") { |file| print_json(file) }
        ensure
          @workspace.on_process_exit
        end
      end
    end

    private
    def require_extra_libs
      begin
        RequireAll.require_all Rails.root.join('lib')
      rescue Exception => e
      end
      begin
        Rails.application.eager_load!
      rescue Exception => e
      end
    end

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
      value.methods - value.public_methods
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
    rescue Exception => e
      nil
    end

    def module_to_json(mod)
      ret = {
        :name => mod.to_s,
        :type => mod.class.to_s,
        :singleton_class_included => mod.singleton_class.included_modules,
        :included => mod.included_modules,
        :class_methods => mod.methods(false).map {|method| method_to_json(mod.method(method))}.compact,
        :instance_methods => mod.instance_methods(false).map {|method| method_to_json(mod.instance_method(method))}.compact
      }
      ret[:superclass] = mod.superclass if mod.is_a? Class
      ret
    rescue Exception => e
      nil
    end

    def modules_to_json
      ObjectSpace.each_object(Module).map {|mod| module_to_json(mod)}
    end
  end
end
