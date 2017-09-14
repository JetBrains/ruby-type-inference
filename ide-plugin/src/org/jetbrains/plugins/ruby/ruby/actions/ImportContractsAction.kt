package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.ruby.codeInsight.types.storage.server.PacketImpl
import org.jetbrains.ruby.runtime.signature.server.SignatureServer

class ImportContractsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val fileChooserDialogImpl = FileChooserDialogImpl(
                FileChooserDescriptorFactory.createSingleFileDescriptor("bin"),
                e.project)
        val files = fileChooserDialogImpl.choose(e.project)

        if (files.isEmpty())
            return

        ProgressManager.getInstance().runProcessWithProgressSynchronously({ ->
            files.forEach { importContractsFromFile(it) }
        }, "Importing Contracts", false, e.project)
    }

    companion object {
        fun importContractsFromFile(file: VirtualFile) {
            val contents = VfsUtilCore.loadBytes(file)
            SignatureServer.getStorage().readPacket(PacketImpl(contents))
        }
    }
}