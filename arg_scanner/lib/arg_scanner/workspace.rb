module ArgScanner
  class Workspace

    def initialize
      @dir = ENV["ARG_SCANNER_DIR"] || "."
      @pid_file = @dir+"/#{Process.pid}.pid"
    end

    def on_process_start
      File.open(@pid_file, "w") {}
    end


    def open_output_json(prefix)
      path = @dir + "/#{prefix}-#{Time.now.strftime('%Y-%m-%d_%H-%M-%S')}-#{Process.pid}.json"
      path_tmp_name = path + ".temp"
      File.open(path_tmp_name, "w") { |file| yield file }
      require 'fileutils'
      FileUtils.mv(path_tmp_name, path)
    end

    def on_process_exit
      require 'fileutils'
      FileUtils.rm(@pid_file)
    end

  end
end
