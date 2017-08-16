package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContractData
import java.io.DataInputStream
import java.io.DataOutputStream
import java.sql.Blob
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KProperty

class BlobDeserializer {
    companion object {
        private val openBlobs: MutableCollection<Blob> = CopyOnWriteArrayList()

        init {
            EntityHook.subscribe {
                openBlobs.forEach { it.free() }
                openBlobs.clear()
            }
        }
    }

    @Volatile
    private var cachedContract: SignatureContract? = null

    operator fun getValue(signatureContractData: SignatureContractData, property: KProperty<*>): SignatureContract {
        cachedContract?.let { return it }

        val blob = signatureContractData.contractRaw
        try {
            val result = SignatureContract(DataInputStream(blob.binaryStream))
            cachedContract = result
            return result
        } finally {
            blob.free()
        }
    }

    operator fun setValue(signatureContractData: SignatureContractData, property: KProperty<*>, signatureContract: SignatureContract) {
        val blob = TransactionManager.current().connection.createBlob()
        openBlobs.add(blob)

        BlobSerializer.writeToBlob(signatureContract, blob)
        signatureContractData.contractRaw = blob
        cachedContract = signatureContract
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