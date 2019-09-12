package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.jetbrains.plugins.ruby.ruby.persistent.TypeInferenceDirectory
import org.jetbrains.plugins.ruby.util.runServerAsyncInIDEACompatibleMode
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths

/**
 * Short [Project] description for `rubymine-type-tracer`
 */
data class ProjectDescription(val projectName: String, val projectPath: String, val pipeFilePath: String) {
    /**
     * @param project default projects are not allowed!
     */
    constructor(project: Project, pipeFilePath: String) : this(project.name, project.basePath!!, pipeFilePath)
}

/**
 * This directory is needed for `rubymine-type-tracker` script
 *
 * In this directory we keep files named the same as currently opened projects in RubyMine.
 * Each file contains projectPath of pipe file required for arg-scanner.
 */
private val openedProjectsDir = File(System.getProperty("java.io.tmpdir")!!).resolve(".ruby-type-inference")
        .also { it.mkdirs() }

/**
 * This registered in `plugin.xml` and it's constructor called every time RubyMine starts
 */
class ProjectLifecycleListenerImpl : ProjectManagerListener {
    private val gson = Gson()
    private val log = Logger.getInstance(this.javaClass.canonicalName)

    private companion object {
        @Volatile
        private var initialized: Boolean = false
    }

    override fun projectOpened(project: Project) {
        if (!project.isDefault) {
            connectToDB(project.name)

            // This server is used for `rubymine-type-tracker` script
            startNewBackgroundInfinityServer(project)
        }
    }

    override fun projectClosed(project: Project) {
        if (!project.isDefault) {
            val projectDescription = readProjectDescription(project, deleteJsonAfterRead = true)
            File(projectDescription.pipeFilePath).delete()
        }
    }

    private fun connectToDB(projectName: String) {
        val filePath = Paths.get(
                TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toString(),
                projectName).toString() + DatabaseProvider.H2_DB_FILE_EXTENSION

        DatabaseProvider.connectToDB(filePath, isDefaultDatabase = true)
        log.info("Connected to DB: $filePath")

        DatabaseProvider.createAllDatabases()
    }

    /**
     * Starts server for `rubymine-type-tracker` script
     */
    private fun startNewBackgroundInfinityServer(project: Project): Boolean {
        if (project.isDefault) {
            return false
        }

        val server = SignatureServer()
        val pipeFilePath: String = server.runServerAsyncInIDEACompatibleMode(project)

        writeProjectDescription(ProjectDescription(project, pipeFilePath))

        server.afterExitListener = {
            startNewBackgroundInfinityServer(project)
        }
        return true
    }

    private fun writeProjectDescription(description: ProjectDescription) {
        val jsonFile: File = openedProjectsDir.resolve(description.projectName)
        PrintWriter(jsonFile).use { it.println(gson.toJson(description)) }
    }

    private fun readProjectDescription(project: Project, deleteJsonAfterRead: Boolean = false): ProjectDescription {
        val jsonFile: File = openedProjectsDir.resolve(project.name)
        val json: String = jsonFile.bufferedReader().use { it.readText() }
        val description = gson.fromJson<ProjectDescription>(json, ProjectDescription::class.java)!!
        if (deleteJsonAfterRead) {
            jsonFile.delete()
        }
        return description
    }
}
