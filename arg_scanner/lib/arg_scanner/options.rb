require 'ostruct'

module ArgScanner
  OPTIONS = OpenStruct.new(
      :enable_type_tracker => ENV['ARG_SCANNER_ENABLE_TYPE_TRACKER'],
      :enable_state_tracker => ENV['ARG_SCANNER_ENABLE_STATE_TRACKER'],
      :enable_return_type_tracker => ENV['ARG_SCANNER_ENABLE_RETURN_TYPE_TRACKER'],
      :output_directory => ENV['ARG_SCANNER_DIR'],
      :catch_only_every_n_call => ENV['ARG_SCANNER_CATCH_ONLY_EVERY_N_CALL'] || 1,
      :project_root => ENV['ARG_SCANNER_PROJECT_ROOT'],
  )

  def OPTIONS.set_env
    ENV['ARG_SCANNER_ENABLE_TYPE_TRACKER'] = self.enable_type_tracker ? "1" : nil
    ENV['ARG_SCANNER_ENABLE_STATE_TRACKER'] = self.enable_state_tracker ? "1" : nil
    ENV['ARG_SCANNER_ENABLE_RETURN_TYPE_TRACKER'] = self.enable_return_type_tracker ? "1" : nil
    ENV['ARG_SCANNER_DIR'] = self.output_directory
    ENV['ARG_SCANNER_CATCH_ONLY_EVERY_N_CALL'] = self.catch_only_every_n_call.to_s
    ENV['ARG_SCANNER_PROJECT_ROOT'] = self.project_root
  end
end
