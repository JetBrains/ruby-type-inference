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


    private void doTest(@NotNull String name, @NotNull String method_name, String... items) {

        final String scriptName = name + ".rb";
        final String runnableScriptName = name + "_to_run.rb";

        myFixture.configureByFiles(scriptName, runnableScriptName);

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

        String text = myFixture.getEditor().getDocument().getText();

        myFixture.testCompletionVariants(scriptName, items);
    }

}