import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionModes;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.run.RubyScriptRunner;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkType;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkUtil;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;
import org.junit.Assert;

import java.util.Collections;
import java.util.logging.Logger;

public class CallStatCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

    private static final Logger LOGGER = Logger.getLogger("CallStatCompletionTest");

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
        RSignatureContract contract = doTest("multiple_execution_test2", "foo2", "test1", "test2");

        Assert.assertEquals(contract.getLevels().size(), 2);
        Assert.assertEquals(contract.getLevels().get(1).size(), 1);
    }

    public void testRefLinks() {
        RSignatureContract contract = doTest("ref_links_test", "doo", "test1", "test2");

        Assert.assertEquals(contract.getLevels().size(), 4);
        Assert.assertEquals(contract.getLevels().get(1).size(), 3);
        Assert.assertEquals(contract.getLevels().get(2).size(), 2);
        Assert.assertEquals(contract.getLevels().get(3).size(), 2);
    }

    public void testMerge() {
        RSignatureContract contract1 = doTest("merge_test1", "doo1", "test1", "test2");
        RSignatureContract contract2 = doTest("merge_test2", "doo2", "test3", "test4");

        contract1.merge(contract2);

        Assert.assertEquals(contract1.getLevels().size(), 3);
        Assert.assertEquals(contract1.getLevels().get(1).size(), 1);
        Assert.assertEquals(contract1.getLevels().get(2).size(), 1);
        Assert.assertEquals(contract1.getLevels().get(2).get(0).getTransitionKeys().size(), 2);
    }

    private void executeScript(@NotNull String runnableScriptName) {
        final String scriptPath = PathManager.getAbsolutePath(getTestDataPath() + "/" + runnableScriptName);

        final String versionName = "2.3.0";
        final String sdkPath = "/home/viuginick/.rbenv/versions/2.3.0/bin/ruby";

        final Sdk rubySdk = RubySdkUtil.findOrCreateMockSdk(RubySdkType.getInstance(), versionName, sdkPath, Collections.singletonList(sdkPath));

        final Module module = myFixture.getModule();

        ProcessOutput output = null;

        try {
            output = RubyScriptRunner.runRubyScript(rubySdk, module, scriptPath, myFixture.getTestDataPath(), new ExecutionModes.SameThreadMode(30), null, null, "");
        } catch (ExecutionException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }

        Assert.assertNotNull(output);
    }

    private RSignatureContract doTest(@NotNull String name, @NotNull String method_name, String... items) {

        final String scriptName = name + ".rb";
        final String runnableScriptName = name + "_to_run.rb";

        myFixture.configureByFiles(scriptName, runnableScriptName);

        executeScript(runnableScriptName);

        SignatureServer callStatServer = SignatureServer.getInstance();
        RSignatureContract contract = null;

        int cnt = 0;

        while (contract == null && cnt < 10) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                LOGGER.severe(e.getMessage());
                e.printStackTrace();
            }
            RSignatureContract currContract = callStatServer.getContractByMethodName(method_name);

            if (currContract != null && !currContract.locked)
                contract = currContract;

            cnt++;
        }

        Assert.assertNotNull(contract);

        myFixture.testCompletionVariants(scriptName, items);
        return contract;
    }

}