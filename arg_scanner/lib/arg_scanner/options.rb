require 'ostruct'

module ArgScanner
  OPTIONS = OpenStruct.new(
      :local_version => ENV['ARG_SCANNER_LOCAL_VERSION'] || '0',
      :no_local => ENV['ARG_SCANNER_NO_LOCAL'] ? true : false,
      :project_roots => ((ENV['ARG_SCANNER_PROJECT_ROOTS'] || "").split ':'),
      :enable_type_tracker => ENV['ARG_SCANNER_ENABLE_TYPE_TRACKER'],
      :enable_state_tracker => ENV['ARG_SCANNER_ENABLE_STATE_TRACKER'],
      :enable_return_type_tracker => ENV['ARG_SCANNER_ENABLE_RETURN_TYPE_TRACKER'],
      :output_directory => ENV['ARG_SCANNER_DIR'],
      :catch_only_every_n_call => ENV['ARG_SCANNER_CATCH_ONLY_EVERY_N_CALL'] || 1
  )

  def OPTIONS.set_env
    ENV['ARG_SCANNER_LOCAL_VERSION'] = self.local_version.to_s
    ENV['ARG_SCANNER_NO_LOCAL'] = self.no_local ? "1" : nil
    ENV['ARG_SCANNER_PROJECT_ROOTS'] = self.project_roots.join ':'
    ENV['ARG_SCANNER_ENABLE_TYPE_TRACKER'] = self.enable_type_tracker ? "1" : nil
    ENV['ARG_SCANNER_ENABLE_STATE_TRACKER'] = self.enable_state_tracker ? "1" : nil
    ENV['ARG_SCANNER_ENABLE_RETURN_TYPE_TRACKER'] = self.enable_return_type_tracker ? "1" : nil
    ENV['ARG_SCANNER_DIR'] = self.output_directory
    ENV['ARG_SCANNER_CATCH_ONLY_EVERY_N_CALL'] = self.catch_only_every_n_call.to_s
  end
end
