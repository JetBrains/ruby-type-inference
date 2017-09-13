package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.serialize
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContractData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class SignatureStorageImpl : RSignatureStorage<PacketImpl>, RSignatureProvider by RSignatureProviderImpl() {
    override fun formPackets(): MutableCollection<PacketImpl> {
        val contractData = transaction {
            SignatureContractData.all().toList().map { it.copy() }
        }

        return PacketImpl.createPacketsBySignatureContracts(contractData)
    }
}

class PacketImpl(val data: ByteArray) : RSignatureStorage.Packet {

    override fun getSignatures(): MutableCollection<SignatureInfo> {
        val inputStream = ByteArrayInputStream(data)
        val dataInput = DataInputStream(inputStream)

        val contractsCount = dataInput.readInt()

        return Array(contractsCount, {
            val info = MethodInfo(dataInput)
            val contract = SignatureContract(dataInput)
            SignatureInfo(info, contract)
        }).toMutableList()
    }

    companion object {
        fun createPacketsBySignatureContracts(contractData: List<SignatureInfo>): ArrayList<PacketImpl> {
            val outputStream = ByteArrayOutputStream()
            val dataOut = DataOutputStream(outputStream)

            dataOut.writeInt(contractData.size)

            for (data in contractData) {
                val info = data.methodInfo
                val contract = data.contract
                info.serialize(dataOut)
                contract.serialize(dataOut)
            }
            return ArrayList(listOf(PacketImpl(outputStream.toByteArray())))
        }

    }
}