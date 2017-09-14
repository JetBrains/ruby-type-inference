package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.serialize
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class SignatureStorageImpl : RSignatureStorage<PacketImpl>, RSignatureProvider by RSignatureProviderImpl() {
    override fun formPackets(descriptor: RSignatureStorage.ExportDescriptor?): MutableCollection<PacketImpl> {
        val contractData = transaction {
            val join = GemInfoTable innerJoin ClassInfoTable innerJoin MethodInfoTable innerJoin SignatureTable
            val resultsIterable = if (descriptor == null) {
                join.selectAll()
            } else {
                join.select {
                    MyInListOrNotInListOp(
                            listOf(GemInfoTable.name, GemInfoTable.version),
                            descriptor.gemsToIncludeOrExclude.map { listOf(it.name, it.version) },
                            descriptor.isInclude
                    )
                }
            }

            resultsIterable.toList().map { SignatureInfo(it) }
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

private class MyInListOrNotInListOp(val columns: List<Column<*>>, val list: Iterable<List<*>>, val isInList: Boolean = true) : Op<Boolean>() {

    override fun toSQL(queryBuilder: QueryBuilder): String = buildString {
        list.iterator().let { i ->
            if (!i.hasNext()) {
                append(booleanLiteral(!isInList).toSQL(queryBuilder))
            } else {
                append('(')
                columns.joinTo(this) { column -> column.toSQL(queryBuilder) }
                append(')')

                when {
                    isInList -> append(" IN (")
                    else -> append(" NOT IN (")
                }

                list.joinTo(this) { values ->
                    "(${values.mapIndexed { valueIndex, value -> columns[valueIndex].columnType.valueToString(value) }
                            .joinToString()})"
                }

                append(')')
            }
        }
    }
}
