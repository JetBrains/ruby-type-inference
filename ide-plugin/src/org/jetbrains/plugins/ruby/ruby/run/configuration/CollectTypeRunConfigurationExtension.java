package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemDependency;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.GemInstallUtil;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.RubyUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CollectTypeRunConfigurationExtension extends RubyRunConfigurationExtension {
    private static final String ARG_SCANNER_GEM_NAME = "arg_scanner";

    private static final String ARG_SCANNER_REQUIRE_SCRIPT = "arg_scanner/starter";

    private static final String ROOTS_ENV_KEY = "ARG_SCANNER_PROJECT_ROOTS";

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

        String includeKey = getRequireKeyForGem(module, configuration.getSdk(), ARG_SCANNER_GEM_NAME);
        if (includeKey == null) {
            return;
        }

        final Map<String, String> env = cmdLine.getEnvironment();
        final String rubyOpt = StringUtil.notNullize(env.get(RubyUtil.RUBYOPT));
        final String newRubyOpt = rubyOpt + includeKey + " -r" + ARG_SCANNER_REQUIRE_SCRIPT;

        final Module[] rubyModules = RModuleUtil.getInstance().getAllModulesWithRubySupport(configuration.getProject());
        final String localCodeRoots = StringUtil.join(rubyModules, it -> {
            final VirtualFile[] contentRoots = ModuleRootManager.getInstance(it).getContentRoots();
            return StringUtil.join(contentRoots, VirtualFile::getPath, ":");
        }, ":");
        env.put(ROOTS_ENV_KEY, localCodeRoots);

        cmdLine.withEnvironment(RubyUtil.RUBYOPT, newRubyOpt);
    }

    @Override
    protected void validateConfiguration(@NotNull AbstractRubyRunConfiguration configuration, boolean isExecution) throws Exception {
        RunConfigurationUtil.inspectSDK(configuration, isExecution);
        final Module module = configuration.getModule();
        final Sdk sdk = configuration.getSdk();
        if (module == null || sdk == null) {
            RunConfigurationUtil.throwExecutionOrRuntimeException("Cannot execute outside of module context", isExecution);
        }

        GemInfo argScannerGem = GemSearchUtil.findGem(module, sdk, ARG_SCANNER_GEM_NAME);
        if (argScannerGem == null) {
            if (isExecution) {
                final int result = Messages.showYesNoDialog(configuration.getProject(),
                        "'arg_scanner' gem is required to collect the data. Do you want to install it?",
                        "Gem Not Found", null);
                if (result == Messages.YES) {
                    final HashMap<GemDependency, String> errors = new HashMap<>();
                    GemInstallUtil.installGemsDependencies(sdk,
                            module,
                            Collections.singleton(GemDependency.create(ARG_SCANNER_GEM_NAME, ">= 0.1.9")),
                            true,
                            false,
                            errors);
                    if (errors.isEmpty()) {
                        // means success
                        return;
                    }
                }
            }

            RunConfigurationUtil.throwExecutionOrRuntimeException("Cannot find required " + ARG_SCANNER_GEM_NAME +
                    " gem in the current SDK", false);
        }
    }

    @Nullable
    private String getRequireKeyForGem(@NotNull Module module, @Nullable Sdk sdk, @NotNull String gemName) {
        final GemInfo gemInfo = GemSearchUtil.findGem(module, sdk, gemName);
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
