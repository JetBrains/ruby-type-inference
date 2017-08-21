import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.yourkit.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.run.LocalRunner;
import org.jetbrains.plugins.ruby.ruby.run.RubyCommandLine;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class CallStatCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

    private static final Logger LOGGER = Logger.getInstance("CallStatCompletionTest");

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void testSimple() {
        doTest("sample_test", "foo", "test1", "test2");
    }

    public void testKW() {
        doTest("sample_kw_test", "foo1", "test1", "test2");
    }

    public void testMultipleExecution() {
        executeScript("multiple_execution_test1.rb");
        RSignatureContract contract = ((RSignatureContract) doTestContract("multiple_execution_test2", "foo2"));

        Assert.assertEquals(contract.getLevels().size(), 2);
        Assert.assertEquals(contract.getLevels().get(1).size(), 1);
    }

    public void testRefLinks() {
        RSignatureContract contract = ((RSignatureContract) doTestContract("ref_links_test", "doo"));

        Assert.assertEquals(contract.getLevels().size(), 4);
        Assert.assertEquals(contract.getLevels().get(1).size(), 3);
        Assert.assertEquals(contract.getLevels().get(2).size(), 2);
        Assert.assertEquals(contract.getLevels().get(3).size(), 2);
    }

    private void executeScript(@NotNull String runnableScriptName) {
        final String scriptPath = PathManager.getAbsolutePath(getTestDataPath() + "/" + runnableScriptName);

        final Module module = myFixture.getModule();

        try {
            LOGGER.warn(getProcessOutput(new RubyCommandLine(LocalRunner.getRunner(module), false)
                    .withWorkDirectory("../arg_scanner")
                    .withExePath("rake")
                    .withParameters("install")
                    .createProcess()));

            LOGGER.warn(getProcessOutput(new RubyCommandLine(LocalRunner.getRunner(module), false)
                    .withExePath("arg-scanner")
                    .withParameters("ruby", scriptPath)
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
            return /*StringUtil.join(FileUtil.readStreamAsLines(inputStream), "\n") + ";" +*/
                    StringUtil.join(FileUtil.readStreamAsLines(errorStream), "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SignatureContract run(@NotNull String name, @NotNull String method_name) {
        final String scriptName = name + ".rb";
        final String runnableScriptName = name + "_to_run.rb";

        myFixture.configureByFiles(scriptName, runnableScriptName);

        SignatureServer callStatServer = SignatureServer.INSTANCE;
        executeScript(runnableScriptName);

        SignatureContract contract = null;

        int cnt = 0;

        while (contract == null && cnt < 10) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage());
            }
            SignatureContract currContract = callStatServer.getContractByMethodName(method_name);

            if (currContract != null) {
                contract = currContract;
            }

            cnt++;
        }

        return contract;
    }

    private void doTest(@NotNull String name, @NotNull String method_name, String... items) {

        final String scriptName = name + ".rb";
        Assert.assertNotNull(run(name, method_name));

        myFixture.testCompletionVariants(scriptName, items);
    }

    private SignatureContract doTestContract(@NotNull String name, @NotNull String method_name) {
        return run(name, method_name);
    }

}