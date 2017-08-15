package org.jetbrains.ruby.runtime.signature.server

import com.google.gson.JsonParseException
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.signature.serialization.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContract
import org.jetbrains.ruby.codeInsight.types.signature.serialization.serialize
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException
import org.jetbrains.ruby.runtime.signature.server.serialisation.RTupleBuilder
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.logging.Logger


object SignatureServer : RSignatureStorage {

    private val LOGGER = Logger.getLogger("SignatureServer")

    private var mainContainer = RSignatureContractContainer()
    private var newSignaturesContainer = RSignatureContractContainer()

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        runServer()
    }

    fun getContract(info: MethodInfo): SignatureContract? {
        return mainContainer.getSignature(info)!!.copy()
    }

    fun getContractByMethodName(methodName: String): SignatureContract? {
        return mainContainer.registeredMethods
                .firstOrNull { it.name == methodName }
                ?.let { mainContainer.getSignature(it)?.copy() }
    }

    fun getContractByMethodAndReceiverName(methodName: String, receiverName: String): SignatureContract? {
        return mainContainer.registeredMethods
                .firstOrNull { it.name == methodName && it.classInfo.classFQN == receiverName }
                ?.let { mainContainer.getSignature(it)?.copy() }
    }

    fun getMethodByClass(className: String, methodName: String): MethodInfo? {
        return mainContainer.registeredMethods
                .firstOrNull { it.name == methodName && it.classInfo.classFQN == className }
    }

    @Throws(StorageException::class)
    override fun addTuple(signature: RTuple) {

    }

    @Throws(StorageException::class)
    override fun readPacket(packet: RSignatureStorage.Packet) {
        if (packet is TestPacketImpl) {
            val inputStream = ByteArrayInputStream(packet.data)
            val `in` = DataInputStream(inputStream)

            val info = MethodInfo(`in`)
            val contract = SignatureContract(`in`)

            if (contract is RSignatureContract)
                SignatureServer.mainContainer.addContract(info, contract)
        }
    }

    @Throws(StorageException::class)
    override fun formPackets(): Collection<RSignatureStorage.Packet> {

        val packets = ArrayList<RSignatureStorage.Packet>()

        val outputStream = ByteArrayOutputStream()
        val out = DataOutputStream(outputStream)

        var size = 0

        for (info in SignatureServer.newSignaturesContainer.registeredMethods) {
            val contract = SignatureServer.newSignaturesContainer.getSignature(info)
                    ?: throw Error("should not happen, but..")

            info.serialize(out)
            contract.serialize(out)
            size++
        }
        packets.add(TestPacketImpl(outputStream.toByteArray(), size))

        return packets
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

                        if (currRTuple != null && !SignatureServer.mainContainer.acceptTuple(currRTuple)) {
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
                SignatureServer.mainContainer.reduce()
                try {
                    val packets = SignatureServer.formPackets()
                    for (packet in packets) {
                        LocalBucket.readPacket(packet)
                    }
                } catch (ex: Exception) {
                    LOGGER.severe(ex.message)
                }

            }
        }

    }

    @Throws(IOException::class)
    fun runServer() {
        LOGGER.info("Starting server")

        var handlersCounter = 0

        val packets = LocalBucket.formPackets()

        for (packet in packets) {
            if (packet is TestPacketImpl) {
                val packetData = packet.data

                if (packet.size > 0) {
                    val inputStream = ByteArrayInputStream(packetData)
                    val `in` = DataInputStream(inputStream)

                    val methodInfo = MethodInfo(`in`)
                    val contract = SignatureContract(`in`)
                    if (contract is RSignatureContract)
                        SignatureServer.mainContainer.addContract(methodInfo, contract)
                }
            }
        }


        try {
            ServerSocket(7777).use { listener ->
                while (true) {
                    SignatureHandler(listener.accept(), handlersCounter++).start()
                }
            }
        } finally {
            SignatureServer.mainContainer.reduce()
        }
    }

}
