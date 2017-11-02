package org.jetbrains.ruby.codeInsight.types.signature

import org.jetbrains.ruby.codeInsight.types.signature.serialization.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SignatureContractSerializationTest : SignatureContractTestBase() {

    private fun checkSignaturesSerialization(signatures: List<SignatureInfo.Impl>,
                                             newSignatures: List<SignatureInfo>, contractsTestData: List<String>) {
        assertTrue(signatures.size == newSignatures.size)
        for (i in 0 until newSignatures.size) {
            assertTrue(signatures[i].methodInfo == newSignatures[i].methodInfo)
            checkSerialization(signatures[i].contract, contractsTestData[i % 4])
        }
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


    private fun doTest(contract: String) {
        val normalizedInput = contract.trim().replace('\n', ' ')
        val signatureContract = SignatureContract(StringDataInput(normalizedInput))
        val serialized = StringDataOutput().let {
            signatureContract.serialize(it)
            it.result.toString()
        }

        assertEquals(normalizedInput, serialized)
    }

    fun testSimple() {
        doTest(SignatureTestData.simpleContract)
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
}

