package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.CollectStateExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration
import org.jetbrains.plugins.ruby.ruby.run.configuration.CollectExecSettings
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyAbstractCommandLineState
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyProgramRunner
import java.io.IOException

class RubyCollectStateRunner : RubyProgramRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == CollectStateExecutor.EXECUTOR_ID && profile is AbstractRubyRunConfiguration<*>
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState,
                           env: ExecutionEnvironment): RunContentDescriptor? {
        if (state is RubyAbstractCommandLineState) {
            val newConfig = state.config.clone()
            val pathToState =  tryGenerateTmpDirPath()

            CollectExecSettings.putTo(newConfig,
                    CollectExecSettings.createSettings(true,
                            false,
                            false,
                            true,
                            pathToState
                    ))
            val newState = newConfig.getState(env.executor, env)
            if (newState != null) {
                return super.doExecute(newState, env)
            }
        }

        return null
    }


    private fun tryGenerateTmpDirPath(): String? {
        try {
            val tmpDir = FileUtil.createTempDirectory("state-tracker", "")
            return tmpDir.absolutePath
        } catch (ignored: IOException) {
            return null
        }

    }

    override fun getRunnerId(): String {
        return RUBY_COLLECT_STATE_RUNNER_ID
    }

    companion object {
        private val RUBY_COLLECT_STATE_RUNNER_ID = "RubyCollectState"
    }
}