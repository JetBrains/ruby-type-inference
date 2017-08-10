package org.jetbrains.ruby.runtime.signature.server;

import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage;
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoSerializationKt;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContractSerializationKt;
import org.jetbrains.ruby.runtime.signature.server.serialisation.RSignatureBuilder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;


public class SignatureServer implements RSignatureStorage {
    private static final Logger LOGGER = Logger.getLogger("SignatureServer");

    RSignatureContractContainer mainContainer = new RSignatureContractContainer();
    RSignatureContractContainer newSignaturesContainer = new RSignatureContractContainer();

    @Nullable
    private static SignatureServer ourInstance;

    @NotNull
    public static SignatureServer getInstance() {
        if (ourInstance == null) {
            ourInstance = new SignatureServer();
        }

        return ourInstance;
    }

    @Nullable
    public RSignatureContract getContract(@NotNull MethodInfo info) {
        return mainContainer.getSignature(info);
    }

    @Nullable
    public RSignatureContract getContractByMethodName(@NotNull String methodName) {

        for (MethodInfo info : mainContainer.getKeySet()) {
            if (info.getName().equals(methodName)) {
                return mainContainer.getSignature(info);
            }
        }
        return null;
    }

    @Nullable
    public RSignatureContract getContractByMethodAndReceiverName(@NotNull String methodName, @NotNull String receiverName) {

        for (MethodInfo info : mainContainer.getKeySet()) {
            if (info.getName().equals(methodName) && info.getClassInfo().getClassFQN().equals(receiverName))
                return mainContainer.getSignature(info);
        }
        return null;
    }

    @Nullable
    public MethodInfo getMethodByClass(@NotNull String className, @NotNull String methodName) {
        for (MethodInfo info : mainContainer.getKeySet()) {
            if (info.getName().equals(methodName) && info.getClassInfo().getClassFQN().equals(className))
                return info;
        }
        return null;
    }

    @Override
    public void addSignature(@NotNull RSignature signature) throws StorageException {

    }

    @Override
    public void readPacket(@NotNull Packet packet) throws StorageException {
        if (packet instanceof TestPacketImpl) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(((TestPacketImpl) packet).getData());
            DataInput in = new DataInputStream(inputStream);

            MethodInfo info = MethodInfoSerializationKt.MethodInfo(in);
            SignatureContract contract = SignatureContractSerializationKt.SignatureContract(in);

            if (contract instanceof RSignatureContract)
                SignatureServer.getInstance().mainContainer.addContract(info, (RSignatureContract) contract);
        }
    }

    @Override
    public @NotNull Collection<Packet> formPackets() throws StorageException {

        List<Packet> packets = new ArrayList<>();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(outputStream);

        int size = 0;

        for (MethodInfo info : SignatureServer.getInstance().newSignaturesContainer.getKeySet()) {
            RSignatureContract contract = SignatureServer.getInstance().newSignaturesContainer.getSignature(info);


            MethodInfoSerializationKt.serialize(info, out);
            SignatureContractSerializationKt.serialize(contract, out);
            size++;
        }
        packets.add(new TestPacketImpl(outputStream.toByteArray(), size));

        return packets;
    }


    private static class SignatureHandler extends Thread {
        private Socket socket;
        private int handlerNumber;

        SignatureHandler(Socket socket, int handlerNumber) {
            this.socket = socket;
            this.handlerNumber = handlerNumber;
            LOGGER.info("New connection with client# " + handlerNumber + " at " + socket);
        }

        public void run() {
            try {

                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String currString;


                while ((currString = br.readLine()) != null) {
                    try {
                        RSignature currRSignature = RSignatureBuilder.fromJson(currString);

                        if (currRSignature != null
                                && !SignatureServer.getInstance().mainContainer.acceptSignature(currRSignature)) {
                            SignatureServer.getInstance().newSignaturesContainer.addSignature(currRSignature);
                        }

                    } catch (JsonParseException e) {
                        LOGGER.severe("!" + currString + "!\n" + e);
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                LOGGER.severe("Error handling client# " + handlerNumber + ": " + e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    LOGGER.severe("Can't close a socket");
                }
                LOGGER.info("Connection with client# " + handlerNumber + " closed");
                SignatureServer.getInstance().mainContainer.reduce();
                try {
                    Collection<Packet> packets = SignatureServer.getInstance().formPackets();
                    for (Packet packet : packets) {
                        LocalBucket.INSTANCE.readPacket(packet);
                    }
                } catch (Exception ex) {
                    LOGGER.severe(ex.getMessage());
                }
            }
        }

    }

    public void runServer() throws IOException {
        LOGGER.info("Starting server");

        int handlersCounter = 0;

        Collection<Packet> packets = LocalBucket.INSTANCE.formPackets();

        for (Packet packet : packets) {
            if (packet instanceof TestPacketImpl) {
                byte[] packetData = ((TestPacketImpl) packet).getData();

                if (((TestPacketImpl) packet).size > 0) {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(packetData);
                    DataInput in = new DataInputStream(inputStream);

                    MethodInfo methodInfo = MethodInfoSerializationKt.MethodInfo(in);
                    SignatureContract contract = SignatureContractSerializationKt.SignatureContract(in);
                    if (contract instanceof RSignatureContract)
                        SignatureServer.getInstance().mainContainer.addContract(methodInfo, (RSignatureContract) contract);
                }
            }
        }


        try (ServerSocket listener = new ServerSocket(7777)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                new SignatureHandler(listener.accept(), handlersCounter++).start();
            }
        } finally {
            SignatureServer.getInstance().mainContainer.reduce();
        }
    }

    public static void main(String[] args) throws IOException {
        getInstance().runServer();
    }
}
