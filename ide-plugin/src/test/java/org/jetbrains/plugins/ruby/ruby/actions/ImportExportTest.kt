package org.jetbrains.plugins.ruby.ruby.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.plugins.ruby.settings.PerGemSettings
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.RVisibility
import org.jetbrains.ruby.codeInsight.types.signature.serialization.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.ClassInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureTable
import org.jetbrains.ruby.runtime.signature.server.SignatureServer
import org.junit.Test
import java.io.File

class  ImportExportTest : LightPlatformCodeInsightFixtureTestCase() {
    private var transaction: Transaction? = null

    private var tempFile: File? = null

    override fun setUp() {
        super.setUp()

        DatabaseProvider.connectToInMemoryDB()
        transaction = TransactionManager.manager.newTransaction()
        DatabaseProvider.createAllDatabases()

        val gem1 = GemInfoTable.insertAndGetId { it[name] = "test_gem"; it[version] = "1.2.3" }
        val gem2 = GemInfoTable.insertAndGetId { it[name] = "test_gem"; it[version] = "1.2.4" }
        val gem3 = GemInfoTable.insertAndGetId { it[name] = "test_gem2"; it[version] = "1.2" }

        listOf(gem1, gem2, gem3).forEach { insertSomething(it) }

        TestCase.assertEquals(3, SignatureServer.getStorage().registeredGems.size)

        tempFile = FileUtil.createTempFile("temp_contract", ".bin")
    }

    override fun tearDown() {
        try {
            DatabaseProvider.dropAllDatabases()
            tempFile?.delete()
        } finally {
            transaction?.commit()
        }
        tempFile = null
        transaction = null
        super.tearDown()
    }

    fun testExportAllAndReimport() {
        TestCase.assertNotNull(tempFile)

        val files = ExportContractsAction.exportContractsToFile(tempFile!!.absolutePath, emptyMap())
        TestCase.assertEquals(1, files.size)

        GemInfoTable.deleteAll()

        val storage = SignatureServer.getStorage()
        TestCase.assertEquals(0, storage.registeredGems.size)

        val virtualFile = VfsUtil.findFileByIoFile(files.first(), true)
        TestCase.assertNotNull(virtualFile)

        ImportContractsAction.importContractsFromFile(virtualFile!!)

        ApplicationManager.getApplication().runWriteAction { virtualFile.delete(this) }

        TestCase.assertEquals(3, storage.registeredGems.size)

        TestCase.assertEquals(SignatureTestData.simpleContract,
                storage.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met1", RVisibility.PUBLIC))
                        ?.contract?.let { contract -> StringDataOutput().let { contract.serialize(it); it.result.toString() } })
        TestCase.assertEquals(SignatureTestData.trivialContract,
                storage.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.4"), "Test::Fqn"), "met2", RVisibility.PUBLIC))
                        ?.contract?.let { contract -> StringDataOutput().let { contract.serialize(it); it.result.toString() } })
        TestCase.assertEquals(SignatureTestData.simpleContract,
                storage.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem2", "1.2"), "Test::Fqn"), "met3", RVisibility.PUBLIC))
                        ?.contract?.let { contract -> StringDataOutput().let { contract.serialize(it); it.result.toString() } })

    }

    fun testExportAllowedAndReimport() {
        TestCase.assertNotNull(tempFile)

        val files = ExportContractsAction.exportContractsToFile(tempFile!!.absolutePath,
                mapOf(GemInfo("test_gem", "1.2.4") to PerGemSettings(false)))
        TestCase.assertEquals(1, files.size)

        GemInfoTable.deleteAll()

        val storage = SignatureServer.getStorage()
        TestCase.assertEquals(0, storage.registeredGems.size)

        val virtualFile = VfsUtil.findFileByIoFile(files.first(), true)
        TestCase.assertNotNull(virtualFile)

        ImportContractsAction.importContractsFromFile(virtualFile!!)

        ApplicationManager.getApplication().runWriteAction { virtualFile.delete(this) }

        TestCase.assertEquals(2, storage.registeredGems.size)

        TestCase.assertEquals(SignatureTestData.simpleContract,
                storage.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.3"), "Test::Fqn"), "met1", RVisibility.PUBLIC))
                        ?.contract?.let { contract -> StringDataOutput().let { contract.serialize(it); it.result.toString() } })
        TestCase.assertNull(storage.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem", "1.2.4"), "Test::Fqn"), "met2", RVisibility.PUBLIC)))
        TestCase.assertEquals(SignatureTestData.simpleContract,
                storage.getSignature(MethodInfo(ClassInfo(GemInfo("test_gem2", "1.2"), "Test::Fqn"), "met3", RVisibility.PUBLIC))
                        ?.contract?.let { contract -> StringDataOutput().let { contract.serialize(it); it.result.toString() } })
    }

    companion object {
        private fun insertSomething(gem: EntityID<Int>?) {
            val clazz = ClassInfoTable.insertAndGetId { it[fqn] = "Test::Fqn"; it[gemInfo] = gem }
            val method1 = MethodInfoTable.insertAndGetId { it[name] = "met1"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = clazz }
            val method2 = MethodInfoTable.insertAndGetId { it[name] = "met2"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = clazz }
            val method3 = MethodInfoTable.insertAndGetId { it[name] = "met3"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = clazz }
            val method4 = MethodInfoTable.insertAndGetId { it[name] = "met4"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = clazz }
            val contract1 = SignatureContract(StringDataInput(SignatureTestData.simpleContract))
            val contract2 = SignatureContract(StringDataInput(SignatureTestData.trivialContract))

            val blob1 = TransactionManager.current().connection.createBlob()
            SignatureTable.insert { it[contract] = BlobSerializer.writeToBlob(contract1, blob1); it[methodInfo] = method1 }
            blob1.free()

            val blob2 = TransactionManager.current().connection.createBlob()
            SignatureTable.insert { it[contract] = BlobSerializer.writeToBlob(contract2, blob2); it[methodInfo] = method2 }
            blob2.free()

            val blob3 = TransactionManager.current().connection.createBlob()
            SignatureTable.insert { it[contract] = BlobSerializer.writeToBlob(contract1, blob3); it[methodInfo] = method3 }
            blob3.free()

            val blob4 = TransactionManager.current().connection.createBlob()
            SignatureTable.insert { it[contract] = BlobSerializer.writeToBlob(contract2, blob4); it[methodInfo] = method4 }
            blob4.free()
        }

        private object SignatureTestData {
            val simpleContract = """
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
            """.replace('\n', ' ').trim()

            val trivialContract = """
0
2
1
1 0 a
0
""".replace('\n', ' ').trim()

        }

    }
}
