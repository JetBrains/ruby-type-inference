package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import junit.framework.TestCase
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataInput
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataOutput
import org.jetbrains.ruby.codeInsight.types.signature.serialization.serialize

class SignatureContractSerializationTest : TestCase() {

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
}

object SignatureTestData {
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
            """

    val trivialContract = """
0
2
1
1 0 a
0
"""

}
