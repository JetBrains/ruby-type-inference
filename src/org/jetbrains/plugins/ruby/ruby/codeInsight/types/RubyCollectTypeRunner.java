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
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyRunner;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunCommandLineState;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfiguration;

public class RubyCollectTypeRunner extends RubyRunner {
    @NotNull
    private static final String RUBY_COLLECT_TYPE_RUNNER_ID = "RubyCollectType";
    @NotNull
    private static final String RUBY_TYPE_TRACKER_PATH = "/home/user/RubymineProjects/untitled/type_tracker.rb";

    @Nullable
    @Override
    protected RunContentDescriptor doExecute(@NotNull final RunProfileState state,
                                             @NotNull final ExecutionEnvironment env) throws ExecutionException {
        final RubyRunConfiguration configuration = ((RubyRunCommandLineState) state).getConfig();
        final String scriptPath= configuration.getScriptPath();
        final String scriptArgs = configuration.getScriptArgs();

        // TODO: remove the hard coded path and get it from config file
        configuration.setScriptPath(RUBY_TYPE_TRACKER_PATH);
        configuration.setScriptArgs(scriptPath + ' ' + scriptArgs);

        final RunContentDescriptor result = super.doExecute(state, env);

        configuration.setScriptPath(scriptPath);
        configuration.setScriptArgs(scriptArgs);

        return result;
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