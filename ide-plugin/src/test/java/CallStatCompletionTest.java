import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.yourkit.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.run.RubyCommandLine;
import org.jetbrains.plugins.ruby.ruby.run.RubyLocalRunner;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContractSerializationKt;
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataOutput;
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider;
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;
import org.jetbrains.ruby.runtime.signature.server.SignatureServerKt;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class CallStatCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

    private static final Logger LOGGER = Logger.getInstance("CallStatCompletionTest");

    public CallStatCompletionTest() {
        DatabaseProvider.connectToInMemoryDB();
        SignatureServer.runServerAsyncIfNotRunYet(0, true);
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DatabaseProvider.INSTANCE.dropAllDatabases();
        DatabaseProvider.INSTANCE.createAllDatabases();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DatabaseProvider.INSTANCE.dropAllDatabases();
        } finally {
            super.tearDown();
        }
    }

// todo: test is disabled as new version of ruby plugin doesn't support this feature
//    public void testSimple() {
//        doTest("sample_test", createMethodInfo("A", "foo"), "test1", "test2");
//    }

// todo: test is disabled as new version of ruby plugin doesn't support this feature
//    public void testKW() {
//        doTest("sample_kw_test", createMethodInfo("A", "foo1"), "test1", "test2");
//    }

    public void testSimpleCallInfoCollection() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("simple_call_info_collection_test.rb",
                createMethodInfo("AClass", "foo"));
        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 1));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "Symbol"));
    }

    public void testSimpleCallInfosCollectionMultipleFunctions() throws StorageException {
        executeScript("simple_call_info_collection_test_multiple_functions_test.rb");
        waitForServer();

        List<CallInfo> fooCallInfos = new ArrayList<>(RSignatureProviderImpl.INSTANCE.getRegisteredCallInfos(
                createMethodInfo("A", "foo"), null));
        List<CallInfo> barCallInfos = new ArrayList<>(RSignatureProviderImpl.INSTANCE.getRegisteredCallInfos(
                createMethodInfo("A", "bar"), null));

        assertEquals(1, fooCallInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(fooCallInfos, 2));
        assertTrue(callInfosContainsUnique(fooCallInfos, asList("String", "Class"), "String"));

        assertEquals(3, barCallInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(barCallInfos, 1));
        assertTrue(callInfosContainsUnique(barCallInfos, singletonList("TrueClass"), "A"));
        assertTrue(callInfosContainsUnique(barCallInfos, singletonList("FalseClass"), "FalseClass"));
        assertTrue(callInfosContainsUnique(barCallInfos, singletonList("Symbol"), "A"));
    }

    public void testSimpleCallInfosCollectionWithMultipleArguments() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("simple_call_info_collection_with_multiple_arguments_test.rb",
                createMethodInfo("AClass", "foo"));

        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 2));
        assertTrue(callInfosContainsUnique(callInfos, asList("String", "TrueClass"), "Regexp"));
    }

    public void testSaveTypesBetweenLaunches() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("save_types_between_launches_test_part_1.rb",
                createMethodInfo("A", "foo"));
        assertEquals(2, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 1));

        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "Symbol"));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("Class"), "A"));

        callInfos = runAndGetCallInfos("save_types_between_launches_test_part_2.rb",
                createMethodInfo("A", "foo"));
        assertEquals(4, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 1));

        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "Symbol"));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("Class"), "A"));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("TrueClass"), "FalseClass"));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "Regexp"));
    }

    public void testForgetCallInfoWhenArgumentsNumberChanged() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("forget_call_info_when_arguments_number_changed_test_part_1.rb",
                createMethodInfo("A", "foo"));
        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 1));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "Symbol"));

        callInfos = runAndGetCallInfos("forget_call_info_when_arguments_number_changed_test_part_2.rb",
                createMethodInfo("A", "foo"));
        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 2));
        assertTrue(callInfosContainsUnique(callInfos, asList("TrueClass", "FalseClass"), "FalseClass"));
    }

    public void testCallInfoOfNestedClass() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("call_info_of_nested_class_test.rb",
                createMethodInfo("M::A", "foo"));
        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 1));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("M::A"), "M::A"));
    }

    public void testTopLevelMethodsCallInfoCollection() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("top_level_methods_call_info_collection_test.rb",
                createMethodInfo("Object", "foo"));
        assertEquals(4, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 2));
        assertTrue(callInfosContainsUnique(callInfos, asList("TrueClass", "FalseClass"), "TrueClass"));
        assertTrue(callInfosContainsUnique(callInfos, asList("FalseClass", "Symbol"), "Symbol"));
        assertTrue(callInfosContainsUnique(callInfos, asList("String", "TrueClass"), "Regexp"));
        assertTrue(callInfosContainsUnique(callInfos, asList("String", "TrueClass"), "String"));
    }

    public void testDuplicatesInCallInfoTable() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("duplicates_in_callinfo_table_test.rb",
                createMethodInfo("Object", "foo"));
        assertEquals(3, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 1));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "String"));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("String"), "FalseClass"));
        assertTrue(callInfosContainsUnique(callInfos, singletonList("FalseClass"), "FalseClass"));
    }

    public void testMethodWithoutParameters() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("method_without_parameters_test.rb",
                createMethodInfo("Object", "foo"));
        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 0));
        assertTrue(callInfosContainsUnique(callInfos, emptyList(), "String"));
    }

    public void testAnonymousModuleMethodCall() throws StorageException {
        List<CallInfo> callInfos = runAndGetCallInfos("anonymous_module_method_call_test.rb",
                createMethodInfo("A", "foo"));
        assertEquals(1, callInfos.size());
        assertTrue(allCallInfosHaveNumberOfArguments(callInfos, 2));
        assertTrue(callInfosContainsUnique(callInfos, asList("String", "Symbol"), "TrueClass"));
    }

    public void testMultipleExecution() {
        executeScript("multiple_execution_test1.rb");
        SignatureContract contract = doTestContract("multiple_execution_test2",
                createMethodInfo("A", "foo2"));


        assertEquals(3, contract.getNodeCount());
        Map<ContractTransition, SignatureNode> edges = contract.getStartNode().getTransitions();
        assertEquals(4, edges.size());

        assertEquals(1, edges.values().stream().distinct().count());
        Map<ContractTransition, SignatureNode> nextEdges = edges.values().iterator().next().getTransitions();

        assertEquals(Collections.singleton("Abacaba"), nextEdges.keySet().iterator().next().getValue(singletonList(Collections.singleton("Abacaba"))));
    }

    public void testRefLinks() {
        SignatureContract contract = doTestContract("ref_links_test",
                createMethodInfo("A", "doo"));

        final StringDataOutput stream = new StringDataOutput();
        SignatureContractSerializationKt.serialize(contract, stream);
        assertTrue(
                stream.getResult().toString().equals("3 a 0 b 0 c 0 9 3 1 0 Fixnum 2 0 B 3 0 String 1 4 0 String 1 4 0 A 1 5 1 1 1 6 1 1 1 7 1 3 1 8 1 5 1 8 1 7 0")
                || stream.getResult().toString().equals("3 a 0 b 0 c 0 9 3 1 0 Integer 2 0 B 3 0 String 1 4 0 String 1 4 0 A 1 5 1 1 1 6 1 1 1 7 1 3 1 8 1 5 1 8 1 7 0")
        );
    }

    private void executeScript(@NotNull String runnableScriptName) {
        URL url = getClass().getClassLoader().getResource(runnableScriptName);

        if (url == null) {
            RuntimeException e = new RuntimeException("Cannot find script: " + runnableScriptName);
            LOGGER.error(e);
            throw e;
        }

        final String scriptPath = url.getPath();
        final Module module = myFixture.getModule();

        try {
            LOGGER.warn(getProcessOutput(new RubyCommandLine(RubyLocalRunner.getRunner(module), false)
                    .withWorkDirectory("../arg_scanner")
                    .withExePath("rake")
                    .withParameters("install")
                    .createProcess()));

            LOGGER.warn(getProcessOutput(new RubyCommandLine(RubyLocalRunner.getRunner(module), false)
                    .withExePath("arg-scanner")
                    .withParameters("--server-port=" + SignatureServer.getPortNumber(),
                            "--type-tracker", "ruby", scriptPath)
                    .createProcess()));
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static String getProcessOutput(@NotNull Process process) throws InterruptedException {
//        final InputStream inputStream = process.getInputStream();
        final InputStream errorStream = process.getErrorStream();
        process.waitFor(30, TimeUnit.SECONDS);
        try {
            return StringUtil.join(FileUtil.readStreamAsLines(errorStream), "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitForServer() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        int cnt = 0;
        while (SignatureServer.INSTANCE.isProcessingRequests() && cnt < 100) {
            try {
                Thread.sleep(1000);
                cnt++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private SignatureContract run(@NotNull String name, @NotNull MethodInfo methodInfo) {
        final String scriptName = name + ".rb";
        final String runnableScriptName = name + "_to_run.rb";

        myFixture.configureByFiles(scriptName, runnableScriptName);

        executeScript(runnableScriptName);

        waitForServer();

        return SignatureServer.INSTANCE.getContract(methodInfo);
    }

    @NotNull
    private List<CallInfo> runAndGetCallInfos(@NotNull String executableScriptName,
                                              @NotNull MethodInfo methodInfo) throws StorageException {
        executeScript(executableScriptName);
        waitForServer();
        return new ArrayList<>(RSignatureProviderImpl.INSTANCE.getRegisteredCallInfos(methodInfo, null));
    }

    private void doTest(@NotNull String name, @NotNull MethodInfo methodInfo, String... items) {

        final String scriptName = name + ".rb";
        Assert.assertNotNull(run(name, methodInfo));

        myFixture.testCompletionVariants(scriptName, items);
    }

    private SignatureContract doTestContract(@NotNull String name, @NotNull MethodInfo methodInfo) {
        SignatureContract contract = run(name, methodInfo);
        Assert.assertNotNull(contract);

        return contract;
    }

    private static MethodInfo createMethodInfo(@NotNull String className, @NotNull String methodName) {
        return MethodInfoKt.MethodInfo(ClassInfoKt.ClassInfo(className), methodName, RVisibility.PUBLIC);
    }

    private static boolean callInfosContainsUnique(final @NotNull List<CallInfo> callInfos,
                                                   final @NotNull List<String> arguments,
                                                   final @NotNull String returnType) {
        return callInfos.stream().filter(callInfo -> callInfo.getArgumentsTypes().equals(arguments) &&
                callInfo.getReturnType().equals(returnType)).count() == 1;
    }

    private static boolean allCallInfosHaveNumberOfArguments(final @NotNull List<CallInfo> callInfos, int numberOfArguments) {
        return callInfos.stream().allMatch(callInfo -> callInfo.getNumberOfArguments() == numberOfArguments);
    }
}