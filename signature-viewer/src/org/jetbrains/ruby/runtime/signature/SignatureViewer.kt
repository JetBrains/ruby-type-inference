package org.jetbrains.ruby.runtime.signature

import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl


fun main(args : Array<String>) {

    DatabaseProvider.connect()
    val provider = RSignatureProviderImpl()
    try {
        for (gem in provider.registeredGems) {
            println(gem.name + " " + gem.version)
            for (clazz in provider.getRegisteredClasses(gem)) {
                println("\t" + clazz.classFQN)
                for (method in provider.getRegisteredMethods(clazz)) {

                    val contract = provider.getSignature(method)?.contract
                    contract?.let {
                        val allTypes = SignatureContract.getAllReturnTypes(contract)
                        val size = allTypes.size
                        print("\t\t" + method.name + " Size: " +  size)
                        val types = allTypes.toList().sorted()
                        for (type in types) {
                            print(" " + type)
                        }
                        println()
                    }
                }
            }
        }
    } catch (e: StorageException) {
        e.printStackTrace()
    }

}
