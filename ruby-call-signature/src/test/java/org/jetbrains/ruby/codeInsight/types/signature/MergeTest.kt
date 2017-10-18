package org.jetbrains.ruby.codeInsight.types.signature

import junit.framework.TestCase
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureInfoSerialization
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataInput
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataOutput
import org.jetbrains.ruby.codeInsight.types.signature.serialization.serialize
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class MergeTest : TestCase() {

    private fun checkSerialization(rContract: SignatureContract, testData: String) {
        val serialized = StringDataOutput().let {
            rContract.serialize(it)
            it.result.toString()
        }

        val testDataClean = testData.trim().replace('\n', ' ')

        assertEquals(serialized, testDataClean)
    }

    private fun checkSignaturesSerialization(signatures: List<SignatureInfo.Impl>,
                                             newSignatures: List<SignatureInfo>, contractsTestData: List<String>) {
        assertTrue(signatures.size == newSignatures.size)
        for (i in 0 until newSignatures.size) {
            assertTrue(signatures[i].methodInfo == newSignatures[i].methodInfo)
            checkSerialization(signatures[i].contract, contractsTestData[i % 4])
        }
    }


    private fun generateComplicatedContract() : RSignatureContract {
        val args1 = listOf("a1", "c2", "a3", "a4")
        val args2 = listOf("a1", "b2", "a3", "a4")
        val args3 = listOf("a1", "c2", "b3", "a4")
        val args4 = listOf("a1", "b2", "b3", "a4")

        val args5 = listOf("a1", "c2", "b3", "d4")
        val args6 = listOf("a1", "b2", "b3", "d4")

        val tuple1 = generateRTuple(args1, "e5")
        val tuple2 = generateRTuple(args2, "e5")
        val tuple3 = generateRTuple(args3, "e5")
        val tuple4 = generateRTuple(args4, "e5")


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

        return contract1
    }

    private fun generateSimpleContract() : RSignatureContract {
        val args1 = listOf("String1", "String2", "String3")
        val args2 = listOf("Int1", "String2", "String3")
        val args3 = listOf("String1", "Int2", "String3")
        val args4 = listOf("Int1", "Int2", "String3")

        val args5 = listOf("Int1", "Int2", "Int3")


        val tuple1 = generateRTuple(args1, "String4")
        val tuple2 = generateRTuple(args2, "String4")
        val tuple3 = generateRTuple(args3, "String4")
        val tuple4 = generateRTuple(args4, "String4")

        val tuple5 = generateRTuple(args5, "String4")

        val contract1 = RSignatureContract(tuple1)
        contract1.addRTuple(tuple2)
        contract1.addRTuple(tuple3)
        contract1.addRTuple(tuple4)

        contract1.minimize()

        val contract2 = RSignatureContract(tuple5)

        contract1.mergeWith(contract2)

        return contract1
    }


    private fun generateMultipleReturnTypeContract(): RSignatureContract {
        val args1 = listOf("a1")
        val args2 = listOf("a1")

        val args3 = listOf("a1")

        val tuple1 = generateRTuple(args1, "b2")
        val tuple2 = generateRTuple(args2, "c2")

        val tuple3 = generateRTuple(args3, "d2")

        val contract1 = RSignatureContract(tuple1)
        contract1.addRTuple(tuple2)

        contract1.minimize()

        val contract2 = RSignatureContract(tuple3)

        contract1.mergeWith(contract2)
        return contract1
    }

    private fun generateAddContract(): RSignatureContract {
        val testArgs1 = listOf("String1", "Date2", "String3")
        val testTuple1 = generateRTuple(testArgs1, "String4")
        val args1 = listOf("String1", "String2", "String3")
        val args2 = listOf("String1", "Int2", "String3")

        val tuple1 = generateRTuple(args1, "String4")
        val tuple2 = generateRTuple(args2, "String4")

        val contract1 = RSignatureContract(tuple1)
        contract1.addRTuple(tuple2)

        contract1.minimize()

        val contract2 = RSignatureContract(testTuple1)

        contract1.mergeWith(contract2)
        return contract1
    }

    private fun generateRTuple(args: List<String>, returnType: String): RTuple {
        val gemInfo = GemInfo.Impl("test_gem", "1.2.3")
        val classInfo = ClassInfo.Impl(gemInfo, "TEST1::Fqn")
        val location = Location("test1test1", 11)
        val methodInfo = MethodInfo.Impl(classInfo, "met1", RVisibility.PUBLIC, location)

        val params = args.indices.map { ParameterInfo("a" + it, ParameterInfo.Type.REQ) }

        return RTuple(methodInfo, params, args, returnType)
    }

    private fun generateSignatures(): Pair<List<String>, List<SignatureInfo.Impl>> {
        val gems = listOf(
                GemInfo("gem", "1.2.3"),
                GemInfo("anothergem", "3.4.5"),
                GemInfo("supergem", "0.99")
        )
        val classNames = listOf("A::B::C",
                "B::C::D",
                "D::E::F")

        val classes = gems.map { gem -> classNames.map { ClassInfo(gem, it) } }.flatten()
        val methodNames = listOf("foo", "bar", "baz", "foobar")
        val methods = classes.map { clazz -> methodNames.map { MethodInfo(clazz, it, RVisibility.PUBLIC) } }.flatten()
        val contracts = listOf(generateSimpleContract(), generateComplicatedContract(),
                generateMultipleReturnTypeContract(), generateAddContract())
        val contractsTestData = listOf(MergeTestData.testSimpleMerge, MergeTestData.testComplicatedMerge,
                MergeTestData.testMultipleReturnTypeMerge, MergeTestData.testAddResult)
        assertTrue(contracts.size == contractsTestData.size)

        var idx = 0
        val signatures = methods.map { SignatureInfo(it, contracts[idx++ % contracts.size]) }
        return Pair(contractsTestData, signatures)
    }

    @Test
    fun testSimpleMerge() {
        val contract = generateSimpleContract()

        val testArgs1 = listOf("Int1", "Int2", "Int3")
        val testArgs2 = listOf("String1", "Int2", "Int3")

        val testTuple1 = generateRTuple(testArgs1, "String4")
        val testTuple2 = generateRTuple(testArgs2, "String4")

        assertTrue(SignatureContract.Companion.accept(contract, testTuple1))
        assertFalse(SignatureContract.Companion.accept(contract, testTuple2))

        checkSerialization(contract, MergeTestData.testSimpleMerge)
    }

    @Test
    fun testComplicatedMerge() {
        val testArgs1 = listOf("a1", "b2", "a3", "d4")
        val testArgs2 = listOf("a1", "c2", "b3", "d4")
        val testTuple1 = generateRTuple(testArgs1, "a5")
        val testTuple2 = generateRTuple(testArgs2, "a5")

        val contract = generateComplicatedContract()

        assertFalse(SignatureContract.Companion.accept(contract, testTuple1))
        assertTrue(SignatureContract.Companion.accept(contract, testTuple2))

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
        assertTrue(SignatureContract.Companion.accept(contract, testTuple1))

        checkSerialization(contract, MergeTestData.testAddResult)
    }

    @Test
    fun testSerializationList() {
        val (contractsTestData, signatures) = generateSignatures()

        val dataOutput = StringDataOutput()
        SignatureInfoSerialization.serialize(signatures, dataOutput)
        val newSignatures = SignatureInfoSerialization.deserialize(StringDataInput(dataOutput.result.toString()))

        checkSignaturesSerialization(signatures, newSignatures, contractsTestData)
    }

    @Test
    fun testBinarySerialization() {
        val (contractsTestData, signatures) = generateSignatures()

        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use {
            DataOutputStream(outputStream).use {
                SignatureInfoSerialization.serialize(signatures, it)
            }
        }

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        GZIPInputStream(inputStream).use {
            DataInputStream(inputStream).use {
                val newSignatures = SignatureInfoSerialization.deserialize(it)
                checkSignaturesSerialization(signatures, newSignatures, contractsTestData)
            }
        }
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
        val testMultipleReturnTypeMerge = """
1
a0 0
3
1
1 0 a1
3
2 0 b2
2 0 d2
2 0 c2
0            """
    }
}
