package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.RubyUtil;

import java.util.Map;

public class CollectTypeRunConfigurationExtension extends RubyRunConfigurationExtension {
    private static final String ARG_SCANNER_GEM_NAME = "arg_scanner";

    private static final String ARG_SCANNER_REQUIRE_SCRIPT = "arg_scanner/starter";

    @NonNls
    @NotNull
    private static final String ID = "CollectTypeRunConfigurationExtension";

    @Override
    protected void readExternal(@NotNull final AbstractRubyRunConfiguration runConfiguration,
                                @NotNull final Element element) throws InvalidDataException {

    }

    @Nullable
    @Override
    protected String getEditorTitle() {
        return null;
    }

    @Override
    protected boolean isApplicableFor(@NotNull final AbstractRubyRunConfiguration configuration) {
        return true;
    }

    @Override
    protected boolean isEnabledFor(@NotNull final AbstractRubyRunConfiguration applicableConfiguration,
                                   @Nullable final RunnerSettings runnerSettings) {
        final CollectTypeExecSettings config = CollectTypeExecSettings.getFrom(applicableConfiguration);
        return config.isCollectTypeExecEnabled;
    }

    @Override
    protected void patchCommandLine(@NotNull final AbstractRubyRunConfiguration configuration,
                                    @Nullable final RunnerSettings runnerSettings,
                                    @NotNull final GeneralCommandLine cmdLine,
                                    @NotNull final String runnerId) throws ExecutionException {
        final Module module = configuration.getModule();
        if (module == null) {
            return;
        }

        String includeKey = getRequireKeyForGem(module, ARG_SCANNER_GEM_NAME);
        if (includeKey == null) {
            return;
        }

        final Map<String, String> env = cmdLine.getEnvironment();
        final String rubyOpt = StringUtil.notNullize(env.get(RubyUtil.RUBYOPT));
        final String newRubyOpt = rubyOpt + includeKey + " -r" + ARG_SCANNER_REQUIRE_SCRIPT;

        cmdLine.withEnvironment(RubyUtil.RUBYOPT, newRubyOpt);
    }

    @Override
    protected void validateConfiguration(@NotNull AbstractRubyRunConfiguration configuration, boolean isExecution) throws Exception {
        RunConfigurationUtil.inspectSDK(configuration, isExecution);
        Module module = configuration.getModule();
        if (module == null) {
            RunConfigurationUtil.throwExecutionOrRuntimeException("Cannot execute outside of module context", isExecution);
        }

        GemInfo argScannerGem = GemSearchUtil.findGem(module, ARG_SCANNER_GEM_NAME);
        if (argScannerGem == null) {
            RunConfigurationUtil.throwExecutionOrRuntimeException("Cannot find required " + ARG_SCANNER_GEM_NAME +
                    " gem in the current SDK", false);
        }
    }

    @Nullable
    private String getRequireKeyForGem(@NotNull Module module, @NotNull String gemName) {
        final GemInfo gemInfo = GemSearchUtil.findGem(module, gemName);
        if (gemInfo == null) {
            return null;
        }

        final VirtualFile libFolder = gemInfo.getLibFolder();
        if (libFolder == null) {
            return null;
        }
        return " -I" + libFolder.getPath();
    }

    @Nullable
    @Override
    protected <P extends AbstractRubyRunConfiguration> SettingsEditor<P> createEditor(@NotNull final P configuration) {
        return null;
    }
}
