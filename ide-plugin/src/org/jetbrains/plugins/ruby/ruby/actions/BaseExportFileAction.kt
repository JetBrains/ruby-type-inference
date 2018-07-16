package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/**
 * Base class representing file export action with "save to" dialog
 * @param whatToExport Will be shown in "save to" dialog
 * @param defaultFileName Default file name in "save to" dialog
 * @param extensions Array of available extensions for exported file
 * @param description Description in "save to" dialog
 */
abstract class BaseExportFileAction(
        private val whatToExport: String,
        private val defaultFileName: String,
        private val extensions: Array<String>,
        private val description: String = ""
) : DumbAwareAction() {

    /**
     * In this method implementation you can do you job needed for file export and then file exporting itself.
     * @param absoluteFilePath Absolute file path which user have chosen to save file to.
     */
    protected abstract fun backgroundProcess(project: Project, absoluteFilePath: String)

    final override fun actionPerformed(e: AnActionEvent?) {
        val project = e?.project ?: return
        val dialog = FileSaverDialogImpl(FileSaverDescriptor(
                "Export $whatToExport",
                description,
                *extensions), project)
        val fileWrapper = dialog.save(null, defaultFileName) ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                FileExporterRunnable(project, fileWrapper.file.absolutePath, this::backgroundProcess),
                "Exporting $whatToExport", false, e.project)
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
