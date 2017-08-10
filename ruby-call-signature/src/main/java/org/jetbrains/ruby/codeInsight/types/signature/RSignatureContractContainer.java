package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Logger;

public class RSignatureContractContainer {
    private static final Logger LOGGER = Logger.getLogger(RSignatureContractContainer.class.getName());

    @NotNull
    private final Map<MethodInfo, RSignatureContract> myContracts;
    @NotNull
    private final Map<MethodInfo, Integer> myNumberOfCalls;

    public RSignatureContractContainer() {
        myContracts = new HashMap<>();
        myNumberOfCalls = new HashMap<>();
    }

    public void reduce()
    {
        myContracts.entrySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> myNumberOfCalls.get(entry.getKey()))))
                .forEachOrdered(entry -> {
                    final MethodInfo key = entry.getKey();
                    final RSignatureContract contract = myContracts.get(key);
                    synchronized (contract) {
                        contract.minimize();
                    }
                });
        LOGGER.fine("Finished");
    }

    public void addContract(@NotNull MethodInfo info, @NotNull RSignatureContract contract) {
        myContracts.put(info, contract);
    }

    public boolean acceptSignature(@NotNull RSignature signature) {
        MethodInfo currInfo = signature.getMethodInfo();

        if (myContracts.containsKey(currInfo)) {
            RSignatureContract contract = myContracts.get(currInfo);

            return signature.getArgsInfo().equals(contract.getArgsInfo()) && myContracts.get(currInfo).accept(signature);
        } else {
            return false;
        }
    }

    public void addSignature(@NotNull RSignature signature) {
        MethodInfo currInfo = signature.getMethodInfo();

        if (myContracts.containsKey(currInfo)) {
            RSignatureContract contract = myContracts.get(currInfo);

            synchronized (contract) {
                if (signature.getArgsInfo().size() == contract.getArgsInfo().size()) {
                    contract.addRSignature(signature);
                    myNumberOfCalls.compute(currInfo, (method, oldNumber) -> oldNumber != null ? oldNumber + 1 : 1);
                }
            }

        } else {
            RSignatureContract contract = new RSignatureContract(signature);
            myContracts.put(currInfo, contract);
        }
    }

    public Set<MethodInfo> getKeySet() {
        return myContracts.keySet();
    }

    public RSignatureContract getSignature(MethodInfo info) {
        return myContracts.get(info);
    }
}