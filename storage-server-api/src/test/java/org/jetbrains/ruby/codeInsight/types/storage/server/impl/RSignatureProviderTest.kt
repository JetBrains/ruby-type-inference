package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import junit.framework.TestCase
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.signature.RVisibility
import org.junit.Test

class RSignatureProviderTest : TestCase() {
    var transaction: Transaction? = null

    override fun setUp() {
        super.setUp()

        Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
        transaction = TransactionManager.manager.newTransaction()
        SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable)
    }

    override fun tearDown() {
        try {
            SchemaUtils.drop(GemInfoTable, ClassInfoTable, MethodInfoTable)
        } finally {
            transaction?.commit()
        }
        transaction = null
        super.tearDown()
    }

    @Test
    fun testPutGet() {
        GemInfoTable.insert { it[name] = "rails"; it[version] = "5.0.0.beta1" }
        val insertedGem = GemInfoData.all().first()
        assertEquals("rails", insertedGem.name)
        assertEquals("5.0.0.beta1", insertedGem.version)

        ClassInfoTable.insert { it[gemInfo] = insertedGem.id; it[fqn] = "ActiveRecord::Base" }
        val insertedClass = ClassInfoData.all().first()
        assertEquals("ActiveRecord::Base", insertedClass.classFQN)
        assertEquals("rails", insertedClass.gemInfo?.name)

        MethodInfoTable.insert { it[classInfo] = insertedClass.id; it[name] = "[]="; it[visibility] = RVisibility.PUBLIC }
        val insertedMethod = MethodInfoData.all().first()
        assertEquals("[]=", insertedMethod.name)
        assertEquals(RVisibility.PUBLIC, insertedMethod.visibility)
        assertEquals("ActiveRecord::Base", insertedMethod.classInfo.classFQN)
    }

    @Test
    fun testClosestGem() {
        val gems = listOf(
                GemInfo("name1", "0.1"),
                GemInfo("name1", "0.2"),
                GemInfo("name1", "0.3"),
                GemInfo("name2", "1.0")
        )
        for (gem in gems) {
            GemInfoTable.insert { it[name] = gem.name; it[version] = gem.version }
        }

        val provider = RSignatureProviderImpl()
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
    fun testRegisteredClasses() {
        val insertResult = GemInfoTable.insertAndGetId { it[name] = "test_gem"; it[version] = "0.1" }
        ClassInfoTable.insert { it[fqn] = "Test1"; it[gemInfo] = insertResult }
        ClassInfoTable.insert { it[fqn] = "Test2"; it[gemInfo] = insertResult }
        ClassInfoTable.insert { it[fqn] = "Test3" }

        val provider = RSignatureProviderImpl()
        val classes = provider.getRegisteredClasses(GemInfo("test_gem", "0.1"))
        assertEquals(2, classes.size)
        assertEquals(setOf("Test1", "Test2"), classes.map { it.classFQN }.toSet())
    }

    @Test
    fun testRegisteredMethods() {
        val class1 = ClassInfoTable.insertAndGetId { it[fqn] = "Test::Fqn" }
        val class2 = ClassInfoTable.insertAndGetId { it[fqn] = "Test2::Fqn" }
        MethodInfoTable.insert { it[name] = "met1"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class1 }
        MethodInfoTable.insert { it[name] = "met2"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class1 }
        MethodInfoTable.insert { it[name] = "met3"; it[visibility] = RVisibility.PUBLIC; it[classInfo] = class2 }

        val provider = RSignatureProviderImpl()
        val methods = provider.getRegisteredMethods(ClassInfo("Test::Fqn"))
        assertEquals(2, methods.size)
        assertEquals(setOf("met1", "met2"), methods.map { it.name }.toSet())
    }
}