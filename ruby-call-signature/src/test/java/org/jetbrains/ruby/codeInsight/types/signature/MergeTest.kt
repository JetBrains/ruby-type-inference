package org.jetbrains.ruby.codeInsight.types.signature

import junit.framework.TestCase
import org.junit.Test

class MergeTest : TestCase() {

    private fun generateRTuple(args: List<String>, returnType: String): RTuple {
        val gemInfo = GemInfo.Impl("test_gem", "1.2.3")
        val classInfo = ClassInfo.Impl(gemInfo, "TEST1::Fqn")
        val location = Location("test1test1", 11)
        val methodInfo = MethodInfo.Impl(classInfo, "met1", RVisibility.PUBLIC, location)

        val params = args.indices.map { ParameterInfo("a" + it, ParameterInfo.Type.REQ) }

        return RTuple(methodInfo, params, args, returnType)
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
        assertTrue(contract1.accept(testTuple1))
        assertFalse(contract1.accept(testTuple2))
    }
}