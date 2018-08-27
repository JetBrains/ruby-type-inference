# starter.rb is loaded with "ruby -r" option from bin/arg-scanner
# or by IDEA also with "ruby -r" option

unless ENV["ARG_SCANNER_ENABLE_STATE_TRACKER"].nil?
  require_relative 'state_tracker'
  ArgScanner::StateTracker.new
end

unless ENV["ARG_SCANNER_ENABLE_TYPE_TRACKER"].nil?
  require_relative 'arg_scanner'
  require_relative 'type_tracker'

  # instantiating type tracker will enable calls tracing and sending the data
  ArgScanner::TypeTracker.instance
end



