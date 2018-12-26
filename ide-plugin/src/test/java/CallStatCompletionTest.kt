import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.yourkit.util.FileUtil
import junit.framework.Assert
import org.jetbrains.plugins.ruby.ruby.run.RubyCommandLine
import org.jetbrains.plugins.ruby.ruby.run.RubyLocalRunner
import org.jetbrains.ruby.codeInsight.types.signature.CallInfo
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.RVisibility
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.IOException
import java.util.concurrent.TimeUnit

class CallStatCompletionTest : LightPlatformCodeInsightFixtureTestCase() {

    private var lastServer: SignatureServer? = null

    init {
        DatabaseProvider.connectToInMemoryDB()
    }

    override fun getTestDataPath(): String {
        return "src/test/testData"
    }

    override fun setUp() {
        super.setUp()
        DatabaseProvider.dropAllDatabases()
        DatabaseProvider.createAllDatabases()
    }

    override fun tearDown() {
        try {
            DatabaseProvider.dropAllDatabases()
        } finally {
            super.tearDown()
        }
    }
    
    fun testSimpleCallInfoCollection() {
        val callInfos = runAndGetCallInfos("simple_call_info_collection_test.rb",
                createMethodInfo("AClass", "foo"))
        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 1))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "Symbol"))
    }
    
    fun testSimpleCallInfosCollectionMultipleFunctions() {
        executeScript("simple_call_info_collection_test_multiple_functions_test.rb")
        waitForServer()

        val fooCallInfos = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("A", "foo"))
        val barCallInfos = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("A", "bar"))

        assertEquals(1, fooCallInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(fooCallInfos, 2))
        assertTrue(callInfosContainsUnique(fooCallInfos, listOf("String", "Class"), "String"))

        assertEquals(3, barCallInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(barCallInfos, 1))
        assertTrue(callInfosContainsUnique(barCallInfos, listOf("TrueClass"), "A"))
        assertTrue(callInfosContainsUnique(barCallInfos, listOf("FalseClass"), "FalseClass"))
        assertTrue(callInfosContainsUnique(barCallInfos, listOf("Symbol"), "A"))
    }
    
    fun testSimpleCallInfosCollectionWithMultipleArguments() {
        val callInfos = runAndGetCallInfos("simple_call_info_collection_with_multiple_arguments_test.rb",
                createMethodInfo("AClass", "foo"))

        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 2))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String", "TrueClass"), "Regexp"))
    }
    
    fun testSaveTypesBetweenLaunches() {
        var callInfos = runAndGetCallInfos("save_types_between_launches_test_part_1.rb",
                createMethodInfo("A", "foo"))
        assertEquals(2, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 1))

        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "Symbol"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("Class"), "A"))

        callInfos = runAndGetCallInfos("save_types_between_launches_test_part_2.rb",
                createMethodInfo("A", "foo"))
        assertEquals(4, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 1))

        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "Symbol"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("Class"), "A"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("TrueClass"), "FalseClass"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "Regexp"))
    }
    
    fun testForgetCallInfoWhenArgumentsNumberChanged() {
        var callInfos = runAndGetCallInfos("forget_call_info_when_arguments_number_changed_test_part_1.rb",
                createMethodInfo("A", "foo"))
        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 1))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "Symbol"))

        callInfos = runAndGetCallInfos("forget_call_info_when_arguments_number_changed_test_part_2.rb",
                createMethodInfo("A", "foo"))
        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 2))
        assertTrue(callInfosContainsUnique(callInfos, listOf("TrueClass", "FalseClass"), "FalseClass"))
    }

    fun testCallInfoOfNestedClass() {
        val callInfos = runAndGetCallInfos("call_info_of_nested_class_test.rb",
                createMethodInfo("M::A", "foo"))
        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 1))
        assertTrue(callInfosContainsUnique(callInfos, listOf("M::A"), "M::A"))
    }
    
    fun testTopLevelMethodsCallInfoCollection() {
        val callInfos = runAndGetCallInfos("top_level_methods_call_info_collection_test.rb",
                createMethodInfo("Object", "foo"))
        assertEquals(4, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 2))
        assertTrue(callInfosContainsUnique(callInfos, listOf("TrueClass", "FalseClass"), "TrueClass"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("FalseClass", "Symbol"), "Symbol"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String", "TrueClass"), "Regexp"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String", "TrueClass"), "String"))
    }
    
    fun testDuplicatesInCallInfoTable() {
        val callInfos = runAndGetCallInfos("duplicates_in_callinfo_table_test.rb",
                createMethodInfo("Object", "foo"))
        assertEquals(3, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 1))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "String"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String"), "FalseClass"))
        assertTrue(callInfosContainsUnique(callInfos, listOf("FalseClass"), "FalseClass"))
    }
    
    fun testMethodWithoutParameters() {
        val callInfos = runAndGetCallInfos("method_without_parameters_test.rb",
                createMethodInfo("Object", "foo"))
        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 0))
        assertTrue(callInfosContainsUnique(callInfos, emptyList(), "String"))
    }

    fun testAnonymousModuleMethodCall() {
        val callInfos = runAndGetCallInfos("anonymous_module_method_call_test.rb",
                createMethodInfo("A", "foo"))
        assertEquals(1, callInfos.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(callInfos, 2))
        assertTrue(callInfosContainsUnique(callInfos, listOf("String", "Symbol"), "TrueClass"))
    }

    fun testRubyExecWithBuffering() {
        executeScript("ruby_exec_test.rb", additionalArgScannerArgs = arrayOf("--buffering"))
        waitForServer()
        val foo: List<CallInfo> = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "foo"))
        val bar: List<CallInfo> = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "bar"))

        assertEquals(0, foo.size)

        assertEquals(1, bar.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(bar, 1))
        assertTrue(callInfosContainsUnique(bar, listOf("TrueClass"), "NilClass"))
    }

    fun testRubyExecWithoutBuffering() {
        executeScript("ruby_exec_test.rb")
        waitForServer()
        val foo: List<CallInfo> = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "foo"))
        val bar: List<CallInfo> = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "bar"))

        assertEquals(1, foo.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(foo, 1))
        assertTrue(callInfosContainsUnique(foo, listOf("String"), "NilClass"))

        assertEquals(1, bar.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(bar, 1))
        assertTrue(callInfosContainsUnique(bar, listOf("TrueClass"), "NilClass"))
    }

    fun testGemFunctionsCatchingWithProjectRootSpecified() {
        val runnableScriptName = "in_project_root_test/in_project_root_test.rb"
        val projectRoot: String = javaClass.classLoader.getResource(runnableScriptName).path
        executeScript(runnableScriptName, additionalArgScannerArgs = arrayOf("--project-root=$projectRoot"))
        waitForServer()

        val catch = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "catch"))
        assertEquals(1, catch.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(catch, 1))
        assertTrue(callInfosContainsUnique(catch, listOf("String"), "NilClass"))

        val catch_2 = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "catch_2"))
        assertEquals(1, catch_2.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(catch_2, 1))
        assertTrue(callInfosContainsUnique(catch_2, listOf("String"), "NilClass"))

        val dont_catch_2 = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "dont_catch_2"))
        assertEquals(0, dont_catch_2.size)

        val catch_3 = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "catch_3"))
        assertEquals(1, catch_3.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(catch_3, 1))
        assertTrue(callInfosContainsUnique(catch_3, listOf("Proc"), "NilClass"))

        val dont_catch_3 = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "dont_catch_3"))
        assertEquals(0, dont_catch_3.size)

        val foo = RSignatureProviderImpl.getRegisteredCallInfos(createMethodInfo("Object", "foo"))
        assertEquals(1, foo.size)
        assertTrue(allCallInfosHaveNumberOfUnnamedArguments(foo, 1))
        assertTrue(callInfosContainsUnique(foo, listOf("Proc"), "NilClass"))
    }

    private fun executeScript(runnableScriptName: String, additionalArgScannerArgs: Array<String> = emptyArray()) {
        val url = javaClass.classLoader.getResource(runnableScriptName)

        if (url == null) {
            val e = RuntimeException("Cannot find script: $runnableScriptName")
            LOGGER.error(e)
            throw e
        }

        val scriptPath = url.path
        val module = myFixture.module

        try {
            LOGGER.warn(getProcessOutput(RubyCommandLine(RubyLocalRunner.getRunner(module), false)
                    .withWorkDirectory("../arg_scanner")
                    .withExePath("rake")
                    .withParameters("install")
                    .createProcess()))

            lastServer = SignatureServer()

            val pipeFileName = lastServer!!.runServerAsync(true)

            assertEquals("", getProcessOutput(RubyCommandLine(RubyLocalRunner.getRunner(module), false)
                    .withExePath("arg-scanner")
                    .withParameters("--pipe-file-path=$pipeFileName", "--type-tracker",
                            *additionalArgScannerArgs, "ruby", scriptPath)
                    .createProcess()))
        } catch (e: ExecutionException) {
            LOGGER.error(e.message)
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            LOGGER.error(e.message)
            throw RuntimeException(e)
        }

    }

    private fun waitForServer() {
        try {
            Thread.sleep(100)
        } catch (ignored: InterruptedException) {
        }

        var cnt = 0
        while (lastServer!!.isProcessingRequests() && cnt < 100) {
            try {
                Thread.sleep(1000)
                cnt++
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }

        }
    }
    
    private fun runAndGetCallInfos(executableScriptName: String,
                                   methodInfo: MethodInfo): List<CallInfo> {
        executeScript(executableScriptName)
        waitForServer()
        return RSignatureProviderImpl.getRegisteredCallInfos(methodInfo)
    }

    companion object {

        private val LOGGER = Logger.getInstance("CallStatCompletionTest")

        @Throws(InterruptedException::class)
        private fun getProcessOutput(process: Process): String {
            //        final InputStream inputStream = process.getInputStream();
            val errorStream = process.errorStream
            process.waitFor(30, TimeUnit.SECONDS)
            try {
                return StringUtil.join(FileUtil.readStreamAsLines(errorStream), "\n")
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }

        private fun createMethodInfo(className: String, methodName: String): MethodInfo {
            return MethodInfo(ClassInfo(className), methodName, RVisibility.PUBLIC)
        }

        private fun callInfosContainsUnique(callInfos: List<CallInfo>,
                                            arguments: List<String>,
                                            returnType: String): Boolean {
            return callInfos.filter { callInfo ->
                callInfo.unnamedArguments.map { it.type } == arguments && callInfo.returnType == returnType
            }.count() == 1
        }

        private fun allCallInfosHaveNumberOfUnnamedArguments(callInfos: List<CallInfo>, numberOfArguments: Int): Boolean {
            return callInfos.all { it.unnamedArguments.size == numberOfArguments }
        }
    }
}

