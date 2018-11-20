package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.CollectTypeExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration
import org.jetbrains.plugins.ruby.ruby.run.configuration.CollectExecSettings
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyAbstractCommandLineState
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyProgramRunner
import org.jetbrains.plugins.ruby.settings.RubyTypeContractsSettings
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.IOException

class RubyCollectTypeRunner : RubyProgramRunner() {

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState,
                           environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state is RubyAbstractCommandLineState) {
            val (_, _, typeTrackerEnabled) = ServiceManager.getService(environment.project, RubyTypeContractsSettings::class.java)
            val newConfig = state.config.clone()
            val pathToState = tryGenerateTmpDirPath()

            CollectExecSettings.putTo(newConfig,
                    CollectExecSettings.createSettings(true,
                            typeTrackerEnabled,
                            false,
                            pathToState
                    ))
            val newState = newConfig.getState(environment.executor, environment)
            if (newState != null) {
                return super.doExecute(newState, environment)
            }
        }

        return null
    }

    private fun tryGenerateTmpDirPath(): String? = try {
        val tmpDir = FileUtil.createTempDirectory("type-tracker", "")
        tmpDir.absolutePath
    } catch (ignored: IOException) {
        null
    }

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == CollectTypeExecutor.EXECUTOR_ID && profile is AbstractRubyRunConfiguration<*>
    }

    override fun getRunnerId(): String {
        return RUBY_COLLECT_TYPE_RUNNER_ID
    }

    companion object {
        private val RUBY_COLLECT_TYPE_RUNNER_ID = "RubyCollectType"
    }
}
