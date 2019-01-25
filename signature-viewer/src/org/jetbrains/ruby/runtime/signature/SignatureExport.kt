package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.RmcDirectoryImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.io.File
import java.nio.file.Paths

const val DB_NAME = "ruby-type-inference-db" + DatabaseProvider.H2_DB_FILE_EXTENSION

fun main(arg : Array<String>) {
    val outputDirPath = parseCommandLine(arg)
    DatabaseProvider.connectToDB(Paths.get(outputDirPath, DB_NAME).toString())

    val outputDir = File(outputDirPath)

    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    val rmcDirectory = RmcDirectoryImpl(outputDir)


    try {
        for (gem in RSignatureProviderImpl.registeredGems) {
            val signatureInfos = ArrayList<SignatureInfo>()
            for (clazz in RSignatureProviderImpl.getRegisteredClasses(gem)) {
                RSignatureProviderImpl.getRegisteredMethods(clazz).mapNotNullTo(signatureInfos) { RSignatureProviderImpl.getSignature(it) }
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
