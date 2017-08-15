package org.jetbrains.ruby.codeInsight.types.signature

import junit.framework.TestCase
import org.jetbrains.ruby.codeInsight.types.storage.server.StringDataOutput
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.serialize
import org.junit.Test

class MergeTest : TestCase() {

    private fun checkSerialization(rContract: RSignatureContract, testData: String) {
        val serialized = StringDataOutput().let {
            val contract = rContract as SignatureContract
            contract.serialize(it)
            it.result.toString()
        }

        val testDataClean = testData.trim().replace('\n', ' ')

        assertEquals(serialized, testDataClean)
    }

    private fun generateRTuple(args: List<String>, returnType: String): RTuple {
        val gemInfo = GemInfo.Impl("test_gem", "1.2.3")
        val classInfo = ClassInfo.Impl(gemInfo, "TEST1::Fqn")
        val location = Location("test1test1", 11)
        val methodInfo = MethodInfo.Impl(classInfo, "met1", RVisibility.PUBLIC, location)

        val params = args.indices.map { ParameterInfo("a" + it, ParameterInfo.Type.REQ) }

        return RTuple(methodInfo, params, args, returnType)
    }

    @Test
    fun testSimpleMerge() {
        val args1 = listOf("String1", "String2", "String3")
        val args2 = listOf("Int1", "String2", "String3")
        val args3 = listOf("String1", "Int2", "String3")
        val args4 = listOf("Int1", "Int2", "String3")

        val args5 = listOf("Int1", "Int2", "Int3")

        val testArgs1 = listOf("Int1", "Int2", "Int3")
        val testArgs2 = listOf("String1", "Int2", "Int3")

        val tuple1 = generateRTuple(args1, "String4")
        val tuple2 = generateRTuple(args2, "String4")
        val tuple3 = generateRTuple(args3, "String4")
        val tuple4 = generateRTuple(args4, "String4")

        val testTuple1 = generateRTuple(testArgs1, "String4")
        val testTuple2 = generateRTuple(testArgs2, "String4")

        val tuple5 = generateRTuple(args5, "String4")

        val contract1 = RSignatureContract(tuple1)
        contract1.addRTuple(tuple2)
        contract1.addRTuple(tuple3)
        contract1.addRTuple(tuple4)

        contract1.minimize()

        val contract2 = RSignatureContract(tuple5)

        contract1.mergeWith(contract2)
        assertTrue(SignatureContract.Companion.accept(contract1, testTuple1))
        assertFalse(SignatureContract.Companion.accept(contract1, testTuple2))

        checkSerialization(contract1, MergeTestData.testSimpleMerge)
    }

    @Test
    fun testComplicatedMerge() {
        val args1 = listOf("a1", "c2", "a3", "a4")
        val args2 = listOf("a1", "b2", "a3", "a4")
        val args3 = listOf("a1", "c2", "b3", "a4")
        val args4 = listOf("a1", "b2", "b3", "a4")

        val args5 = listOf("a1", "c2", "b3", "d4")
        val args6 = listOf("a1", "b2", "b3", "d4")

        val testArgs1 = listOf("a1", "b2", "a3", "d4")
        val testArgs2 = listOf("a1", "c2", "b3", "d4")

        val tuple1 = generateRTuple(args1, "e5")
        val tuple2 = generateRTuple(args2, "e5")
        val tuple3 = generateRTuple(args3, "e5")
        val tuple4 = generateRTuple(args4, "e5")

        val testTuple1 = generateRTuple(testArgs1, "a5")
        val testTuple2 = generateRTuple(testArgs2, "a5")

        val tuple5 = generateRTuple(args5, "a5")
        val tuple6 = generateRTuple(args6, "a5")

        val contract1 = RSignatureContract(tuple1)
        contract1.addRTuple(tuple2)
        contract1.addRTuple(tuple3)
        contract1.addRTuple(tuple4)

        contract1.minimize()

        val contract2 = RSignatureContract(tuple5)
        contract2.addRTuple(tuple6)
        contract2.minimize()

        contract1.mergeWith(contract2)
        assertFalse(SignatureContract.Companion.accept(contract1, testTuple1))
        assertTrue(SignatureContract.Companion.accept(contract1, testTuple2))

        checkSerialization(contract1, MergeTestData.testComplicatedMerge)
    }

    @Test
    fun testAdd() {
        val args1 = listOf("String1", "String2", "String3")
        val args2 = listOf("String1", "Int2", "String3")

        val testArgs1 = listOf("String1", "Date2", "String3")

        val tuple1 = generateRTuple(args1, "String4")
        val tuple2 = generateRTuple(args2, "String4")

        val testTuple1 = generateRTuple(testArgs1, "String4")

        val contract1 = RSignatureContract(tuple1)
        contract1.addRTuple(tuple2)

        contract1.minimize()

        val contract2 = RSignatureContract(testTuple1)

        contract1.mergeWith(contract2)
        assertTrue(SignatureContract.Companion.accept(contract1, testTuple1))

        checkSerialization(contract1, MergeTestData.testAddResult)
    }

    object MergeTestData {
        val testAddResult = """
3
a0 0
a1 0
a2 0
5
1
1 0 String1
3
2 0 Int2
2 0 Date2
2 0 String2
1
3 0 String3
1
4 0 String4
0
            """
        val testSimpleMerge = """
3
a0 0
a1 0
a2 0
7
2
1 0 Int1
2 0 String1
2
3 0 Int2
4 0 String2
2
4 0 Int2
4 0 String2
2
5 0 Int3
5 0 String3
1
5 0 String3
1
6 0 String4
0
            """

        val testComplicatedMerge = """
4
a0 0
a1 0
a2 0
a3 0
8
1
1 0 a1
2
2 0 b2
2 0 c2
2
3 0 b3
4 0 a3
2
5 0 d4
6 0 a4
1
6 0 a4
1
7 0 a5
1
7 0 e5
0
            """
    }

}

//4
//a0 0
//a1 0
//a2 0
//a3 0
//9
//
//1
//1 0 a1
//2
//2 0 b2
//2 0 c2
//2
//3 0 b3
//4 0 a3
//2
//5 0 d4
//6 0 a4
//1
//6 0 a4
//1
//7 0 a4
//1
//7 0 e5
//0