package org.jetbrains.ruby.codeInsight.types.signature

import junit.framework.TestCase
import org.junit.Test
import java.util.*

class MergeTest : TestCase() {

    private fun generateRSignature(args: List<String>, returnType: String): RSignature {
        val gemInfo = GemInfo.Impl("test_gem", "1.2.3")
        val classInfo = ClassInfo.Impl(gemInfo, "TEST1::Fqn")
        val location = Location("test1test1", 11)
        val methodInfo = MethodInfo.Impl(classInfo, "met1", RVisibility.PUBLIC, location)

        val params = ArrayList<ParameterInfo>()

        for (i in args.indices) {
            params.add(ParameterInfo("a" + i, ParameterInfo.Type.REQ))
        }

        return RSignature(methodInfo, params, args, returnType)
    }

    @Test
    fun testMerge() {
        val args1 = listOf("String1", "String2", "String3")
        val args2 = listOf("Int1", "String2", "String3")
        val args3 = listOf("String1", "Int2", "String3")
        val args4 = listOf("Int1", "Int2", "String3")

        val args5 = listOf("Int1", "Int2", "Int3")

        val testArgs1 = listOf("Int1", "Int2", "Int3")
        val testArgs2 = listOf("String1", "Int2", "Int3")

        val signature1 = generateRSignature(args1, "String4")
        val signature2 = generateRSignature(args2, "String4")
        val signature3 = generateRSignature(args3, "String4")
        val signature4 = generateRSignature(args4, "String4")

        val testSignature1 = generateRSignature(testArgs1, "String4")
        val testSignature2 = generateRSignature(testArgs2, "String4")

        val signature5 = generateRSignature(args5, "String4")

        val contract1 = RSignatureContract(signature1)
        contract1.addRSignature(signature2)
        contract1.addRSignature(signature3)
        contract1.addRSignature(signature4)

        contract1.minimization()

        val contract2 = RSignatureContract(signature5)

        contract1.mergeWith(contract2)
        assertTrue(contract1.accept(testSignature1))
        assertFalse(contract1.accept(testSignature2))
    }
}