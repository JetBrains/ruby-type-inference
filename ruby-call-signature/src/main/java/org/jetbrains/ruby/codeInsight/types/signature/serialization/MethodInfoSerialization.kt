package org.jetbrains.ruby.codeInsight.types.signature.serialization

import org.jetbrains.ruby.codeInsight.types.signature.*
import java.io.DataInput
import java.io.DataOutput

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
