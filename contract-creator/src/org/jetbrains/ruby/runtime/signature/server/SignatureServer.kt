package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.CallInfo
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import org.jetbrains.ruby.runtime.signature.server.serialisation.ServerResponseBean
import org.jetbrains.ruby.runtime.signature.server.serialisation.toCallInfo
import java.io.*
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

    private val callInfoContainer = LinkedList<CallInfo>()

    private val gson = Gson()
    private val queue = ArrayBlockingQueue<String>(10024)
    private val isReady = AtomicBoolean(true)
    private var previousPollEndedWithFlush = false

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
        val currCallInfo = ben(jsonTime) {
            try {
                return@ben gson.fromJson(jsonString, ServerResponseBean::class.java)?.toCallInfo()
            } catch (ex: Throwable) {
                when (ex) {
                    is JsonSyntaxException, is JsonParseException -> {
                        // Sometimes it's possible that some json fields contain quotation mark and we got JsonSyntaxException
                        LOGGER.severe("Cannot parse: $jsonString")
                    }
                    is IllegalStateException -> {
                        LOGGER.severe(ex.message)
                    }
                    else -> throw ex
                }
                return@ben null
            }
        }

        // filter, for example, such things #<Class:DidYouMean::Jaro>
        if (currCallInfo?.methodInfo?.classInfo?.classFQN?.startsWith("#<") == true) {
            return
        }

        if (currCallInfo != null) {
            ben(addTime) { callInfoContainer.add(currCallInfo) }
        }
    }

    private fun flushNewTuplesToMainStorage() {
        transaction {
            for (callInfo in callInfoContainer) {
                CallInfoTable.insertInfoIfNotContains(callInfo)
            }
        }
        callInfoContainer.clear()
        afterFlushListener?.invoke()
    }

    private inner class SignatureHandler internal constructor() : Thread() {
        var pipeFilePath: String = ""

        override fun run() {
            try {
                val br = FileInputStream(pipeFilePath).bufferedReader()
                var currString = ""
                do {
                    // continue when EOF is reached because EOF doesn't mean that program
                    // traced by arg-scanner is died. Program could simply call `Kernel.exec`
                    // See CallStatCompletionTest.testRubyExecWithBuffering and
                    // CallStatCompletionTest.testRubyExecWithoutBuffering
                    currString = ben(readTime) { br.readLine() } ?: continue

                    queue.put(currString)
                } while (currString != EXIT_COMMAND)
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