
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.RubyReturnTypeData
import java.io.File

class ClassJsonDataMergingTest : LightPlatformCodeInsightFixtureTestCase()  {

    var tmpDir: File? = null

    override fun setUp() {
        super.setUp()
        tmpDir = FileUtil.createTempDirectory("tests", "", true)
        System.setProperty("idea.system.path", tmpDir!!.absolutePath)
    }

    override fun tearDown() {
        super.tearDown()
        FileUtil.delete(tmpDir!!)
    }

    fun testClassJsonDataMergingSimple() {
        val jsons = listOf(
                "[{\"def\": \"AAAA\", \"name\":\"foo\", \"ret\": \"Foo\" }]",
                "[{\"def\": \"BBBB\", \"name\":\"bar\", \"ret\": \"Bar\" }]"
        )
        RubyReturnTypeData.updateAndSaveToSystemDirectory(jsons, myModule)
        val data = RubyReturnTypeData.getInstance(myModule)
        TestCase.assertNotNull(data)
        TestCase.assertEquals(data!!.getTypeByFQNAndMethodName("AAAA", "foo"), listOf("Foo"))
        TestCase.assertEquals(data.getTypeByFQNAndMethodName("BBBB", "bar"), listOf("Bar"))
    }
}