require_relative 'arg_scanner'
require_relative 'type_tracker'

# instantiating type tracker will enable calls tracing and sending the data
ArgScanner::TypeTracker.instance
