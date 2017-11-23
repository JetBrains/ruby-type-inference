package org.jetbrains.ruby.stateTracker

import junit.framework.TestCase
import org.junit.Test

class RubyClassHierarchyLoaderTest : TestCase() {

    private var classHierarchy : RubyClassHierarchy? = null

    override fun setUp() {
        val inputStream = javaClass.classLoader.getResourceAsStream("classes.json")
        val inputString = inputStream.bufferedReader().use { it.readText() }
        classHierarchy = RubyClassHierarchyLoader.fromJson(inputString)
    }

    @Test
    fun testHasBasicObject() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            assertNotNull(it.getRubyModule("BasicObject"))
        }
    }

    @Test
    fun testIncluded() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            val module = it.getRubyModule("Gem::Resolver::Molinillo::Resolver::Resolution")
            assertNotNull(module)
            assertTrue(module!!.instanceIncluded.any {it.name == "Kernel"})
            assertTrue(module.instanceIncluded.any {it.name == "Gem::Resolver::Molinillo::Delegates::ResolutionState"})
        }
    }

    @Test
    fun testIncludedAreMinimized() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            val module = it.getRubyModule("CGI")
            assertNotNull(module)
            assertTrue(module!!.classIncluded.none {it.name == "Kernel"})
            assertTrue(module.classIncluded.any {it.name == "CGI::Util"})
        }
    }

    @Test
    fun testSuperClass() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            val module = it.getRubyModule("Timeout::Error") as RubyClass
            assertTrue(module.superClass.name == "RuntimeError")
        }
    }

    @Test
    fun testHasMethod() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            val module = it.getRubyModule("Timeout::Error") as RubyClass
            val expectedLocation = Location.Impl("/Users/vkkoshelev/.rvm/rubies/ruby-2.4.1/lib/ruby/2.4.0/timeout.rb",
                    28)
            assertTrue(module.instanceMethods.any{it.name == "thread" && it.location == expectedLocation })
        }
    }

    @Test
    fun testConstants() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            val elem = it.topLevelConstants["STDIN"]
            assertNotNull(elem)
            assertTrue(elem!!.extended.isEmpty())
            assertTrue(elem.name == "STDIN")
            assertTrue(elem.type == "IO")
        }
    }

    @Test
    fun testParameters() {
        assertNotNull(classHierarchy)
        classHierarchy?.let {
            val module = it.getRubyModule("Dir::Tmpname")!!
            assertTrue(module.classMethods.any {
                it.name == "create" &&
                        it.arguments.any { it.kind == RubyMethod.ArgumentKind.KEY_REST } &&
                        it.arguments.any { it.kind == RubyMethod.ArgumentKind.OPT } &&
                        it.arguments.any { it.kind == RubyMethod.ArgumentKind.KEY } &&
                        it.arguments.any { it.kind == RubyMethod.ArgumentKind.REQ }
            })
        }
    }
}