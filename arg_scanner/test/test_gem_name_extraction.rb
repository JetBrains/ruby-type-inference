#!/usr/bin/env ruby
require File.expand_path("helper", File.dirname(__FILE__))

class TestGemNameExtraction < Test::Unit::TestCase

  private def do_test(path, gem_name, gem_version)
    assert_equal [gem_name, gem_version], ArgScanner::TypeTracker.extract_gem_name_and_version(path)
  end

  def cleanup
    ArgScanner::OPTIONS.tap do |opts|
      opts.local_version = '0'
      opts.no_local = false
      opts.project_roots = []
    end

    super
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

  def test_project_root_no_local
    ArgScanner::OPTIONS.tap do |opts|
      opts.project_roots = ['/Users/valich/work/project']
      opts.no_local = true
    end

    do_test '/Users/valich/work/project/lib/boo.rb',
            '',
            ''
  end

  def test_project_root_no_local_despite_version
    ArgScanner::OPTIONS.tap do |opts|
      opts.project_roots = ['/Users/valich/work/project']
      opts.no_local = true
      opts.local_version = '123'
    end

    do_test '/Users/valich/work/project/lib/boo.rb',
            '',
            ''
  end

  def test_project_root_local_version
    ArgScanner::OPTIONS.tap do |opts|
      opts.project_roots = ['/Users/valich/work/project']
      opts.local_version = '123'
    end

    do_test '/Users/valich/work/project/lib/boo.rb',
            'LOCAL',
            '123'
  end

  def test_two_project_roots_local_version
    ArgScanner::OPTIONS.tap do |opts|
      opts.project_roots = ['/Users/valich/work/project', '/Users/valich/work/project2']
      opts.local_version = '123'
    end

    do_test '/Users/valich/work/project/lib/boo.rb',
            'LOCAL',
            '123'
    do_test '/Users/valich/work/project2/lib/boo.rb',
            'LOCAL',
            '123'
  end
end