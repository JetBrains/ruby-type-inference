package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition
import java.io.DataInput
import java.io.DataOutput
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

fun ContractTransition.serialize(stream: DataOutput) {
    stream.writeBoolean(this is ReferenceContractTransition)
    when (this) {
        is ReferenceContractTransition -> stream.writeInt(mask)
        is TypedContractTransition -> stream.writeUTF(type)
        else -> throw IllegalStateException("ContractTransition should be sealed in these classes")
    }
}

fun ContractTransition(stream: DataInput): ContractTransition {
    val type = stream.readBoolean()
    return when (type) {
        true -> ReferenceContractTransition(stream.readInt())
        false -> TypedContractTransition(stream.readUTF())
    }
}

fun ParameterInfo.serialize(stream: DataOutput) {
    stream.writeUTF(name)
    stream.writeByte(modifier.ordinal)
}

fun ParameterInfo(stream: DataInput): ParameterInfo {
    return ParameterInfo(stream.readUTF(), ParameterInfo.Type.values()[stream.readByte().toInt()])
}

fun SignatureContract.serialize(stream: DataOutput) {
    stream.writeInt(argsInfo.size)
    argsInfo.forEach { it.serialize(stream) }

    stream.writeInt(nodeCount)

    val visited = HashMap<SignatureNode, Int>()
    val q = ArrayDeque<SignatureNode>()

    visited[startNode] = 0
    q.push(startNode)

    while (q.isNotEmpty()) {
        val v = q.poll()
        for (it in v.transitions.values) {
            if (!visited.containsKey(it)) {
                visited[it] = visited.size
                q.add(it)
            }
        }

        stream.writeInt(v.transitions.size)
        v.transitions.forEach { transition, u ->
            stream.writeInt(visited[u]!!)
            transition.serialize(stream)
        }
    }
}

fun SignatureContract(stream: DataInput): SignatureContract {
    val argsSize = stream.readInt()
    val argsInfo = List(argsSize) { ParameterInfo(stream) }

    val nodesSize = stream.readInt()

    val nodes = List(nodesSize) { RSignatureContractNode() }

    val distance = IntArray(nodesSize, { 0 })

    repeat(nodesSize) { currentNodeIndex ->
        val transitionsN = stream.readInt()

        repeat(transitionsN) {
            val toIndex = stream.readInt()
            distance[toIndex] = distance[currentNodeIndex] + 1
            val transition = ContractTransition(stream)
            // todo replace with constructor (iterate from the end)
            nodes[currentNodeIndex].addLink(transition, nodes[toIndex])
        }
    }

    val levels = List(argsSize + 2) { ArrayList<RSignatureContractNode>() }
    nodes.indices.forEach {
        levels[distance[it]].add(nodes[it])
    }

    return RSignatureContract(argsInfo, nodes.first(), nodes.last(), levels)

}
