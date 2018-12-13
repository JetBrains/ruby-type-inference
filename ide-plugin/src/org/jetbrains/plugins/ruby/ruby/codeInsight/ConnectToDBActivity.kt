package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.ruby.ruby.persistent.TypeInferenceDirectory
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import java.nio.file.Paths

class ConnectToDBActivity : StartupActivity, DumbAware {
    val log = Logger.getInstance(this.javaClass.canonicalName)

    override fun runActivity(project: Project) {
        val filePath = Paths.get(
                TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toString(),
                project.name).toString()


        DatabaseProvider.connectToDB(filePath)
        log.info("Connected to DB: $filePath")
        DatabaseProvider.createAllDatabases()
    }
}