package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.ruby.settings.PerGemSettings
import org.jetbrains.plugins.ruby.settings.RubyTypeContractsSettings
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import java.io.File

class ExportContractsAction : ExportFileActionBase(
        whatToExport = "Type Contracts",
        defaultFileName = "ruby_contracts",
        extensions = arrayOf("bin"),
        description = "The selected file will be populated with binary contracts representation which might be imported later"
) {
    override fun backgroundProcess() {
        val perGemSettingsMap = ServiceManager.getService(RubyTypeContractsSettings::class.java).perGemSettingsMap
        exportContractsToFile(absoluteFilePath, perGemSettingsMap)
    }

    companion object {
        fun exportContractsToFile(baseFileName: String, perGemSettingsMap: Map<out GemInfo, PerGemSettings>): Collection<File> {
            val exportDescriptor = if (perGemSettingsMap.isEmpty())
                null
            else
                RSignatureStorage.ExportDescriptor(false, perGemSettingsMap.filter { !it.value.share }.keys)

            val packets = SignatureServer.getStorage().formPackets(exportDescriptor)
            val moreThanOne = packets.size > 1

            return packets.mapIndexed { index, packet ->
                val fileName = if (moreThanOne)
                    baseFileName.replace(".bin", ".${index + 1}.bin")
                else
                    baseFileName

                val file = File(fileName)
                FileUtil.writeToFile(file, packet.data)
                file
            }
        }
    }
}