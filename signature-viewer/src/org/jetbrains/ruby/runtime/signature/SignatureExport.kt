package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.RmcDirectoryImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.io.File

fun main(arg : Array<String>) {
    DatabaseProvider.connect()

    val outputDir = File(parseCommandLine(arg))

    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    val rmcDirectory = RmcDirectoryImpl(outputDir)


    val provider = RSignatureProviderImpl()
    try {
        for (gem in provider.registeredGems) {
            val signatureInfos = ArrayList<SignatureInfo>()
            for (clazz in provider.getRegisteredClasses(gem)) {
                provider.getRegisteredMethods(clazz).mapNotNullTo(signatureInfos) { provider.getSignature(it) }
            }
            rmcDirectory.save(gem, signatureInfos)
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
