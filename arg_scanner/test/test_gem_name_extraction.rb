#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))

class TestGemNameExtraction < Test::Unit::TestCase

  private def do_test(path, gem_name, gem_version)
    assert_equal [gem_name, gem_version], TypeTracker.extract_gem_name_and_version(path)
  end

  def test_toplevel
    do_test '/home/valich/foo.rb', '', ''
  end

  def test_ruby_bundled
    do_test '/Users/valich/.rvm/rubies/ruby-2.3.3/lib/ruby/2.3.0/mkmf.rb',
            'ruby',
            '2.3.3'
  end

  def test_rake_rvm
    do_test '/Users/valich/.rvm/rubies/ruby-2.3.3/lib/ruby/gems/2.3.0/gems/rake-10.4.2/lib/rake.rb',
            'rake',
            '10.4.2'
  end

  def test_gem_name_with_dashes
    do_test '/Users/valich/.rvm/gems/ruby-2.3.3/gems/debase-ruby_core_source-0.9.9/lib/debase/ruby_core_source.rb',
            'debase-ruby_core_source',
            '0.9.9'
  end

  def test_mac_preinstalled_gem
    do_test '/System/Library/Frameworks/Ruby.framework/Versions/2.3/usr/lib/ruby/gems/2.3.0/gems/sqlite3-1.3.11/lib/sqlite3.rb',
            'sqlite3',
            '1.3.11'
  end

  def test_mac_system_gem
    do_test '/Users/valich/.gem/ruby/2.3.0/gems/activerecord-5.0.1/lib/active_record.rb',
            'activerecord',
            '5.0.1'
  end
end