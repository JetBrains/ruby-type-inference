package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.ruby.codeInsight.types.signature.*

class DiffPreservingStorage<T : RSignatureStorage.Packet>(
        private val receivedDataStorage: RSignatureStorage<T>,
        private val localDataStorage: RSignatureStorage<T>
) : RSignatureStorage<T> {
    override fun readPacket(packet: T) {
        receivedDataStorage.readPacket(packet)
        localDataStorage.readPacket(packet)

        packet.signatures.forEach { signatureInfo ->
            val methodInfo = signatureInfo.methodInfo

            val localContract = localDataStorage.getSignature(methodInfo)
                    ?: throw AssertionError("contract should be present after reading packets; " +
                    "methodInfo=$methodInfo")

            // local contracts are already merged at this point
            if (localContract.contract == signatureInfo.contract) {
                localDataStorage.deleteSignature(methodInfo)
            }
        }
    }

    override fun formPackets(descriptor: RSignatureStorage.ExportDescriptor?): MutableCollection<T> {
        return localDataStorage.formPackets(descriptor)
    }

    override fun getRegisteredGems(): Collection<GemInfo> {
        return HashSet<GemInfo>().apply {
            addAll(receivedDataStorage.registeredGems)
            addAll(localDataStorage.registeredGems)
        }
    }

    override fun getClosestRegisteredGem(usedGem: GemInfo): GemInfo? {
        TODO("not implemented: unclear what to do when local gem version differs" +
                "MAYBE this method should be eliminated and replaced by internal 'closest gem' auto-cache")
    }

    // TODO cache location
    override fun getRegisteredClasses(gem: GemInfo): MutableCollection<ClassInfo> {
        return HashSet<ClassInfo>().apply {
            addAll(receivedDataStorage.getRegisteredClasses(gem))
            addAll(localDataStorage.getRegisteredClasses(gem))
        }
    }

    override fun getAllClassesWithFQN(fqn: String): MutableCollection<ClassInfo> {
        return HashSet<ClassInfo>().apply {
            addAll(receivedDataStorage.getAllClassesWithFQN(fqn))
            addAll(localDataStorage.getAllClassesWithFQN(fqn))
        }
    }

    override fun getRegisteredMethods(containerClass: ClassInfo): MutableCollection<MethodInfo> {
        return HashSet<MethodInfo>().apply {
            addAll(receivedDataStorage.getRegisteredMethods(containerClass))
            addAll(localDataStorage.getRegisteredMethods(containerClass))
        }
    }

    override fun getSignature(method: MethodInfo): SignatureInfo? {
        return localDataStorage.getSignature(method)
                ?: receivedDataStorage.getSignature(method)
    }

    override fun getRegisteredCallInfos(methodInfo: MethodInfo): MutableCollection<CallInfo> {
        return HashSet<CallInfo>().apply {
            addAll(localDataStorage.getRegisteredCallInfos(methodInfo))
            addAll(receivedDataStorage.getRegisteredCallInfos(methodInfo))
        }
    }

    override fun deleteSignature(method: MethodInfo) {
        localDataStorage.deleteSignature(method)
    }

    override fun putSignature(signatureInfo: SignatureInfo) {
        localDataStorage.putSignature(signatureInfo)
    }
}