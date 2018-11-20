package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.DiffPreservingStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.SignatureStorageImpl
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

object SignatureServer {
    private val LOGGER = Logger.getLogger("SignatureServer")

    private val mainContainer = DiffPreservingStorage(SignatureStorageImpl(), SignatureStorageImpl())
    private val callInfoContainer = LinkedList<CallInfo>()

    private val gson = Gson()
    private val queue = ArrayBlockingQueue<String>(10024)
    private val isReady = AtomicBoolean(true)
    private var previousPollEndedWithFlush = false
    private const val LOCAL_STORAGE_SIZE_LIMIT = 128
    val readTime = AtomicLong(0)
    val jsonTime = AtomicLong(0)
    val addTime = AtomicLong(0)

    @JvmStatic
    fun main(args: Array<String>) {
        parseArgs(args).let {
            DatabaseProvider.connectToDB(it.dbFilePath)
        }
        DatabaseProvider.createAllDatabases()

        val pipeFileName = SignatureServer.runServerAsync(isDaemon = false)
        println("Pass this to arg-scanner via --pipe-file-path: $pipeFileName")

        // Intercept Ctrl+C
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            File(pipeFileName).delete()
        })
    }

    fun getContract(info: MethodInfo): SignatureContract? {
        return mainContainer.getSignature(info)?.contract?.let { (it as? RSignatureContract)?.copy() ?: it }
    }

    fun getContractByMethodAndReceiverName(methodName: String, receiverName: String): SignatureContract? {
        return getMethodByClass(receiverName, methodName)?.let { mainContainer.getSignature(it)?.contract }
    }

    fun getMethodByClass(className: String, methodName: String): MethodInfo? {
        return mainContainer.getRegisteredMethods(ClassInfo(className)).find { it.name == methodName }
    }

    fun getStorage() = mainContainer

    fun isProcessingRequests() = !isReady.get()

    private fun generateTempFilePath(prefix: String = ""): String {
        val dirForTempFiles = System.getProperty("java.io.tmpdir")
        return Paths.get(dirForTempFiles, prefix + UUID.randomUUID()).toString()
    }

    /**
     * @return pipe filename path which should be passed to arg-scanner
     */
    @JvmStatic
    fun runServerAsync(isDaemon: Boolean): String {
        LOGGER.info("Starting server")

        val pipeFileName = generateTempFilePath(prefix = "ruby-type-inference-pipe-")
        val proc: Process = Runtime.getRuntime().exec("mkfifo $pipeFileName")
        if (proc.waitFor() != 0) {
            throw RuntimeException("Cannot create pipe file")
        }

        val signatureHandler = SignatureHandler(pipeFileName)

        signatureHandler.isDaemon = isDaemon
        signatureHandler.start()

        val pollJsonThread = PollJsonThread()

        pollJsonThread.isDaemon = isDaemon
        pollJsonThread.start()
        return pipeFileName
    }

    @JvmStatic
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
    }

    private class SignatureHandler internal constructor(private val pipeFilePath: String) : Thread() {
        override fun run() {
            try {
                val br = FileInputStream(pipeFilePath).bufferedReader()
                while (true) {
                    onLowQueueCapacity()
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
                onExit()
            }
        }

        companion object {

            private var iter = 0L
            private var millis = System.currentTimeMillis()

            fun onLowQueueCapacity() {
                ++iter
                val remainingCapacity = queue.remainingCapacity()
                val mask = (1L shl 12) - 1L
                if (iter and mask == 0L) {
                    val timeInterval = System.currentTimeMillis() - millis
                    millis = System.currentTimeMillis()
                    LOGGER.info( "[" + iter.toString() + "]" +" per second: " +
                            ((mask +1) * 1000 / timeInterval).toString())
                    if (queue.size > remainingCapacity) {
                        LOGGER.info("Queue capacity is low: $remainingCapacity")
                    }
                }
            }

            fun onExit() {
                LOGGER.info("Connection with client closed")

                LOGGER.info("Stats: ")
                LOGGER.info("add=" + addTime.toLong() * 1e-6)
                LOGGER.info("json=" + jsonTime.toLong() * 1e-6)
                LOGGER.info("read=" + readTime.toLong() * 1e-6)
            }
        }
    }

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

    private data class ParsedArgs(val dbFilePath: String)

    private class PollJsonThread : Thread() {
        override fun run() {
            while (true) {
                if (pollJson()) {
                    afterExitListener?.invoke()
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