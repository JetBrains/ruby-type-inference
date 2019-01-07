package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.Gson
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import kotlin.concurrent.thread

private const val EXIT_COMMAND = "EXIT";

fun main(args: Array<String>) {
    parseArgs(args).let {
        DatabaseProvider.connectToDB(it.dbFilePath)
    }
    DatabaseProvider.createAllDatabases()

    val pipeFileName = SignatureServer().runServerAsync(isDaemon = false)
    println("Pass this to arg-scanner via --pipe-file-path: $pipeFileName")

    // Intercept Ctrl+C
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        File(pipeFileName).delete()
    })
}

private data class ParsedArgs(val dbFilePath: String)

private fun parseArgs(args: Array<String>): ParsedArgs {
    if (args.size != 1) {
        System.err.println("""
                One argument required: path-to-h2-db-file
                Or if you run it via gradle: ./gradlew contract-creator:runServer --args path-to-db
            """.trimIndent())
        System.exit(1)
    }
    return ParsedArgs(args.single())
}

class SignatureServer {
    companion object {
        private const val LOCAL_STORAGE_SIZE_LIMIT = 128

        @Suppress("ObjectPropertyName")
        private val _runningServers: MutableList<SignatureServer> = Collections.synchronizedList(mutableListOf())
        val runningServers: List<SignatureServer>
            get() = _runningServers
    }
    private val LOGGER = Logger.getLogger("SignatureServer")

    private val callInfoContainer = LinkedList<String>()

    private val gson = Gson()
    private val queue = ArrayBlockingQueue<String>(10024)
    private val isReady = AtomicBoolean(true)
    private var previousPollEndedWithFlush = false
    private val correctResolvesWriter = PrintWriter(Paths.get(
            System.getProperty("user.home")!!,
            "logs",
            "correct-resolves-${System.currentTimeMillis()}.txt"
    ).toFile().also { it.createNewFile() })

    val readTime = AtomicLong(0)
    val jsonTime = AtomicLong(0)
    val addTime = AtomicLong(0)

    private val signatureHandler = SignatureHandler()
    private val pollJsonThread = PollJsonThread()

    fun isProcessingRequests() = !isReady.get()

    private fun generateTempFilePath(prefix: String = ""): String {
        val dirForTempFiles = System.getProperty("java.io.tmpdir")
        return Paths.get(dirForTempFiles, prefix + UUID.randomUUID()).toString()
    }

    /**
     * @return pipe filename path which should be passed to arg-scanner
     */
    fun runServerAsync(isDaemon: Boolean): String {
        isReady.set(false)
        _runningServers.add(this)
        LOGGER.info("Starting server")

        val pipeFileName = generateTempFilePath(prefix = "ruby-type-inference-pipe-")
        val proc: Process = Runtime.getRuntime().exec("mkfifo $pipeFileName")
        if (proc.waitFor() != 0) {
            throw RuntimeException("Cannot create pipe file")
        }

        signatureHandler.pipeFilePath = pipeFileName
        signatureHandler.isDaemon = isDaemon
        signatureHandler.start()

        pollJsonThread.isDaemon = isDaemon
        pollJsonThread.start()
        return pipeFileName
    }

    var afterFlushListener: (() -> Unit)? = null

    var afterExitListener: (() -> Unit)? = null

    /**
     * @return true when client won't send data anymore
     */
    private fun pollJson(): Boolean {
        val jsonString by lazy { if (previousPollEndedWithFlush) queue.take() else queue.poll() }
        if (callInfoContainer.size > LOCAL_STORAGE_SIZE_LIMIT || jsonString == null || jsonString == EXIT_COMMAND) {
            flushNewTuplesToMainStorage()
            previousPollEndedWithFlush = true
            return jsonString == EXIT_COMMAND
        }
        previousPollEndedWithFlush = false

        parseJson(jsonString)
        return false
    }

    private fun parseJson(jsonString: String) {
        callInfoContainer.add(jsonString)
    }

    private fun flushNewTuplesToMainStorage() {
        for (resolveInfo in callInfoContainer) {
            correctResolvesWriter.println(resolveInfo)
        }

        callInfoContainer.clear()
        afterFlushListener?.invoke()
    }

    private inner class SignatureHandler internal constructor() : Thread() {
        var pipeFilePath: String = ""

        override fun run() {
            try {
                var missed = 0
                var br = FileInputStream(pipeFilePath).bufferedReader()
                var currString: String? = ""
                do {
                    // continue when EOF is reached because EOF doesn't mean that program
                    // traced by arg-scanner is died. Program could simply call `Kernel.exec`
                    // See CallStatCompletionTest.testRubyExecWithBuffering and
                    // CallStatCompletionTest.testRubyExecWithoutBuffering
                    currString = ben(readTime) { br.readLine() }

                    if (currString != null) {
                        queue.put(currString)
                    } else {
                        missed++
                        br.close()
                        // If don't reassign reader then `readLine` will always return `null`
                        br = FileInputStream(pipeFilePath).bufferedReader()
                    }

                    // 1000 is just threshold for safety
                } while (currString != EXIT_COMMAND && missed < 1000)
            } catch (e: IOException) {
                LOGGER.severe("Error in SignatureHandler")
            } finally {
                File(pipeFilePath).delete()
            }
        }
    }

    private inner class PollJsonThread : Thread() {
        override fun run() {
            while (true) {
                if (pollJson()) {
                    isReady.set(true)
                    afterExitListener?.invoke()
                    _runningServers.remove(this@SignatureServer)
                    break
                }
            }
        }
    }
}

fun <T> ben(x: AtomicLong, F: ()->T): T {
    val start = System.nanoTime()
    try {
        return F.invoke()
    }
    finally {
        x.addAndGet(System.nanoTime() - start)
    }
}