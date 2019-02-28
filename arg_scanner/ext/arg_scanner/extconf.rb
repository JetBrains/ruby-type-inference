require "mkmf"

RbConfig::MAKEFILE_CONFIG['CC'] = ENV['CC'] if ENV['CC']

require "debase/ruby_core_source"
require "native-package-installer"

class NilClass
  def empty?; true; end
end

# Just a replacement of have_header because have_header searches not recursively :(
def real_have_header(header_name)
  if (have_header(header_name))
    return true
  end
  yes_msg = "checking for #{header_name}... yes"
  no_msg = "checking for #{header_name}... no"

  include_env = ENV["C_INCLUDE_PATH"]
  if !include_env.empty? && !Dir.glob("#{include_env}/**/#{header_name}").empty?
    puts yes_msg
    return true
  end
  if !Dir.glob("/usr/include/**/#{header_name}").empty?
    puts yes_msg
    return true
  end
  puts no_msg
  return false
end

if !real_have_header('glib.h') &&
    !NativePackageInstaller.install(:alt_linux => "glib2-devel",
                                    :debian => "libglib2.0-dev",
                                    :redhat => "glib2-devel",
                                    :arch_linux => "glib2",
                                    :homebrew => "glib",
                                    :macports => "glib2",
                                    :msys2 => "glib2")
  exit(false)
end

hdrs = proc {
  have_header("vm_core.h") and
  have_header("iseq.h") and
  have_header("version.h") and
      have_header("vm_core.h") and
      have_header("vm_insnhelper.h") and
      have_header("vm_core.h") and
      have_header("method.h")
}

# Allow use customization of compile options. For example, the
# following lines could be put in config_options to to turn off
# optimization:
#   $CFLAGS='-fPIC -fno-strict-aliasing -g3 -ggdb -O2 -fPIC'
config_file = File.join(File.dirname(__FILE__), 'config_options.rb')
load config_file if File.exist?(config_file)

if ENV['debase_debug']
  $CFLAGS+=' -Wall -Werror -g3'
end

$CFLAGS += ' `pkg-config --cflags --libs glib-2.0`'
$DLDFLAGS += ' `pkg-config --cflags --libs glib-2.0`'

dir_config("ruby")
if !Debase::RubyCoreSource.create_makefile_with_core(hdrs, "arg_scanner/arg_scanner")
  STDERR.print("Makefile creation failed\n")
  STDERR.print("*************************************************************\n\n")
  STDERR.print("  NOTE: If your headers were not found, try passing\n")
  STDERR.print("        --with-ruby-include=PATH_TO_HEADERS      \n\n")
  STDERR.print("*************************************************************\n\n")
  exit(1)
end