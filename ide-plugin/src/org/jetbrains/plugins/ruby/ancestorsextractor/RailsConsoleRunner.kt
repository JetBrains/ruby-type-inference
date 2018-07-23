package org.jetbrains.plugins.ruby.ancestorsextractor

import com.google.gson.Gson
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionModes
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.ruby.rails.Rails3Constants
import org.jetbrains.plugins.ruby.rails.Rails4Constants
import org.jetbrains.plugins.ruby.ruby.run.context.RubyScriptExecutionContext
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Paths

/**
 * Runs some Ruby code on Ruby on Rails console ("bin/rails console")
 */
class RailsConsoleRunner(
        /**
         * Set [Listener]. There is only one possible [Listener]. Feel free to change it to
         * addListener to have multiple listeners if you want
         */
        private var listener: RailsConsoleRunner.Listener?) {

    data class RailsConsoleExecutionResult(val stdout: String, val stderr: String)

    /**
     * Extract information left by [rubyCode] in [tempJSONFilPath]
     * @param clazz which kind of information [rubyCode] left in [tempJSONFilPath].
     * Note: Do not use [List] here because [Gson] won't parse it, use [Array] instead
     * @param tempJSONFilPath path to temp file where [rubyCode] left information which
     * can be converted from JSON to [clazz]
     * @param rubyCode Your ruby code which should leave some JSON information in [tempJSONFilPath]
     * @param eagerLoad Works like you set `eager_load` variable inside config/environments/LOADED_ENVIRONMENT.rb
     * @param rubyConsoleArguments additional arguments to pass to ruby interpreter
     * @throws ExecutionException when error occurred either while executing [rubyCode] either
     * while trying to read data from JSON left by Ruby
     * @throws IllegalStateException when getter [Project.getBasePath] of [project] returns `null`
     */
    @Throws(ExecutionException::class, IllegalStateException::class)
    fun <T> extractFromRubyOnRailsConsole(project: Project, sdk: Sdk, clazz: Class<T>, tempJSONFilPath: String,
                                          rubyCode: String, eagerLoad: Boolean,
                                          rubyConsoleArguments: Array<String> = arrayOf()): T {
        val projectDirPath = project.basePath ?: throw IllegalStateException("Seems that project is default. " +
                "Quote from com.intellij.openapi.project.Project#getBasePath JavaDoc")

        val rubyCodeToExec = if (eagerLoad) {
            """
                Rails.application.eager_load!; nil # nil is to prevent irb to print big output

            """.trimIndent() + rubyCode
        } else {
            rubyCode
        }

        val railsConsoleExecutionResult = runRailsConsole(projectDirPath, sdk,
                rubyCodeToExec, rubyConsoleArguments, railsConsoleArguments = arrayOf("--environment=development"))

        val ret = ReadAction.compute(ThrowableComputable<T?, Exception> {
            val file = File(tempJSONFilPath)
            return@ThrowableComputable try {
                file.inputStream().bufferedReader().use {
                    Gson().fromJson(it.readLine(), clazz)
                }
            } catch (ex: IOException) {
                null
            }
        })

        listener?.informationWasExtractedFromIRB()

        return ret ?: throw ExecutionException("""
            |Error occurred either in the following Ruby code (ruby was launched with these arguments: ${rubyConsoleArguments.contentToString()}):
            |$rubyCodeToExec

            |stdout of this Ruby code execution:
            |${railsConsoleExecutionResult.stdout}

            |stderr of this Ruby code execution:
            |${railsConsoleExecutionResult.stderr}

            |either while trying to read data from JSON left by Ruby
        """.trimMargin())
    }

    /**
     * Run [toExec] in ruby on rails console ("bin/rails console"). You can use it for example for generating
     * some temp json files to later parse them in Kotlin/Java
     * @param projectDirPath Path to project dir
     * @param toExec Newline separated [String] to execute in ruby on rails console
     * @param rubyConsoleArguments additional arguments to pass to ruby interpreter
     * @param railsConsoleArguments additional arguments to pass to "bin/rails console"
     * @throws ExecutionException when error occurred while launching rails console
     */
    @Throws(ExecutionException::class)
    fun runRailsConsole(projectDirPath: String, sdk: Sdk, toExec: String,
                        rubyConsoleArguments: Array<String> = arrayOf(),
                        railsConsoleArguments: Array<String> = arrayOf()): RailsConsoleExecutionResult {
        val executionMode = ExecutionModes.SameThreadMode(false)
        executionMode.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) { }
            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) { }
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) { }

            override fun startNotified(event: ProcessEvent) {
                PrintWriter(event.processHandler.processInput, true).use { it.println(toExec); it.println("quit") }
            }
        })

        val processOutput = RubyScriptExecutionContext(Paths.get(projectDirPath, Rails4Constants.CONSOLE4_SCRIPT).toString(), sdk)
                .withInterpreterOptions(*rubyConsoleArguments)
                .withArguments(Rails3Constants.CONSOLE, *railsConsoleArguments)
                .withExecutionMode(executionMode)
                .withWorkingDir(VirtualFileManager.getInstance().findFileByUrl(projectDirPath)).executeScript()
                ?: throw ExecutionException("Error occurred while launching rails console")

        listener?.irbConsoleExecuted()

        return RailsConsoleRunner.RailsConsoleExecutionResult(
                processOutput.stdout,
                processOutput.stderr
        )
    }

    /**
     * [Listener] of particular events in [RailsConsoleRunner].
     */
    interface Listener {
        /**
         * It would be called first
         */
        fun irbConsoleExecuted()

        /**
         * It would be called second
         */
        fun informationWasExtractedFromIRB()
    }
}