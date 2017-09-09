package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.JsonParseException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DiffPreservingStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.SignatureStorageImpl
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.ClassInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureTable
import org.jetbrains.ruby.runtime.signature.server.serialisation.RTupleBuilder
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger


object SignatureServer {

    private val LOGGER = Logger.getLogger("SignatureServer")

    private val mainContainer = DiffPreservingStorage(SignatureStorageImpl(), SignatureStorageImpl())
    private val newSignaturesContainer = RSignatureContractContainer()

    private val queue = ArrayBlockingQueue<String>(10024)
    private val isReady = AtomicBoolean(true)
    val readTime = AtomicLong(0)
    val jsonTome = AtomicLong(0)
    val addTime = AtomicLong(0)

    @JvmStatic
    fun main(args: Array<String>) {

        Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
        val transaction = TransactionManager.manager.newTransaction()
        SchemaUtils.create(GemInfoTable, ClassInfoTable, MethodInfoTable, SignatureTable)
        transaction.commit()
        runServer()
    }

    fun getContract(info: MethodInfo): SignatureContract? {
        return mainContainer.getSignature(info)?.contract?.let { (it as? RSignatureContract)?.copy() ?: it }
    }

    fun getContractByMethodAndReceiverName(methodName: String, receiverName: String): SignatureContract? {
        return getMethodByClass(receiverName, methodName)?.let { mainContainer.getSignature(it)?.contract }
    }

    fun getStorage() = mainContainer

    fun getMethodByClass(className: String, methodName: String): MethodInfo? {
        return mainContainer.getRegisteredMethods(ClassInfo(className)).find { it.name == methodName }
    }

    fun isProcessingRequests() = !isReady.get()

    fun runServer() {
        LOGGER.info("Starting server")

        SocketDispatcher().start()

        while (true) {
            val jsonString = queue.poll(5, TimeUnit.SECONDS)
            if (jsonString == null) {
                flushNewTuplesToMainStorage()
                if (queue.isEmpty()) isReady.set(true)
                continue
            }

            try {
                val currRTuple = ben(jsonTome) { RTupleBuilder.fromJson(jsonString) }

                if (currRTuple?.methodInfo?.classInfo?.classFQN?.startsWith("#<") == true) {
                    continue
                }

                ben(addTime) {
                    if (currRTuple != null
                            && !SignatureServer.newSignaturesContainer.acceptTuple(currRTuple) // optimization
                            && !SignatureServer.mainContainer.acceptTuple(currRTuple)) {
                        SignatureServer.newSignaturesContainer.addTuple(currRTuple)
                    }
                }
            } catch (e: JsonParseException) {
                LOGGER.severe("!$jsonString!\n$e")
            }
        }
    }

    private fun flushNewTuplesToMainStorage() {
        for (methodInfo in newSignaturesContainer.registeredMethods) {
            newSignaturesContainer.getSignature(methodInfo)?.let { newSignature ->
                transaction {
                    val storedSignature = mainContainer.getSignature(methodInfo)
                    mainContainer.putSignature(SignatureInfo(methodInfo,
                            if (storedSignature == null)
                                newSignature
                            else
                                RSignatureContract.mergeMutably(storedSignature.contract, newSignature)
                    ))
                }
            }
        }
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
                    val currString = ben(readTime) { br.readLine() }
                            ?: break
                    if (queue.size > queue.remainingCapacity()) {
                        LOGGER.info("Queue capacity is low")
                    }
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

                LOGGER.info("Connection with client# $handlerNumber closed")

                LOGGER.info("Stats: ")
                LOGGER.info("add=" + addTime.toLong() * 1e-6)
                LOGGER.info("json=" + jsonTome.toLong() * 1e-6)
                LOGGER.info("read=" + readTime.toLong() * 1e-6)
            }
        }
    }

    private class SocketDispatcher : Thread() {
        override fun run() {
            var handlersCounter = 0
            ServerSocket(7777).use { listener ->
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
