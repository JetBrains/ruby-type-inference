package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.resetAllRubyTypeProviderAndIDEACaches
import org.jetbrains.ruby.codeInsight.types.signature.CallInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoRow
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import java.io.File

const val CHUNK_SIZE = 1500

fun Database.copyTo(destination: Database, moveProgressBar: Boolean) {
    var progressIndicator: ProgressIndicator? = null
    var count: Int? = null

    if (moveProgressBar) {
        progressIndicator = ProgressManager.getInstance().progressIndicator
        count = transaction(this) { CallInfoTable.selectAll().count() }
    }

    var offset = 0
    while (true) {
        val info: List<CallInfo> = transaction(this) {
            CallInfoRow.wrapRows(CallInfoTable.selectAll().limit(CHUNK_SIZE, offset)).map { it.copy() }
        }
        if (info.isEmpty()) {
            break
        }

        transaction(destination) {
            info.forEach { CallInfoTable.insertInfoIfNotContains(it) }
        }

        offset += CHUNK_SIZE

        if (moveProgressBar) {
            progressIndicator!!.fraction = offset.toDouble() / count!!
        }
    }
}

class ExportContractsAction : ExportFileActionBase(
        whatToExport = "Type contracts",
        generateFilename = { project: Project -> "${project.name}-type-contracts" },
        extensions = arrayOf("mv.db")
) {
    override fun backgroundProcess(absoluteFilePath: String, module: Module?, sdk: Sdk?, project: Project) {
        exportContractsToFile(absoluteFilePath, moveProgressBar = true)
    }

    companion object {
        fun exportContractsToFile(pathToExport: String, moveProgressBar: Boolean) {
            check(pathToExport.endsWith(DatabaseProvider.H2_DB_FILE_EXTENSION)) {
                "Path to export must end with .mv.db"
            }
            File(pathToExport).delete()

            val databaseToExportTo = DatabaseProvider.connectToDB(pathToExport)
            DatabaseProvider.createAllDatabases(databaseToExportTo)

            DatabaseProvider.defaultDatabase!!.copyTo(databaseToExportTo, moveProgressBar)
        }
    }
}

class ImportContractsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val files = FileChooserDialogImpl(
                FileChooserDescriptor(true, false, false, false, false, false),
                project).choose(project)

        if (files.isEmpty()) {
            return
        }

        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<Unit, Exception> {
                files.forEach { importContractsFromFile(it.path, moveProgressBar = true) }
                return@ThrowableComputable
            }, "Importing type contracts", false, project)
            resetAllRubyTypeProviderAndIDEACaches(project)
        } catch (ex: Exception) {
            Messages.showErrorDialog(ex.message, "Error while importing type contracts")
        }
    }

    companion object {
        fun importContractsFromFile(pathToImportFrom: String, moveProgressBar: Boolean) {
            check(pathToImportFrom.endsWith(DatabaseProvider.H2_DB_FILE_EXTENSION)) {
                "Path to import from must end with .mv.db"
            }

            val dbToImportFrom = DatabaseProvider.connectToDB(pathToImportFrom)

            dbToImportFrom.copyTo(DatabaseProvider.defaultDatabase!!, moveProgressBar)
        }
    }
}
