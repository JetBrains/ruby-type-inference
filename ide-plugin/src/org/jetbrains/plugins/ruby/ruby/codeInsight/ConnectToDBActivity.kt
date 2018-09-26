package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import java.lang.IllegalStateException
import java.nio.file.Paths

private const val DB_DIRECTORY = ".ruby-type-inference"

class ConnectToDBActivity : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        DatabaseProvider.connectToDB(Paths.get(
                project.basePath ?: throw IllegalStateException("ruby type collection cannot be run for default project"),
                DB_DIRECTORY,
                "main").toString())
        DatabaseProvider.createAllDatabases()
    }
}