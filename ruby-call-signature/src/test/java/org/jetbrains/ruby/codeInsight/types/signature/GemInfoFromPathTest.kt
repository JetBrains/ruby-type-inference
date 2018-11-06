package org.jetbrains.ruby.codeInsight.types.signature

import junit.framework.TestCase
import org.junit.Test

class GemInfoFromPathTest : TestCase() {
    private fun doTest(path: String, gemName: String, gemVersion: String) {
        assertEquals(GemInfoOrNull(gemName, gemVersion), gemInfoFromFilePathOrNull(path))
    }

    @Test
    fun testToplevel() {
        doTest("/home/valich/foo.rb", "", "")
    }

    @Test
    fun testRubyBundled() {
        doTest("/Users/valich/.rvm/rubies/ruby-2.3.3/lib/ruby/2.3.0/mkmf.rb", "ruby", "2.3.3")
    }

    @Test
    fun testRakeRVM() {
        doTest("/Users/valich/.rvm/rubies/ruby-2.3.3/lib/ruby/gems/2.3.0/gems/rake-10.4.2/lib/rake.rb",
                "rake", "10.4.2")
    }

    @Test
    fun testGemNameWithDashes() {
        doTest("/Users/valich/.rvm/gems/ruby-2.3.3/gems/debase-ruby_core_source-0.9.9/lib/debase/ruby_core_source.rb",
                "debase-ruby_core_source", "0.9.9")
    }

    @Test
    fun testMacPreinstalledGem() {
        doTest("/System/Library/Frameworks/Ruby.framework/Versions/2.3/usr/lib/ruby/gems/2.3.0/gems/sqlite3-1.3.11/lib/sqlite3.rb",
                "sqlite3", "1.3.11")
    }

    @Test
    fun testMacSystemGem() {
        doTest("/Users/valich/.gem/ruby/2.3.0/gems/activerecord-5.0.1/lib/active_record.rb",
                "activerecord", "5.0.1")
    }
}