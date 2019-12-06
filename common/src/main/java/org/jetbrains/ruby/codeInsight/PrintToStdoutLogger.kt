package org.jetbrains.ruby.codeInsight

import java.text.SimpleDateFormat
import java.util.*

private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

/**
 * Most basic [Logger] implementation
 */
class PrintToStdoutLogger(private val category: String) : Logger {
    constructor(cl : Class<*>) : this(cl.name)

    override fun info(msg: String) {
        println("${format.format(Calendar.getInstance())} [$category] $msg")
    }
}
