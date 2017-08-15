package org.jetbrains.ruby.codeInsight.types.signature.serialization

import java.io.DataInput
import java.io.DataOutput
import java.util.*

class StringDataOutput : DataOutput {
    val result = StringBuilder()

    private var wasNewline = true

    fun newline() {
        result.append("\n")
        wasNewline = true
    }

    private fun writeSpace() {
        if (!wasNewline) {
            result.append(' ')
        }
        wasNewline = false
    }

    override fun writeShort(v: Int): Unit = TODO("not implemented")

    override fun writeLong(v: Long): Unit = TODO("not implemented")

    override fun writeDouble(v: Double): Unit = TODO("not implemented")

    override fun writeBytes(s: String?): Unit = TODO("not implemented")

    override fun writeByte(v: Int) {
        writeSpace()
        result.append(v)
    }

    override fun writeFloat(v: Float): Unit = TODO("not implemented")

    override fun write(b: Int): Unit = TODO("not implemented")

    override fun write(b: ByteArray?): Unit = TODO("not implemented")

    override fun write(b: ByteArray?, off: Int, len: Int): Unit = TODO("not implemented")

    override fun writeChars(s: String?): Unit = TODO("not implemented")

    override fun writeChar(v: Int): Unit = TODO("not implemented")

    override fun writeBoolean(v: Boolean) {
        writeSpace()
        result.append(if (v) '1' else '0')
    }

    override fun writeUTF(s: String?) {
        writeSpace()
        result.append(s)
    }

    override fun writeInt(v: Int) {
        writeSpace()
        result.append(v)
    }
}

class StringDataInput(s: String) : DataInput {
    private val scanner = Scanner(s)

    override fun readFully(b: ByteArray?): Unit = TODO("not implemented")

    override fun readFully(b: ByteArray?, off: Int, len: Int): Unit = TODO("not implemented")

    override fun readInt(): Int = scanner.nextInt()

    override fun readUnsignedShort(): Int = TODO("not implemented")

    override fun readUnsignedByte(): Int = TODO("not implemented")

    override fun readUTF(): String = scanner.next()

    override fun readChar(): Char = TODO("not implemented")

    override fun readLine(): String = TODO("not implemented")

    override fun readByte(): Byte = scanner.nextByte()

    override fun readFloat(): Float = TODO("not implemented")

    override fun skipBytes(n: Int): Int = TODO("not implemented")

    override fun readLong(): Long = TODO("not implemented")

    override fun readDouble(): Double = TODO("not implemented")

    override fun readBoolean(): Boolean = (scanner.nextInt() == 1)

    override fun readShort(): Short = TODO("not implemented")
}