package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorBase
import org.jetbrains.plugins.ruby.ancestorsextractor.RailsConsoleRunner
import org.jetbrains.plugins.ruby.ruby.RModuleUtil

/**
 * Base class representing file export action with "save to" dialog
 * @param whatToExport Will be shown in "save to" dialog
 * @param generateFilename Generate filename for "save to" dialog
 * @param extensions Array of available extensions for exported file
 * @param description Description in "save to" dialog
 */
abstract class ExportFileActionBase(
        private val whatToExport: String,
        private val generateFilename: (Project) -> String,
        private val extensions: Array<String>,
        private val description: String = "",
        private val numberOfProgressBarFractions: Int? = null
) : DumbAwareAction() {
    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = FileSaverDialogImpl(FileSaverDescriptor(
                "Export $whatToExport",
                description,
                *extensions), project)
        val fileWrapper = dialog.save(null, generateFilename(project)) ?: return

        val module: Module? = RModuleUtil.getInstance().getModule(e.dataContext)
        val sdk: Sdk? = RModuleUtil.getInstance().findRubySdkForModule(module)

        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<Unit, Exception> {
                return@ThrowableComputable backgroundProcess(fileWrapper.file.absolutePath, module, sdk, project)
            }, "", false, project)
        } catch (ex: Exception) {
            Messages.showErrorDialog(ex.message, "Error while exporting $whatToExport")
        }
    }

    /**
     * In this method implementation you can do you job needed for file export and then file exporting itself.
     *
     * @param absoluteFilePath absolute file path which user have chosen to save file to.
     * @param module module from the context of action it invoked
     * @param sdk sdk from the context of action it invoked
     * @param project project from the context of action it invoked
     */
    protected abstract fun backgroundProcess(absoluteFilePath: String, module: Module?, sdk: Sdk?, project: Project)

    @Throws(IllegalStateException::class)
    protected fun moveProgressBarForward() {
        if (numberOfProgressBarFractions == null) throw IllegalStateException("You cannot call moveProgressBarForward() " +
                "method when progressBarFractions property is null")
        val progressIndicator = ProgressManager.getInstance().progressIndicator
        if (progressIndicator is ProgressWindow) {
            progressIndicator.fraction = minOf(1.0, progressIndicator.fraction + 1.0/numberOfProgressBarFractions)
        }
    }

    /**
     * You can use to set as [AncestorsExtractorBase.listener] because every [ProgressListener]
     * method call just calls [moveProgressBarForward]
     */
    protected inner class ProgressListener : RailsConsoleRunner.Listener {
        override fun irbConsoleExecuted() {
            moveProgressBarForward()
        }

        override fun informationWasExtractedFromIRB() {
            moveProgressBarForward()
        }
    }
}
