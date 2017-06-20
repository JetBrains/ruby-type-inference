package org.jetbrains.ruby.runtime.signature.server;

import org.jetbrains.ruby.codeInsight.types.storage.server.RSignatureStorage;

class TestPacketImpl implements RSignatureStorage.Packet {
    private byte[] data;
    int size;

    TestPacketImpl(byte[] data, int size) {
        this.data = data;
        this.size = size;
    }

    byte[] getData() {
        return data;
    }
}
