package org.jetbrains.plugins.ruby.ruby.run.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.Sdk;
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

import java.net.URL;
import java.util.Map;

public class CollectTypeRunConfigurationExtension extends RubyRunConfigurationExtension {
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
        final URL typeTrackerScriptURL = this.getClass().getClassLoader().getResource("type_tracker.rb");
        if (typeTrackerScriptURL == null) {
            return;
        }

        final Sdk sdk = configuration.getSdk();
        if (sdk == null) {
            return;
        }

        final GemInfo gemInfo = GemSearchUtil.findGem(sdk, "sqlite3");
        if (gemInfo == null) {
            return;
        }

        final VirtualFile libFolder = gemInfo.getLibFolder();
        if (libFolder == null) {
            return;
        }

        final Map<String, String> env = cmdLine.getEnvironment();
        final String rubyOpt = StringUtil.notNullize(env.get(RubyUtil.RUBYOPT));
        final String newRubyOpt = rubyOpt + " -I" + libFolder.getPath() + " -r" + typeTrackerScriptURL.getPath();
        //final String newRubyOpt = rubyOpt + " -r" + typeTrackerScriptURL.getPath();
        cmdLine.withEnvironment(RubyUtil.RUBYOPT, newRubyOpt);
    }

    @Nullable
    @Override
    protected <P extends AbstractRubyRunConfiguration> SettingsEditor<P> createEditor(@NotNull final P configuration) {
        return null;
    }
}
