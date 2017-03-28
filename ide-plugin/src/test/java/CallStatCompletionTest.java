import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionModes;
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


    private void executeScript(@NotNull String runnableScriptName) {
        final String scriptPath = PathManager.getAbsolutePath(getTestDataPath() + "/" + runnableScriptName);

        final String versionName = "2.3.0";
        final String sdkPath = "/home/viuginick/.rbenv/versions/2.3.0/bin/ruby";

        final Sdk rubySdk = RubySdkUtil.findOrCreateMockSdk(RubySdkType.getInstance(), versionName, sdkPath, Collections.singletonList(sdkPath));

        final Module module = myFixture.getModule();

        try {
            RubyScriptRunner.runRubyScript(rubySdk, module, scriptPath, myFixture.getTestDataPath(), new ExecutionModes.SameThreadMode(30), null, null, "");
        } catch (ExecutionException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    private RSignatureContract doTest(@NotNull String name, @NotNull String method_name, String... items) {

        final String scriptName = name + ".rb";
        final String runnableScriptName = name + "_to_run.rb";

        myFixture.configureByFiles(scriptName, runnableScriptName);

        executeScript(runnableScriptName);

        SignatureServer callStatServer = SignatureServer.getInstance();
        RSignatureContract contract = null;

        int cnt = 0;

        while (contract == null && cnt < 20) {
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