package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import junit.framework.TestCase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobSerializer
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataInput
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.testutil.doDBTest
import org.junit.Test

class RSignatureProviderTest : TestCase() {
    init {
        DatabaseProvider.connectToInMemoryDB(isDefaultDatabase = true)
    }

    @Test
    fun testPutGet() = doDBTest {
        GemInfoTable.insert { it[name] = "rails"; it[version] = "5.0.0.beta1" }
        val insertedGem = GemInfoRow.all().first()
        assertEquals("rails", insertedGem.name)
        assertEquals("5.0.0.beta1", insertedGem.version)

        ClassInfoTable.insert { it[gemInfo] = insertedGem.id; it[fqn] = "ActiveRecord::Base" }
        val insertedClass = ClassInfoRow.all().first()
        assertEquals("ActiveRecord::Base", insertedClass.classFQN)
        assertEquals("rails", insertedClass.gemInfo?.name)

        MethodInfoTable.insert { it[classInfo] = insertedClass.id; it[name] = "[]="; it[visibility] = RVisibility.PUBLIC }
        val insertedMethod = MethodInfoRow.all().first()
        assertEquals("[]=", insertedMethod.name)
        assertEquals(RVisibility.PUBLIC, insertedMethod.visibility)
        assertEquals("ActiveRecord::Base", insertedMethod.classInfo.classFQN)
    }

    @Test
    fun testClosestGem() = doDBTest {
        val gems = listOf(
                GemInfo("name1", "0.1"),
                GemInfo("name1", "0.2"),
                GemInfo("name1", "0.3"),
                GemInfo("name2", "1.0")
        )
        for (gem in gems) {
            GemInfoTable.insert { it[name] = gem.name; it[version] = gem.version }
        }

        val provider = RSignatureProviderImpl
        assertEquals("0.1", provider.getClosestRegisteredGem(GemInfo("name1", "0.0"))?.version)
        assertEquals("0.1", provider.getClosestRegisteredGem(GemInfo("name1", "0.1"))?.version)
        assertEquals("0.1", provider.getClosestRegisteredGem(GemInfo("name1", "0.1.2"))?.version)
        assertEquals("0.1", provider.getClosestRegisteredGem(GemInfo("name1", "0.1.9"))?.version)
        assertEquals("0.2", provider.getClosestRegisteredGem(GemInfo("name1", "0.2"))?.version)
        assertEquals("0.3", provider.getClosestRegisteredGem(GemInfo("name1", "0.4"))?.version)
        assertEquals("0.3", provider.getClosestRegisteredGem(GemInfo("name1", "1.0"))?.version)
        assertEquals("1.0", provider.getClosestRegisteredGem(GemInfo("name2", "1.0"))?.version)
        assertEquals("1.0", provider.getClosestRegisteredGem(GemInfo("name2", "0.1"))?.version)
        assertEquals("1.0", provider.getClosestRegisteredGem(GemInfo("name2", "2.0"))?.version)
        assertEquals(null, provider.getClosestRegisteredGem(GemInfo("name3", "2.0")))
    }

    @Test
    fun testRegisteredClasses() = doDBTest {
        val insertResult = GemInfoTable.insertAndGetId { it[name] = "test_gem"; it[version] = "0.1" }
        ClassInfoTable.insert { it[fqn] = "Test1"; it[gemInfo] = insertResult }
        ClassInfoTable.insert { it[fqn] = "Test2"; it[gemInfo] = insertResult }
        ClassInfoTable.insert { it[fqn] = "Test3" }

        val provider = RSignatureProviderImpl
        val classes = provider.getRegisteredClasses(GemInfo("test_gem", "0.1"))
        assertEquals(2, classes.size)
        assertEquals(setOf("Test1", "Test2"), classes.map { it.classFQN }.toSet())
    }

    @Test
    fun testRegisteredMethods() = doDBTest {
        val gem = GemInfoTable.insertAndGetId { it[name] = "test_gem"; it[version] = "1.2.3" }
        val class1 = ClassInfoTable.insertAndGetId { it[fqn] = "Test::Fqn" }
        val class2 = ClassInfoTable.insertAndGetId { it[fqn] = "Test2::Fqn" }
        val class3 = ClassInfoTable.insertAndGetId { it[fqn] = "Test::Fqn"; it[gemInfo] = gem }
        MethodInfoTable.insert { it[name] = "met1"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class1 }
        MethodInfoTable.insert { it[name] = "met2"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class1 }
        MethodInfoTable.insert { it[name] = "met3"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class2 }
        MethodInfoTable.insert { it[name] = "met4"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class3 }

        val provider = RSignatureProviderImpl
        val methodsWithNullGem = provider.getRegisteredMethods(ClassInfo("Test::Fqn"))
        assertEquals(2, methodsWithNullGem.size)
        assertEquals(setOf("met1", "met2"), methodsWithNullGem.map { it.name }.toSet())

        val methodsWithGivenGem = provider.getRegisteredMethods(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"))
        assertEquals(1, methodsWithGivenGem.size)
        assertEquals(setOf("met4"), methodsWithGivenGem.map { it.name }.toSet())
    }

    @Test
    fun testSignatures() = doDBTest {
        val gem = GemInfoTable.insertAndGetId { it[name] = "test_gem"; it[version] = "1.2.3" }
        val clazz = ClassInfoTable.insertAndGetId { it[fqn] = "Test::Fqn"; it[gemInfo] = gem }
        val method1 = MethodInfoTable.insertAndGetId {
            it[name] = "met1"
            it[visibility] = RVisibility.PUBLIC
            it[classInfo] = clazz
        }
        val method2 = MethodInfoTable.insertAndGetId {
            it[name] = "met2"
            it[visibility] = RVisibility.PUBLIC
            it[classInfo] = clazz
        }

        val contract1 = SignatureContract(StringDataInput(SignatureTestData.simpleContract))
        val contract2 = SignatureContract(StringDataInput(SignatureTestData.trivialContract))

        val blob1 = TransactionManager.current().connection.createBlob()
        SignatureTable.insert { it[contract] = BlobSerializer.writeToBlob(contract1, blob1); it[methodInfo] = method1 }
        blob1.free()

        val blob2 = TransactionManager.current().connection.createBlob()
        SignatureTable.insert { it[contract] = BlobSerializer.writeToBlob(contract2, blob2); it[methodInfo] = method2 }
        blob2.free()

        val provider = RSignatureProviderImpl
        val signatureInfo1 = provider.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met1", RVisibility.PUBLIC))
        val signatureInfo2 = provider.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met2", RVisibility.PUBLIC))

        assertNotNull(signatureInfo1)
        assertEquals(4, signatureInfo1!!.contract.nodeCount)

        assertNotNull(signatureInfo2)
        assertEquals(2, signatureInfo2!!.contract.nodeCount)
    }

    @Test
    fun testSignaturesWithAPIPut() = doDBTest {
        val gem = GemInfo("test_gem", "1.2.3")
        val clazz = ClassInfo(gem, "Test::Fqn")
        val method1 = MethodInfo(clazz,
                "met1",
                RVisibility.PUBLIC)

        val method2 = MethodInfo(clazz,
                "met2",
                RVisibility.PUBLIC)

        val contract1 = SignatureContract(StringDataInput(SignatureTestData.simpleContract))
        val contract2 = SignatureContract(StringDataInput(SignatureTestData.trivialContract))

        val provider = RSignatureProviderImpl

        provider.putSignature(SignatureInfo(method1, contract1))
        provider.putSignature(SignatureInfo(method2, contract2))

        val signatureInfo1 = provider.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met1", RVisibility.PUBLIC))
        val signatureInfo2 = provider.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met2", RVisibility.PUBLIC))

        assertNotNull(signatureInfo1)
        assertEquals(4, signatureInfo1!!.contract.nodeCount)

        assertNotNull(signatureInfo2)
        assertEquals(2, signatureInfo2!!.contract.nodeCount)
    }

    @Test
    fun testSignaturesWithSignatureReplace() = doDBTest {
        val gem = GemInfo("test_gem", "1.2.3")
        val clazz = ClassInfo(gem, "Test::Fqn")
        val method = MethodInfo(clazz,
                "met1",
                RVisibility.PUBLIC)

        val contract1 = SignatureContract(StringDataInput(SignatureTestData.simpleContract))
        val contract2 = SignatureContract(StringDataInput(SignatureTestData.trivialContract))

        val provider = RSignatureProviderImpl

        provider.putSignature(SignatureInfo(method, contract1))
        val signatureInfo1 = provider.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met1", RVisibility.PUBLIC))
        assertNotNull(signatureInfo1)
        assertEquals(4, signatureInfo1!!.contract.nodeCount)

        provider.putSignature(SignatureInfo(method, contract2))
        val signatureInfo2 = provider.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met1", RVisibility.PUBLIC))
        assertNotNull(signatureInfo2)
        assertEquals(2, signatureInfo2!!.contract.nodeCount)
    }

    object SignatureTestData {
        const val simpleContract = """
1 arg 0
4
3
1 0 a
2 0 b
2 0 c
1
3 0 d
1
3 1 0
0
            """

        const val trivialContract = """
0
2
1
1 0 a
0
"""

    }
}
