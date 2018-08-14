package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.JsonParseException
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DatabaseProvider
import org.jetbrains.ruby.codeInsight.types.storage.server.DiffPreservingStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.SignatureStorageImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import org.jetbrains.ruby.runtime.signature.server.serialisation.RTupleBuilder
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger


object SignatureServer {

    private val LOGGER = Logger.getLogger("SignatureServer")

    private val mainContainer = DiffPreservingStorage(SignatureStorageImpl(), SignatureStorageImpl())
    private val newSignaturesContainer = RSignatureContractContainer()
    private val callInfoContainer = LinkedList<CallInfo>()

    private val queue = ArrayBlockingQueue<String>(10024)
    private val isReady = AtomicBoolean(true)
    val readTime = AtomicLong(0)
    val jsonTime = AtomicLong(0)
    val addTime = AtomicLong(0)

    @JvmStatic
    fun main(args: Array<String>) {

        DatabaseProvider.connect()

        transaction { DatabaseProvider.createAllDatabases() }

        Thread {
            while (true) {
                try {
                    SignatureServer.runServer()
                } catch (e: Exception) {
                    System.err.println(e)
                }
            }
        }.start()
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

    fun runServer() {
        LOGGER.info("Starting server")

        SocketDispatcher().start()

        try {
            while (true) {
                pollJson()
            }
        } finally {
            LOGGER.warning("Exiting...")
        }
    }

    private fun pollJson() {
        val jsonString = queue.poll(5, TimeUnit.SECONDS)
        if (jsonString == null) {
            flushNewTuplesToMainStorage()
            if (queue.isEmpty()) isReady.set(true)
            return
        }

        try {
            parseJson(jsonString)
        } catch (e: JsonParseException) {
            LOGGER.severe("!$jsonString!\n$e")
        }
    }

    private fun parseJson(jsonString: String) {
        val currRTuple = ben(jsonTime) { RTupleBuilder.fromJson(jsonString) }

        // filter, for example, such things #<Class:DidYouMean::Jaro>
        if (currRTuple?.methodInfo?.classInfo?.classFQN?.startsWith("#<") == true) {
            return
        }

        ben(addTime) {
            if (currRTuple != null) {
                if (!newSignaturesContainer.acceptTuple(currRTuple) // optimization
                        && !mainContainer.acceptTuple(currRTuple)) {
                    newSignaturesContainer.addTuple(currRTuple)
                }
                callInfoContainer.add(CallInfoImpl(currRTuple))
            }
        }
    }

    private fun flushNewTuplesToMainStorage() {
        for (methodInfo in newSignaturesContainer.registeredMethods) {
            if (!methodInfo.validate()) {
                LOGGER.warning("validation failed, cannot store " + methodInfo.toString())
                continue
            }
            newSignaturesContainer.getSignature(methodInfo)?.let { newSignature: RSignatureContract ->
                transaction {
                    val storedSignature = mainContainer.getSignature(methodInfo)
                    mainContainer.putSignature(SignatureInfo(methodInfo,
                            if (storedSignature == null) {
                                newSignature
                            } else {
                                RSignatureContract.mergeMutably(storedSignature.contract, newSignature)
                            }
                    ))
                }
            }
        }
        for (callInfo in callInfoContainer) {
            CallInfoTable.insertInfoIfNotContains(callInfo)
        }
        callInfoContainer.clear()
        newSignaturesContainer.clear()
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

    private class SocketDispatcher : Thread() {
        override fun run() {
            var handlersCounter = 0
            ServerSocket(7777).use { listener: ServerSocket ->
                while (true) {
                    SignatureHandler(listener.accept(), handlersCounter++).start()
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

private fun <T : RSignatureStorage.Packet> RSignatureStorage<T>.acceptTuple(tuple: RTuple): Boolean {
    val contractInfo = getSignature(tuple.methodInfo)
    return contractInfo != null
            && tuple.argsInfo == contractInfo.contract.argsInfo
            && SignatureContract.accept(contractInfo.contract, tuple)

}
