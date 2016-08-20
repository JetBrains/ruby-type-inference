package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.CollectTypeExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyAbstractCommandLineState;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyRunner;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfiguration;

import java.net.URL;

public class RubyCollectTypeRunner extends RubyRunner {
    @NotNull
    private static final String RUBY_COLLECT_TYPE_RUNNER_ID = "RubyCollectType";

    @Nullable
    @Override
    protected RunContentDescriptor doExecute(@NotNull final RunProfileState state,
                                             @NotNull final ExecutionEnvironment env) throws ExecutionException {
        if (state instanceof RubyAbstractCommandLineState) {
            final AbstractRubyRunConfiguration config = ((RubyAbstractCommandLineState) state).getConfig();
            if (config instanceof RubyRunConfiguration) {
                final RubyRunConfiguration newConfig = (RubyRunConfiguration) config.clone();
                newConfig.setScriptArgs(newConfig.getScriptPath() + ' ' + newConfig.getScriptArgs());
                final URL typeTrackerScriptURL = this.getClass().getClassLoader().getResource("type_tracker.rb");
                if (typeTrackerScriptURL != null) {
                    newConfig.setScriptPath(typeTrackerScriptURL.getPath());
                    final RunProfileState newState = newConfig.getState(env.getExecutor(), env);
                    if (newState != null) {
                        return super.doExecute(newState, env);
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
        return executorId.equals(CollectTypeExecutor.EXECUTOR_ID) && profile instanceof AbstractRubyRunConfiguration;
    }

    @NotNull
    @Override
    public String getRunnerId() {
        return RUBY_COLLECT_TYPE_RUNNER_ID;
    }
}