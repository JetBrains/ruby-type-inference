package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.serialization.RmcDirectoryImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.io.File
import java.nio.file.Paths

fun main(arg : Array<String>) {
    val outputDirPath = parseCommandLine(arg)
    DatabaseProvider.connectToDB(Paths.get(outputDirPath, DB_NAME).toString())

    val inputDirectory = File(outputDirPath)

    if (!inputDirectory.exists()) {
        inputDirectory.mkdirs()
    }
    val rmcDirectory = RmcDirectoryImpl(inputDirectory)

    try {
        rmcDirectory.listGems()
                .map { rmcDirectory.load(it) }
                .forEach { signatureInfos -> signatureInfos.forEach { RSignatureProviderImpl.putSignature(it) } }
    } catch (e: StorageException) {
        e.printStackTrace()
    }

}

