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
     * Absolute file path which user have chosen to save file to.
     */
    protected lateinit var absoluteFilePath: String
        private set

    protected lateinit var project: Project
        private set

    /**
     * In this method implementation you can do you job needed for file export and then file exporting itself.
     * You may want to use [absoluteFilePath] and [project] while implementing this method.
     */
    protected abstract fun backgroundProcess()

    final override fun actionPerformed(e: AnActionEvent?) {
        val project = e?.project ?: return
        val dialog = FileSaverDialogImpl(FileSaverDescriptor(
                "Export $whatToExport",
                description,
                *extensions), project)
        val fileWrapper = dialog.save(null, defaultFileName) ?: return

        this.absoluteFilePath = fileWrapper.file.absolutePath
        this.project = project

        ProgressManager.getInstance().runProcessWithProgressSynchronously(this::backgroundProcess,
                "Exporting $whatToExport", false, e.project)
    }
}
