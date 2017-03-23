import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionModes;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.plugins.ruby.ruby.run.RubyScriptRunner;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkType;
import org.jetbrains.plugins.ruby.ruby.sdk.RubySdkUtil;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.util.Collections;
import java.util.logging.Logger;

public class CallStatCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

    private static final Logger LOGGER = Logger.getLogger("CallStatCompletionTest");

    @Override
    protected String getTestDataPath() {
        return "/home/viuginick/Soft/ruby-type-inference/ide-plugin/src/test/testData";
    }

    public void testCompletion() {

        myFixture.configureByFiles("sample_test.rb", "sample_test_to_run.rb");

        final String versionName = "2.3.0";
        final String sdkPath = "/home/viuginick/.rbenv/versions/2.3.0/bin/ruby";

        final Sdk rubySdk = RubySdkUtil.findOrCreateMockSdk(RubySdkType.getInstance(), versionName, sdkPath, Collections.singletonList(sdkPath));

        final Module module = myFixture.getModule();

        ProcessOutput output;

        String scriptPath = getTestDataPath() + "/sample_test_to_run.rb";

        try {
            output = RubyScriptRunner.runRubyScript(rubySdk, module, scriptPath, myFixture.getTestDataPath(), new ExecutionModes.SameThreadMode(30), null, null, "");
        } catch (ExecutionException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }

        SignatureServer callStatServer = SignatureServer.getInstance();

        RSignatureContract contract = null;

        while (contract == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                LOGGER.severe(e.getMessage());
                e.printStackTrace();
            }

            contract = callStatServer.getContractByMethodName("foo");
        }

        myFixture.testCompletionVariants("sample_test.rb", "test1", "test2");
    }


}