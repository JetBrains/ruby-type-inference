require 'mkmf'

extension_name = 'arg_scanner'

def get_dir(name)
    File.expand_path(File.join(File.dirname(__FILE__), name))
end

LIBDIR     = RbConfig::CONFIG['libdir']
INCLUDEDIR = RbConfig::CONFIG['includedir']

HEADER_DIRS = [ INCLUDEDIR ]

# setup constant that is equal to that of the file path that holds that static libraries that will need to be compiled against
LIB_DIRS = [ LIBDIR ]

libs = []

# The destination
dir_config(extension_name, HEADER_DIRS, LIB_DIRS)

libs.each do |lib|
    $LOCAL_LIBS 
