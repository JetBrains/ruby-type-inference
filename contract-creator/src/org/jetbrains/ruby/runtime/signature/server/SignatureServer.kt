package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.JsonParseException
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.DiffPreservingStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.SignatureStorageImpl
import org.jetbrains.ruby.runtime.signature.server.serialisation.RTupleBuilder
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.logging.Logger


object SignatureServer {

    private val LOGGER = Logger.getLogger("SignatureServer")

    private val mainContainer = DiffPreservingStorage(SignatureStorageImpl(), SignatureStorageImpl())
    private val newSignaturesContainer = RSignatureContractContainer()

    @JvmStatic
    fun main(args: Array<String>) {
        runServer()
    }

    fun getContract(info: MethodInfo): SignatureContract? {
        return mainContainer.getSignature(info)?.contract?.let { (it as? RSignatureContract)?.copy() ?: it }
    }

    fun getContractByMethodName(methodName: String): SignatureContract? {
        TODO("Not implemented and should not be :)")
    }

    fun getContractByMethodAndReceiverName(methodName: String, receiverName: String): SignatureContract? {
        TODO("Not implemented (and should be with gem data given)")
    }

    fun getMethodByClass(className: String, methodName: String): MethodInfo? {
        TODO("Not implemented (and should be with gem data given)")
    }

    private class SignatureHandler internal constructor(private val socket: Socket, private val handlerNumber: Int) : Thread() {

        init {
            LOGGER.info("New connection with client# $handlerNumber at $socket")
        }

        override fun run() {
            try {
                val br = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (true) {
                    val currString = br.readLine()
                            ?: break
                    try {
                        val currRTuple = RTupleBuilder.fromJson(currString)

                        if (currRTuple != null
                                && !SignatureServer.newSignaturesContainer.acceptTuple(currRTuple) // optimization
                                && !SignatureServer.mainContainer.acceptTuple(currRTuple)) {
                            SignatureServer.newSignaturesContainer.addTuple(currRTuple)
                        }
                    } catch (e: JsonParseException) {
                        LOGGER.severe("!$currString!\n$e")
                        e.printStackTrace()
                    }

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
                flushNewTuplesToMainStorage()
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
                        newSignaturesContainer.deleteSignature(methodInfo)
                    }
                }
            }
        }

    }

    fun runServer() {
        LOGGER.info("Starting server")

        var handlersCounter = 0
        ServerSocket(7777).use { listener ->
            while (true) {
                SignatureHandler(listener.accept(), handlersCounter++).start()
            }
        }
    }

}

private fun <T : RSignatureStorage.Packet> RSignatureStorage<T>.acceptTuple(tuple: RTuple): Boolean {
    val contractInfo = getSignature(tuple.methodInfo)
    return contractInfo != null
            && tuple.argsInfo == contractInfo.contract.argsInfo
            && SignatureContract.accept(contractInfo.contract, tuple)

}
