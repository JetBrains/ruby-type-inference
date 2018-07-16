package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

abstract class ExportFileAction(
        private val whatToExport: String,
        private val defaultFileName: String,
        private val extensions: Array<String>,
        private val description: String = "",
        private val canProgressBeCanceled: Boolean = false
) : DumbAwareAction() {

    protected abstract fun backgroundProcess(project: Project, absoluteFilePath: String)

    override fun actionPerformed(e: AnActionEvent?) {
        val project = e?.project ?: return
        val dialog = FileSaverDialogImpl(FileSaverDescriptor(
                "Export $whatToExport",
                description,
                *extensions), project)
        val fileWrapper = dialog.save(null, defaultFileName) ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                FileExporterRunnable(project, fileWrapper.file.absolutePath, this::backgroundProcess),
                "Exporting $whatToExport", canProgressBeCanceled, e.project)
    }

    private class FileExporterRunnable(
            private val project: Project,
            private val absoluteFilePath: String,
            private val backgroundProcess: (project: Project, absoluteFilePath: String) -> Unit
    ) : Runnable {
        override fun run() {
            backgroundProcess(project, absoluteFilePath)
        }
    }
}
