package org.jetbrains.ruby.runtime.signature.server

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.RTuple
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.BlobSerializer
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.serialize
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object LocalBucket : RSignatureStorage {

    override fun formPackets(): Collection<RSignatureStorage.Packet> {
        val contractData = transaction { getRegisteredContractsWithInfos() }
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
                val info = org.jetbrains.ruby.codeInsight.types.signature.serialization.MethodInfo(dataInput)
                val contract = SignatureContract(dataInput)
                val oldContract = getSignatureContract(info)

                if (oldContract == null)
                    putSignatureContract(info, contract)
                else {
                    if (oldContract is RSignatureContract && contract is RSignatureContract) {
                        oldContract.mergeWith(contract)
                        putSignatureContract(info, oldContract)
                    }
                }
            }

        }
    }

    override fun addTuple(signature: RTuple) {

    }

    private fun getSignatureContract(methodInfo: MethodInfo): SignatureContract? {

        return transaction {
            val gemInfo = methodInfo.classInfo.gemInfo
            val classInfo = methodInfo.classInfo
            val gemInfoId: EntityID<Int>? = if (gemInfo != null) {
                GemInfoTable.select { GemInfoTable.name.eq(gemInfo.name) and GemInfoTable.version.eq(gemInfo.version) }.firstOrNull()?.get(GemInfoTable.id)
            } else {
                null
            }

            val insertedClassInfo = ClassInfoTable.select { ClassInfoTable.gemInfo.eq(gemInfoId) and ClassInfoTable.fqn.eq(classInfo.classFQN) }.firstOrNull()?.get(ClassInfoTable.id) ?: return@transaction null

            val insertedMethodInfo = MethodInfoTable.select {
                MethodInfoTable.classInfo.eq(insertedClassInfo) and MethodInfoTable.name.eq(methodInfo.name) and
                        MethodInfoTable.visibility.eq(methodInfo.visibility) and MethodInfoTable.locationFile.eq(methodInfo.location?.path) and MethodInfoTable.locationLineno.eq(methodInfo.location?.lineno ?: 0)
            }.firstOrNull()?.get(MethodInfoTable.id) ?: return@transaction null

            return@transaction SignatureContractData.find { SignatureTable.methodInfo.eq(insertedMethodInfo) }.firstOrNull()?.contract
        }
    }

    private fun putSignatureContract(methodInfo: MethodInfo, contract: SignatureContract) {

        transaction {

            val gemInfo = methodInfo.classInfo.gemInfo
            val insertedGemInfo: EntityID<Int>?


            insertedGemInfo = GemInfoTable
                    .select { GemInfoTable.name.eq(gemInfo?.name ?: "") and GemInfoTable.version.eq(gemInfo?.version ?: "") }
                    .firstOrNull()
                    ?.get(GemInfoTable.id)
                    ?: GemInfoTable.insertAndGetId { it[name] = gemInfo?.name; it[version] = gemInfo?.version }

            val classInfo = methodInfo.classInfo
            val insertedClassInfo = ClassInfoTable.select { ClassInfoTable.gemInfo.eq(insertedGemInfo) and ClassInfoTable.fqn.eq(classInfo.classFQN) }.firstOrNull()?.get(ClassInfoTable.id)
                    ?: ClassInfoTable.insertAndGetId { it[ClassInfoTable.gemInfo] = insertedGemInfo; it[fqn] = classInfo.classFQN }
                    ?: throw AssertionError("Failed to insert ClassInfo: $classInfo")


            val insertedMethodInfo = MethodInfoTable.select {
                MethodInfoTable.classInfo.eq(insertedClassInfo) and MethodInfoTable.name.eq(methodInfo.name) and
                        MethodInfoTable.visibility.eq(methodInfo.visibility) and MethodInfoTable.locationFile.eq(methodInfo.location?.path) and MethodInfoTable.locationLineno.eq(methodInfo.location?.lineno ?: 0)
            }.firstOrNull()?.get(MethodInfoTable.id)
                    ?: MethodInfoTable.insertAndGetId { it[MethodInfoTable.classInfo] = insertedClassInfo; it[name] = methodInfo.name; it[visibility] = methodInfo.visibility; it[locationFile] = methodInfo.location?.path; it[locationLineno] = methodInfo.location?.lineno }
                    ?: throw AssertionError("Failed to insert MethodInfo: $methodInfo")

            val insertedSignature = SignatureTable.select { SignatureTable.methodInfo.eq(insertedMethodInfo) }.firstOrNull()?.get(SignatureTable.id)

            val blob = TransactionManager.current().connection.createBlob()
            if (insertedSignature == null) {
                SignatureTable.insertIgnore { it[SignatureTable.contract] = BlobSerializer.writeToBlob(contract, blob); it[SignatureTable.methodInfo] = insertedMethodInfo }
            } else {
                SignatureTable.update({ SignatureTable.id.eq(insertedSignature) }) {
                    it[SignatureTable.contract] = BlobSerializer.writeToBlob(contract, blob)
                }
            }
        }
    }

    private fun getRegisteredContractsWithInfos(): Collection<SignatureContractData> {
        return transaction {
            return@transaction SignatureContractData.all().toList()
        }
    }

}