package org.jetbrains.ruby.codeInsight.types.storage.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;

import java.util.Collection;

public interface RSignatureStorage<T extends RSignatureStorage.Packet> extends RSignatureProvider {

    default void readPacket(@NotNull T packet) throws StorageException {
        for (final SignatureInfo signatureInfo : packet.getSignatures()) {
            final MethodInfo methodInfo = signatureInfo.getMethodInfo();
            final SignatureInfo oldSignature = getSignature(methodInfo);

            RSignatureContract contract;
            if (oldSignature != null &&
                    (contract = RSignatureContract.mergeMutably(oldSignature.getContract(), signatureInfo.getContract())) != null) {
                putSignature(SignatureInfoKt.SignatureInfo(methodInfo, contract));
            } else {
                putSignature(signatureInfo);
            }
        }
    }

    @NotNull
    Collection<T> formPackets(@Nullable ExportDescriptor descriptor) throws StorageException;

    class ExportDescriptor {
        private final boolean myInclude;

        @NotNull
        private final Collection<GemInfo> myGemsToIncludeOrExclude;

        public ExportDescriptor(boolean include, @NotNull Collection<GemInfo> gemsToIncludeOrExclude) {
            myInclude = include;
            myGemsToIncludeOrExclude = gemsToIncludeOrExclude;
        }

        public boolean isInclude() {
            return myInclude;
        }

        @NotNull
        public Collection<GemInfo> getGemsToIncludeOrExclude() {
            return myGemsToIncludeOrExclude;
        }
    }

    interface Packet {
        Collection<SignatureInfo> getSignatures();
    }
}
