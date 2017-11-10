unless ENV["ARG_SCANNER_STATE_TRACKER_DIR"].nil?
  require_relative 'state_tracker'
  ArgScanner::StateTracker.new
end

if ENV["ARG_SCANNER_DISABLE_TYPE_TRACKER"].nil?
  require_relative 'arg_scanner'
  require_relative 'type_tracker'

  # instantiating type tracker will enable calls tracing and sending the data
  ArgScanner::TypeTracker.instance
end

