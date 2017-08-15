package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContractData
import java.io.DataInputStream
import java.io.DataOutputStream
import java.sql.Blob
import kotlin.reflect.KProperty

object BlobDeserializer {
    operator fun getValue(signatureContractData: SignatureContractData, property: KProperty<*>): SignatureContract {
        val blob = signatureContractData.contractRaw
        try {
            return SignatureContract(DataInputStream(blob.binaryStream))
        } finally {
            blob.free()
        }
    }

    operator fun setValue(signatureContractData: SignatureContractData, property: KProperty<*>, signatureContract: SignatureContract) {
        val blob = TransactionManager.current().connection.createBlob()
        try {
            BlobSerializer.writeToBlob(signatureContract, blob)
            signatureContractData.contractRaw = blob
        } finally {
            blob.free()
        }
    }
}

object BlobSerializer {
    fun writeToBlob(signatureContract: SignatureContract, blob: Blob): Blob {
        val binaryStream = blob.setBinaryStream(1)
        signatureContract.serialize(DataOutputStream(binaryStream))
        binaryStream.close()
        return blob
    }
}