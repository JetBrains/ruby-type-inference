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
import org.jetbrains.plugins.ruby.ancestorsextractor.AncestorsExtractorBase
import org.jetbrains.plugins.ruby.ancestorsextractor.RailsConsoleRunner
import org.jetbrains.plugins.ruby.ruby.RModuleUtil

/**
 * Base class representing file export action with "save to" dialog
 * @param whatToExport Will be shown in "save to" dialog
 * @param defaultFileName Default file name in "save to" dialog
 * @param extensions Array of available extensions for exported file
 * @param description Description in "save to" dialog
 */
abstract class ExportFileActionBase(
        private val whatToExport: String,
        private val defaultFileName: String,
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
        val fileWrapper = dialog.save(null, defaultFileName) ?: return

        this.absoluteFilePath = fileWrapper.file.absolutePath
        this.project = project
        this.module = RModuleUtil.getInstance().getModule(e.dataContext)
        this.sdk = RModuleUtil.getInstance().findRubySdkForModule(module)

        ProgressManager.getInstance().runProcessWithProgressSynchronously(this::backgroundProcess,
                "Exporting $whatToExport", false, e.project)
    }

    /**
     * Absolute file path which user have chosen to save file to.
     */
    protected lateinit var absoluteFilePath: String
        private set

    protected var module: Module? = null
        private set

    protected var sdk: Sdk? = null
        private set

    protected lateinit var project: Project
        private set

    /**
     * @return [sdk] or throws [IllegalStateException] if [sdk] is `null`
     * @throws IllegalStateException if [sdk] is `null`
     */
    protected val sdkOrThrowExceptionWithMessage: Sdk
        @Throws(IllegalStateException::class)
        get() {
            return sdk ?: throw IllegalStateException("Ruby SDK is not set")
        }

    /**
     * In this method implementation you can do you job needed for file export and then file exporting itself.
     * You may want to use [absoluteFilePath], [project], [module], [sdk] or [setProgressFraction] while implementing this method.
     */
    protected abstract fun backgroundProcess()

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
