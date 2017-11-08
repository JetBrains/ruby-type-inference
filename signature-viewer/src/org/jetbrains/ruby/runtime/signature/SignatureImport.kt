package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.serialization.RmcDirectoryImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.io.File

fun main(arg : Array<String>) {
    DatabaseProvider.connect()

    val inputDirectory = File(parseCommandLine(arg))

    if (!inputDirectory.exists()) {
        inputDirectory.mkdirs()
    }
    val rmcDirectory = RmcDirectoryImpl(inputDirectory)

    val provider = RSignatureProviderImpl()
    try {
        rmcDirectory.listGems()
                .map { rmcDirectory.load(it) }
                .forEach { signatureInfos -> signatureInfos.forEach { provider.putSignature(it) } }
    } catch (e: StorageException) {
        e.printStackTrace()
    }

}

