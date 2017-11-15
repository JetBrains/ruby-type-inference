require 'test/unit'
require 'tempfile'
require 'fileutils'
require 'json'

class MyTest < Test::Unit::TestCase

  class << self
    #Runs only once at start
    def startup
      file = Tempfile.new("StateTracker")
      dirname = file.path
      FileUtils.rm(dirname)
      file.close
      begin
        FileUtils.makedirs(dirname)
        system("echo exit | ARG_SCANNER_STATE_TRACKER_DIR=\"#{dirname}\" ARG_SCANNER_DISABLE_TYPE_TRACKER=\"\" irb -r\"#{File.dirname(__dir__)}/lib/arg_scanner/starter.rb\"  2> /dev/null")
        files = Dir["#{dirname}/*.json"]
        @@json = JSON.parse(File.read(files[0]))
      ensure
        FileUtils.rm_rf(dirname)
      end
    end
  end

  def test_has_struct
    assert_not_nil(get_class_with_name("Struct"))
  end

  def test_symbol_is_fine
    symbol = get_class_with_name("Symbol")
    assert_not_nil(symbol)
    assert_equal(symbol["type"], "Class")
    assert_equal(symbol["superclass"], "Object")
    assert_not_nil(symbol["singleton_class_included"].find_index("Kernel"))
    assert_not_nil(symbol["included"].find_index("Comparable"))
    assert_not_nil(get_class_method(symbol, "all_symbols"))
    assert_not_nil(get_instance_method(symbol, "match"))
    parameters = get_instance_method(symbol, "match")['parameters']
    assert_not_nil(parameters)
    if RUBY_VERSION < "2.4.0"
      assert_equal(parameters[0][0], "req")
    else
      assert_equal(parameters[0][0], "rest")
    end

  end

  private

  def get_class_method(symbol, name)
    get_named_entity(symbol, "class_methods", name)
  end

  def get_instance_method(symbol, name)
    get_named_entity(symbol, "instance_methods", name)
  end

  def get_class_with_name(name)
    get_named_entity(@@json, "modules", name)
  end

  def get_named_entity(obj, index, name)
    obj[index].find {|entity| entity["name"] == name}
  end

end
