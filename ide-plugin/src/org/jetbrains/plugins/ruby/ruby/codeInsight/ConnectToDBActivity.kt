package org.jetbrains.plugins.ruby.ruby.codeInsight

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.ruby.ruby.persistent.TypeInferenceDirectory
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import java.nio.file.Paths

class ConnectToDBActivity : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        DatabaseProvider.connectToDB(Paths.get(TypeInferenceDirectory.RUBY_TYPE_INFERENCE_DIRECTORY.toString(), project.name).toString())
        DatabaseProvider.createAllDatabases()
    }
}