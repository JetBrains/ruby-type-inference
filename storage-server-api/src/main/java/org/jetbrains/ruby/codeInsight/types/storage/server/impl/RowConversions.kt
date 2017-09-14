package org.jetbrains.ruby.codeInsight.types.storage.server.impl

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import java.io.DataInputStream

fun GemInfo(row: ResultRow): GemInfo = GemInfo(row[GemInfoTable.name], row[GemInfoTable.version])

fun ClassInfo(row: ResultRow): ClassInfo = ClassInfo(GemInfo(row), row[ClassInfoTable.fqn])

fun Location(row: ResultRow): Location? {
    val locationFile = row[MethodInfoTable.locationFile]
            ?: return null

    return Location(locationFile, row[MethodInfoTable.locationLineno])
}

fun MethodInfo(row: ResultRow): MethodInfo = MethodInfo.Impl(
        ClassInfo(row),
        row[MethodInfoTable.name],
        row[MethodInfoTable.visibility],
        Location(row))

fun SignatureInfo(row: ResultRow): SignatureInfo {
    val blob = row[SignatureTable.contract]
    try {
        return SignatureInfo(MethodInfo(row), SignatureContract(DataInputStream(blob.binaryStream)))
    } finally {
        blob.free()
    }
}