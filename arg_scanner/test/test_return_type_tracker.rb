require 'test/unit'
require 'tempfile'
require 'fileutils'
require 'json'

class ReturnTypeTrackerTest < Test::Unit::TestCase

  class << self
    #Runs only once at start
    def startup
      file = Tempfile.new("StateTracker")
      dirname = file.path
      FileUtils.rm(dirname)
      file.close
      begin
        FileUtils.makedirs(dirname)
        system("echo exit | ARG_SCANNER_DIR=\"#{dirname}\" ARG_SCANNER_ENABLE_RETURN_TYPE_TRACKER=\"1\" irb -r\"#{File.dirname(__dir__)}/lib/arg_scanner/starter.rb\"  2> /dev/null")
        files = Dir["#{dirname}/*.json"]
        @@json = JSON.parse(File.read(files[0]))
      ensure
        FileUtils.rm_rf(dirname)
      end
    end
  end

  def test_has_struct
    assert_not_nil(@@json.find {|elem| elem["def"] == "RubyLex" && elem["name"] == "get_readed" && elem["ret"] == "String"} )
  end

  private

end
