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
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import kotlin.concurrent.thread

private const val POLL_THREAD_EXIT = "\u0000"

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
        if (callInfoContainer.size > LOCAL_STORAGE_SIZE_LIMIT || jsonString == null || jsonString == POLL_THREAD_EXIT) {
            flushNewTuplesToMainStorage()
            previousPollEndedWithFlush = true
            if (queue.isEmpty()) isReady.set(true)
            return jsonString == POLL_THREAD_EXIT
        }
        previousPollEndedWithFlush = false

        try {
            parseJson(jsonString)
        } catch (ex: Throwable) {
            when (ex) {
                is JsonSyntaxException, is JsonParseException -> {
                    // Sometimes it's possible that some json fields contain quotation mark and we got JsonSyntaxException
                    LOGGER.severe("Cannot parse: $jsonString")
                }
                else -> throw ex
            }
        }
        return false
    }

    private fun parseJson(jsonString: String) {
        val currCallInfo = ben(jsonTime) { gson.fromJson(jsonString, ServerResponseBean::class.java)?.toCallInfo() }

        // filter, for example, such things #<Class:DidYouMean::Jaro>
        if (currCallInfo?.methodInfo?.classInfo?.classFQN?.startsWith("#<") == true) {
            return
        }

        ben(addTime) {
            if (currCallInfo != null) {
                callInfoContainer.add(currCallInfo)
            }
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
                while (true) {
                    val currString = ben(readTime) { br.readLine() }
                            ?: break
                    queue.put(currString)
                    isReady.set(false)
                }
            } catch (e: IOException) {
                LOGGER.severe("Error in SignatureHandler")
            } finally {
                queue.put(POLL_THREAD_EXIT)
                File(pipeFilePath).delete()
            }
        }
    }

    private inner class PollJsonThread : Thread() {
        override fun run() {
            while (true) {
                if (pollJson()) {
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