package org.jetbrains.ruby.runtime.signature.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo;
import org.jetbrains.ruby.codeInsight.types.signature.RSignature;
import org.jetbrains.ruby.codeInsight.types.signature.SignatureContract;
import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage;
import org.jetbrains.ruby.codeInsight.types.storage.server.StorageException;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoSerializationKt;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContractData;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureContractSerializationKt;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

public class LocalBucket implements RSignatureStorage {
    private static final Logger LOGGER = Logger.getLogger("LocalBucket");
    private static SignatureDBProvider provider = new SignatureDBProvider();

    @Nullable
    private static LocalBucket ourInstance;

    @NotNull
    static LocalBucket getInstance() {
        if (ourInstance == null) {
            ourInstance = new LocalBucket();
            provider.connectToDB();
        }

        return ourInstance;
    }

    @Override
    public void addSignature(@NotNull RSignature signature) throws StorageException {

    }

    @Override
    public void readPacket(@NotNull RSignatureStorage.Packet packet) throws StorageException {
        if (packet instanceof TestPacketImpl) {

            ByteArrayInputStream inputStream = new ByteArrayInputStream(((TestPacketImpl) packet).getData());
            DataInput in = new DataInputStream(inputStream);

            for (int i = 0; i < ((TestPacketImpl) packet).size; i++) {
                MethodInfo info = MethodInfoSerializationKt.MethodInfo(in);
                SignatureContract contract = SignatureContractSerializationKt.SignatureContract(in);

                provider.putSignatureContract(info, contract);
                //SignatureContract oldContract = provider.getSignatureContract(info);
            }
        }
    }

    @Override
    public @NotNull Collection<Packet> formPackets() throws StorageException {
        Collection<SignatureContractData> contractData = provider.getRegisteredContractsWithInfos();
        List<Packet> packets = new ArrayList<>();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(outputStream);

        for (SignatureContractData data : contractData) {
            MethodInfo info = data.getMethodInfo();
            SignatureContract contract = data.getContract();

            MethodInfoSerializationKt.serialize(info, out);
            SignatureContractSerializationKt.serialize(contract, out);
        }
        packets.add(new TestPacketImpl(outputStream.toByteArray(), contractData.size()));
        return packets;
    }

}

