package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.Gson
import com.google.gson.JsonParseException
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.DiffPreservingStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.SignatureStorageImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import org.jetbrains.ruby.runtime.signature.server.serialisation.ServerResponseBean
import org.jetbrains.ruby.runtime.signature.server.serialisation.toCallInfo
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

const val DEFAULT_PORT_NUMBER = 7777

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
    var portNumber = DEFAULT_PORT_NUMBER
        private set

    @JvmStatic
    fun main(args: Array<String>) {
        parseArgs(args).let {
            DatabaseProvider.connectToDB(it.dbFilePath)
        }
        DatabaseProvider.createAllDatabases()
        SignatureServer.runServerAsyncIfNotRunYet(isDaemon = false)
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

    private val socketDispatcher = SocketDispatcher()

    private val pollJsonThread = PollJsonThread()

    /**
     * Run server in separate [Thread] if not run yet
     * @param port specify port to run server on. (7777 is used as default port) you can also specify
     * 0 to use a port number that is automatically allocated
     *
     * @return true if server run successfully; false if has already been run before this function call
     */
    @JvmStatic
    fun runServerAsyncIfNotRunYet(port: Int = portNumber, isDaemon: Boolean): Boolean {
        if (socketDispatcher.state == Thread.State.NEW && pollJsonThread.state == Thread.State.NEW) {
            LOGGER.info("Starting server")
            portNumber = port

            socketDispatcher.isDaemon = isDaemon
            socketDispatcher.start()

            pollJsonThread.isDaemon = isDaemon
            pollJsonThread.start()
            return true
        }
        return false
    }

    private fun pollJson() {
        val jsonString by lazy { if (previousPollEndedWithFlush) queue.take() else queue.poll() }
        if (callInfoContainer.size > LOCAL_STORAGE_SIZE_LIMIT || jsonString == null) {
            flushNewTuplesToMainStorage()
            previousPollEndedWithFlush = true
            if (queue.isEmpty()) isReady.set(true)
            return
        }
        previousPollEndedWithFlush = false

        try {
            parseJson(jsonString)
        } catch (e: JsonParseException) {
            LOGGER.severe("!$jsonString!\n$e")
        }
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

    private class SignatureHandler internal constructor(private val socket: Socket, private val handlerNumber: Int) : Thread() {

        init {
            LOGGER.info("New connection with client# $handlerNumber at $socket")
        }

        override fun run() {
            try {
                val br = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (true) {
                    onLowQueueCapacity()
                    val currString = ben(readTime) { br.readLine() }
                            ?: break
                    queue.put(currString)
                    isReady.set(false)
                }
            } catch (e: IOException) {
                LOGGER.severe("Error handling client# $handlerNumber: $e")
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    LOGGER.severe("Can't close a socket")
                }
                onExit(handlerNumber)
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
                        LOGGER.info("Queue capacity is low: " + remainingCapacity)
                    }
                }
            }

            fun onExit(handlerNumber: Int) {
                LOGGER.info("Connection with client# $handlerNumber closed")

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
                pollJson()
            }
        }
    }

    private class SocketDispatcher : Thread() {
        override fun run() {
            var handlersCounter = 0
            ServerSocket(portNumber).use { listener: ServerSocket ->
                portNumber = listener.localPort
                LOGGER.info("Used port is: ${listener.localPort}")
                while (true) {
                    SignatureHandler(listener.accept(), handlersCounter++)
                            .also { it.isDaemon = this.isDaemon }
                            .start()
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