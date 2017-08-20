package org.jetbrains.ruby.codeInsight.types.storage.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo;
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfoKt;

import java.util.Collection;

public interface RSignatureStorage<T extends RSignatureStorage.Packet> extends RSignatureProvider {

    default void readPacket(@NotNull T packet) throws StorageException {
        for (final SignatureInfo signatureInfo : packet.getSignatures()) {
            final MethodInfo methodInfo = signatureInfo.getMethodInfo();
            final SignatureInfo oldSignature = getSignature(methodInfo);

            putSignature(oldSignature == null ? signatureInfo : SignatureInfoKt.SignatureInfo(
                    methodInfo,
                    RSignatureContract.mergeMutably(oldSignature.getContract(), signatureInfo.getContract())
            ));
        }
    }

    @NotNull
    Collection<T> formPackets() throws StorageException;

    interface Packet {
        Collection<SignatureInfo> getSignatures();
    }
}
