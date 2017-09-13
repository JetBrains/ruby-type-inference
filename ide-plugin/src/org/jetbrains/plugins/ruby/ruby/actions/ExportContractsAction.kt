package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.File

class ExportContractsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val dialog = FileSaverDialogImpl(FileSaverDescriptor(
                "Export Type Contracts",
                "The selected file will be populated with binary contracts representation which might" +
                        "be imported later",
                "bin"), e.project)
        val fileWrapper = dialog.save(null, "ruby_contracts.bin")
                ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously({ ->
            val packets = SignatureServer.getStorage().formPackets()
            val moreThanOne = packets.size > 1

            packets.forEachIndexed { index, packet ->
                val fileName = if (moreThanOne)
                    fileWrapper.file.absolutePath.replace(".bin", ".${index + 1}.bin")
                else
                    fileWrapper.file.absolutePath

                FileUtil.writeToFile(File(fileName), packet.data)
            }
        }, "Exporting Contracts", false, e.project)
    }
}