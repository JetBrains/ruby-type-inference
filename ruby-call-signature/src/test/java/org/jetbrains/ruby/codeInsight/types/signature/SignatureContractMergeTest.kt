package org.jetbrains.ruby.codeInsight.types.signature

import org.junit.Test

class SignatureContractMergeTest : SignatureContractTestBase() {

    @Test
    fun testSimpleMerge() {
        val contract = generateSimpleContract()

        val testArgs1 = listOf("Int1", "Int2", "Int3")
        val testArgs2 = listOf("String1", "Int2", "Int3")

        val testTuple1 = generateRTuple(testArgs1, "String4")
        val testTuple2 = generateRTuple(testArgs2, "String4")

        assertTrue(SignatureContract.accept(contract, testTuple1))
        assertFalse(SignatureContract.accept(contract, testTuple2))

        checkSerialization(contract, MergeTestData.testSimpleMerge)
    }

    @Test
    fun testComplicatedMerge() {
        val testArgs1 = listOf("a1", "b2", "a3", "d4")
        val testArgs2 = listOf("a1", "c2", "b3", "d4")
        val testTuple1 = generateRTuple(testArgs1, "a5")
        val testTuple2 = generateRTuple(testArgs2, "a5")

        val contract = generateComplicatedContract()

        assertFalse(SignatureContract.accept(contract, testTuple1))
        assertTrue(SignatureContract.accept(contract, testTuple2))

        checkSerialization(contract, MergeTestData.testComplicatedMerge)
    }

    @Test
    fun testMultipleReturnTypeMerge() {
        val contract = generateMultipleReturnTypeContract()
        checkSerialization(contract, MergeTestData.testMultipleReturnTypeMerge)
    }

    @Test
    fun testAdd() {
        val testArgs1 = listOf("String1", "Date2", "String3")
        val testTuple1 = generateRTuple(testArgs1, "String4")

        val contract = generateAddContract()
        assertTrue(SignatureContract.accept(contract, testTuple1))

        checkSerialization(contract, MergeTestData.testAddResult)
    }
}


