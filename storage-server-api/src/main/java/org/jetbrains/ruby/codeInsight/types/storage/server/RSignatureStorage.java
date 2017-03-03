package org.jetbrains.ruby.codeInsight.types.storage.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.RSignature;

import java.util.Collection;

/**
 * The data obtained when running ruby scripts may be registered to be used for code insight
 * via {@link #addSignature(RSignature)}.
 */
public interface RSignatureStorage {

    void addSignature(@NotNull RSignature signature) throws StorageException;

    void readPacket(@NotNull Packet packet) throws StorageException;

    @NotNull
    Collection<Packet> formPackets() throws StorageException;

    interface Packet {
    }
}
