package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.nio.file.Paths

fun dumpSignatureInfo(signatureInfo: SignatureInfo) {
    val methodInfo = signatureInfo.methodInfo
    val classInfo = methodInfo.classInfo
    val gemInfo = classInfo.gemInfo!!
    println(gemInfo.name + " " + gemInfo.version + " " + classInfo.classFQN)
    print(methodInfo.name + " ")
    methodInfo.location?.let { print( it.path + " " + it.lineno) }
    SignatureContract.getAllReturnTypes(signatureInfo.contract).forEach { print(it + ", ") }
    println()

}


fun main(args : Array<String>) {
    val outputDirPath = parseCommandLine(args)
    DatabaseProvider.connectToDB(Paths.get(outputDirPath, DB_NAME).toString())

    val map = HashMap<String, MutableList<SignatureInfo>>()

    val provider = RSignatureProviderImpl()

    try {
        for (gem in provider.registeredGems) {
            for (clazz in provider.getRegisteredClasses(gem)) {
                for (method in provider.getRegisteredMethods(clazz)) {
                    val signatureInfo = provider.getSignature(method)
                    signatureInfo?.let<SignatureInfo?, Unit> {
                        var list = map[method.name]
                        if (list == null) {
                            list = ArrayList()
                            list.add(signatureInfo)
                            map.put(method.name, list)
                        } else {
                            list.add(signatureInfo)
                        }
                    }
                }
            }
        }
        println("All loaded")

        while (true) {
            val line = readLine()
            map[line]?.let { it.forEach { dumpSignatureInfo(it)} }
        }
    } catch (e: StorageException) {
        e.printStackTrace()
    }

}
