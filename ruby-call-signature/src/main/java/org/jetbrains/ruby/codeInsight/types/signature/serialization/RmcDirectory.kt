package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

interface RmcDirectory {
    fun save(gemInfo: GemInfo, signatures: List<SignatureInfo>)

    fun listGems() : List<GemInfo>

    fun load(gemInfo: GemInfo): List<SignatureInfo>

}

class RmcDirectoryImpl(private val directory: File) : RmcDirectory {
    init {
        if (!directory.exists() || !directory.isDirectory) {
            throw IOException("Existing directory excepted")
        }
    }

    override fun load(gemInfo: GemInfo): List<SignatureInfo> {
        val inputFile = File(directory, gemInfo2Filename(gemInfo))
        FileInputStream(inputFile).use {
            GZIPInputStream(it).use {
                DataInputStream(it).use {
                    return SignatureInfoSerialization.deserialize(it)
                }
            }
        }
    }

    override fun save(gemInfo: GemInfo, signatures: List<SignatureInfo>) {
        val outputFile = File(directory, gemInfo2Filename(gemInfo))
        FileOutputStream(outputFile).use {
            GZIPOutputStream(it).use {
                DataOutputStream(it).use {
                    SignatureInfoSerialization.serialize(signatures, it)
                }
            }
        }
    }

    override fun listGems(): List<GemInfo> = directory.listFiles().mapNotNull { file2GemInfo(it) }

    private fun gemInfo2Filename(gemInfo: GemInfo) = "${gemInfo.name}-${gemInfo.version}.rmc"

    private fun file2GemInfo(file: File): GemInfo? {
        if (file.extension != "rmc") {
            return null
        }
        val name = file.nameWithoutExtension.substringBeforeLast('-')
        val version = file.nameWithoutExtension.substringAfterLast('-')
        if (name == "" || version == "") {
            return null
        }
        return GemInfo(name, version)
    }
}
