package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureInfoSerialization
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

fun main(arg : Array<String>) {
    DatabaseProvider.connect()

    val outputDir = File(parseCommandLine(arg))

    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    val provider = RSignatureProviderImpl()
    try {
        for (gem in provider.registeredGems) {
            val outputFile = File(outputDir, gem.name + "-" + gem.version + ".rmc")
            FileOutputStream(outputFile).use {
                GZIPOutputStream(it).use {
                    DataOutputStream(it).use {
                        val signatureInfos = ArrayList<SignatureInfo>()

                        for (clazz in provider.getRegisteredClasses(gem)) {
                            provider.getRegisteredMethods(clazz).mapNotNullTo(signatureInfos) { provider.getSignature(it) }
                        }
                        SignatureInfoSerialization.serialize(signatureInfos, it)
                    }
                }
            }
        }
    } catch (e: StorageException) {
        e.printStackTrace()
    }

}

fun parseCommandLine(arg: Array<String>): String {
    if (arg.size != 1) {
        println("Usage: <path-to-output-dir>")
        System.exit(-1)
    }
    return arg[0]
}
