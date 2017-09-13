package org.jetbrains.ruby.codeInsight.types.storage.server

import org.jetbrains.ruby.codeInsight.types.signature.ClassInfo
import org.jetbrains.ruby.codeInsight.types.signature.GemInfo
import org.jetbrains.ruby.codeInsight.types.signature.MethodInfo
import org.jetbrains.ruby.codeInsight.types.signature.SignatureInfo

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

    override fun formPackets(): MutableCollection<T> {
        return localDataStorage.formPackets()
    }

    override fun getRegisteredGems(): Collection<GemInfo> {
        return HashSet<GemInfo>().let {
            it.addAll(receivedDataStorage.registeredGems)
            it.addAll(localDataStorage.registeredGems)
            it
        }
    }

    override fun getClosestRegisteredGem(usedGem: GemInfo): GemInfo? {
        TODO("not implemented: unclear what to do when local gem version differs" +
                "MAYBE this method should be eliminated and replaced by internal 'closest gem' auto-cache")
    }

    // TODO cache location
    override fun getRegisteredClasses(gem: GemInfo): MutableCollection<ClassInfo> {
        return HashSet<ClassInfo>().let {
            it.addAll(receivedDataStorage.getRegisteredClasses(gem))
            it.addAll(localDataStorage.getRegisteredClasses(gem))
            it
        }
    }

    override fun getAllClassesWithFQN(fqn: String): MutableCollection<ClassInfo> {
        return HashSet<ClassInfo>().let {
            it.addAll(receivedDataStorage.getAllClassesWithFQN(fqn))
            it.addAll(localDataStorage.getAllClassesWithFQN(fqn))
            it
        }
    }

    override fun getRegisteredMethods(containerClass: ClassInfo): MutableCollection<MethodInfo> {
        return HashSet<MethodInfo>().let {
            it.addAll(receivedDataStorage.getRegisteredMethods(containerClass))
            it.addAll(localDataStorage.getRegisteredMethods(containerClass))
            it
        }
    }

    override fun getSignature(method: MethodInfo): SignatureInfo? {
        return localDataStorage.getSignature(method)
                ?: receivedDataStorage.getSignature(method)
    }

    override fun deleteSignature(method: MethodInfo) {
        localDataStorage.deleteSignature(method)
    }

    override fun putSignature(signatureInfo: SignatureInfo) {
        localDataStorage.putSignature(signatureInfo)
    }
}