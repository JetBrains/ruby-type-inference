package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.ruby.codeInsight.types.signature.*
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

fun MethodInfo.serialize(stream: DataOutput) {
    classInfo.serialize(stream)
    stream.writeUTF(name)
    stream.writeByte(visibility.ordinal)
    stream.writeBoolean(location != null)
    location?.serialize(stream)
}

fun MethodInfo(stream: DataInput): MethodInfo {
    val classInfo = ClassInfo(stream)
    val name = stream.readUTF()
    val visibility = RVisibility.values()[stream.readByte().toInt()]
    val isLocationPresent = stream.readBoolean()
    val location = if (isLocationPresent) Location(stream) else null

    return MethodInfo.Impl(classInfo, name, visibility, location)
}

fun ClassInfo.serialize(stream: DataOutput) {
    stream.writeUTF(classFQN)
    gemInfo.serialize(stream)
}

fun ClassInfo(stream: DataInput): ClassInfo {
    val classFQN = stream.readUTF()
    val gemInfo = GemInfo(stream)

    return ClassInfo(gemInfo, classFQN)
}

fun GemInfo?.serialize(stream: DataOutput) {
    (this ?: GemInfo.NONE).let {
        stream.writeUTF(it.name)
        stream.writeUTF(it.version)
    }
}

fun GemInfo(stream: DataInput): GemInfo? {
    val name = stream.readUTF()
    val version = stream.readUTF()
    return GemInfoOrNull(name, version)
}

fun Location.serialize(stream: DataOutput) {
    stream.writeUTF(path)
    stream.writeInt(lineno)
}

fun Location(stream: DataInput): Location {
    val path = stream.readUTF()
    val lineno = stream.readInt()

    return Location(path, lineno)
}

object SignatureInfoSerialization {

    private val PROTOCOL_VERSION = 1

    fun serialize(signatureInfos: List<SignatureInfo>, stream: DataOutput) {
        writeProtocolVersion(stream)

        val (classInfo2Id, gemInfo2Id) = collectClassInfoAndGemInfo(signatureInfos)
        serializeGemInfos(gemInfo2Id, stream)
        serializeClassInfos(stream, classInfo2Id, gemInfo2Id)
        serializeSignatureInfos(stream, signatureInfos, classInfo2Id)
    }

    fun deserialize(stream: DataInput): List<SignatureInfo> {
        checkProtocolVersion(stream)

        val id2GemInfo = deserializeGemInfo(stream)
        val id2ClassInfo = deserializeClassInfo(stream, id2GemInfo)
        return deserializeSignatureInfos(stream, id2ClassInfo)
    }

    private fun writeProtocolVersion(stream: DataOutput) {
        stream.writeInt(PROTOCOL_VERSION)
    }


    private fun checkProtocolVersion(stream: DataInput) {
        val version = stream.readInt()
        if (version != PROTOCOL_VERSION) {
            throw IOException("Cannot deserialize SignatureInfos: protocol version mismatch. Expected:" +
                    " $PROTOCOL_VERSION but got: $version")
        }
    }

    private fun collectClassInfoAndGemInfo(signatureInfos: List<SignatureInfo>) :
            Pair<LinkedHashMap<ClassInfo, Int>, LinkedHashMap<GemInfo, Int>>{
        val gemInfo2Id = LinkedHashMap<GemInfo, Int>()
        val classInfo2Id = LinkedHashMap<ClassInfo, Int>()
        signatureInfos.forEach {
            val classInfo = it.methodInfo.classInfo
            val gemInfo = classInfo.gemInfo
            classInfo2Id.putIfAbsent(classInfo, classInfo2Id.size)
            gemInfo?.let {
                gemInfo2Id.putIfAbsent(gemInfo, gemInfo2Id.size)
            }
        }
        return Pair(classInfo2Id, gemInfo2Id)
    }

    private fun serializeGemInfos(gemInfo2Id: LinkedHashMap<GemInfo, Int>, stream: DataOutput) {
        stream.writeInt(gemInfo2Id.size)
        var iter = 0
        gemInfo2Id.forEach {
            assert(iter++ == it.value)
            it.key.serialize(stream)
        }
    }

    private fun deserializeGemInfo(stream: DataInput): LinkedHashMap<Int, GemInfo> {
        val id2GemInfo = LinkedHashMap<Int, GemInfo>()
        val gemInfoSize = stream.readInt()
        for (i in 1..gemInfoSize) {
            val gemInfo = GemInfo(stream)!!
            id2GemInfo.put(i - 1, gemInfo)
        }
        id2GemInfo.put(-1, GemInfo.NONE)
        return id2GemInfo
    }

    private fun serializeClassInfos(stream: DataOutput, classInfo2Id: LinkedHashMap<ClassInfo, Int>, gemInfo2Id: LinkedHashMap<GemInfo, Int>) {
        gemInfo2Id.put(GemInfo.NONE, -1)
        var iter = 0
        stream.writeInt(classInfo2Id.size)
        classInfo2Id.forEach {
            assert(iter++ == it.value)
            stream.writeUTF(it.key.classFQN)
            stream.writeInt(gemInfo2Id.getValue(it.key.gemInfo ?: GemInfo.NONE))
        }
    }

    private fun deserializeClassInfo(stream: DataInput,
                                     id2GemInfo: LinkedHashMap<Int, GemInfo>): LinkedHashMap<Int, ClassInfo> {
        val id2ClassInfo = LinkedHashMap<Int, ClassInfo>()
        val classInfoSize = stream.readInt()
        for (i in 1..classInfoSize) {
            val fqn = stream.readUTF()
            val gemInfo = id2GemInfo.getValue(stream.readInt())
            id2ClassInfo.put(i - 1, ClassInfo(gemInfo, fqn))
        }
        return id2ClassInfo
    }

    private fun serializeSignatureInfos(stream: DataOutput, signatureInfos: List<SignatureInfo>, classInfo2Id: LinkedHashMap<ClassInfo, Int>) {
        stream.writeInt(signatureInfos.size)
        signatureInfos.forEach {
            val methodInfo = it.methodInfo
            stream.writeUTF(methodInfo.name)
            stream.writeByte(methodInfo.visibility.ordinal)
            stream.writeBoolean(methodInfo.location != null)
            methodInfo.location?.serialize(stream)
            stream.writeInt(classInfo2Id.getValue(methodInfo.classInfo))
            it.contract.serialize(stream)
        }
    }

    private fun deserializeSignatureInfos(stream: DataInput,
                                          id2ClassInfo: LinkedHashMap<Int, ClassInfo>) : List<SignatureInfo> {
        val result = ArrayList<SignatureInfo>()
        val signatureInfoSize = stream.readInt()
        for (i in 1..signatureInfoSize) {
            val name = stream.readUTF()
            val visibility = RVisibility.values()[stream.readByte().toInt()]
            val isLocationPresent = stream.readBoolean()
            val location = if (isLocationPresent) Location(stream) else null
            val classInfo = id2ClassInfo.getValue(stream.readInt())
            val methodInfo = MethodInfo.Impl(classInfo, name, visibility, location)
            val contract = SignatureContract(stream)
            result.add(SignatureInfo(methodInfo, contract))
        }
        return result
    }
}
