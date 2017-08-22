package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.ruby.codeInsight.types.signature.*
import java.io.DataInput
import java.io.DataOutput

fun MethodInfo.serialize(stream: DataOutput) {
    classInfo.serialize(stream)
    stream.writeUTF(name)
    stream.writeByte(visibility.ordinal)
    location?.serialize(stream)
}

fun MethodInfo(stream: DataInput): MethodInfo {
    val classInfo = ClassInfo(stream)
    val name = stream.readUTF()
    val tmp = stream.readByte()
    val visibility = RVisibility.values()[tmp.toInt()]
    val location = Location(stream)

    return MethodInfo(classInfo, name, visibility, location)
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
    return GemInfo(name, version)
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
