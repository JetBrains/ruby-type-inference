package org.jetbrains.ruby.runtime.signature.server

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.RSignature
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContract
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.serialize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object LocalBucket : RSignatureStorage {
    private val provider: SignatureDBProvider = SignatureDBProvider()

    override fun formPackets(): Collection<RSignatureStorage.Packet> {

        provider.connectToDB()
        val contractData = transaction { provider.getRegisteredContractsWithInfos() }
        val packets = ArrayList<RSignatureStorage.Packet>()
        val outputStream = ByteArrayOutputStream()
        val dataOut = DataOutputStream(outputStream)

        for (data in contractData) {
            transaction {
                val info = data.methodInfo
                val contract = data.contract
                info.serialize(dataOut)
                contract.serialize(dataOut)
            }
        }
        packets.add(TestPacketImpl(outputStream.toByteArray(), contractData.size))
        return packets
    }

    override fun readPacket(packet: RSignatureStorage.Packet) {

        if (packet is TestPacketImpl) {

            val inputStream = ByteArrayInputStream(packet.data)
            val dataInput = DataInputStream(inputStream)
            repeat(packet.size) { _ ->
                val info = MethodInfo(dataInput)
                val contract = SignatureContract(dataInput)
                val oldContract = provider.getSignatureContract(info)

                if (oldContract == null)
                    provider.putSignatureContract(info, contract)
                else {
                    if (oldContract is RSignatureContract && contract is RSignatureContract) {
                        oldContract.mergeWith(contract)
                        provider.putSignatureContract(info, oldContract)
                    }
                }
            }

        }
    }


    override fun addSignature(signature: RSignature) {

    }

}