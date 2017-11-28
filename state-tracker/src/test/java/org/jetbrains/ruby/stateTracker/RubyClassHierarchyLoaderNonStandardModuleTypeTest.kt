package org.jetbrains.ruby.stateTracker

import junit.framework.TestCase
import org.junit.Test

class RubyClassHierarchyLoaderNonStandardModuleTypeTest : TestCase() {
    private var classHierarchy : RubyClassHierarchy? = null

    override fun setUp() {
        val inputStream = javaClass.classLoader.getResourceAsStream("non-standard-module-type.json")
        val inputString = inputStream.bufferedReader().use { it.readText() }
        classHierarchy = RubyClassHierarchyLoader.fromJson(inputString)
    }

    @Test
    fun testHierarchyLoaded() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            assertNotNull(it.getRubyModule("AAAA"))
            assertNotNull(it.getRubyModule("BBBB"))
        }
    }

}