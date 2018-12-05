package org.jetbrains.plugins.ruby.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.resetAllRubyTypeProviderAndIDEACaches
import org.jetbrains.ruby.runtime.signature.server.SignatureServer

/**
 * Runs [SignatureServer] in IDEA compatible mode (for example IDEAs caches will be cleaned after server
 * flushes data to DB. Server is launched in daemon mode and so on)
 *
 * @return pipe filename path which should be passed to arg-scanner.
 */
fun SignatureServer.runServerAsyncInIDEACompatibleMode(project: Project): String {
    this.afterFlushListener = {
        resetAllRubyTypeProviderAndIDEACaches(project)
    }
    return this.runServerAsync(isDaemon = true)
}
