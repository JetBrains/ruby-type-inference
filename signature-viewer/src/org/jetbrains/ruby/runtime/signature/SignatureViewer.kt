package org.jetbrains.ruby.runtime.signature

import org.jetbrains.exposed.sql.Database
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.*


fun main(args : Array<String>) {
    Database.connect("jdbc:mysql://localhost:3306/" + "ruby_type_contracts" + "?serverTimezone=UTC&nullNamePatternMatchesAll=true&useSSL=false",
            driver = "com.mysql.cj.jdbc.Driver",
            user = System.getProperty("mysql.user.name", "rubymine"),
            password = System.getProperty("mysql.user.password", "rubymine"))


    val provider = RSignatureProviderImpl();
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
