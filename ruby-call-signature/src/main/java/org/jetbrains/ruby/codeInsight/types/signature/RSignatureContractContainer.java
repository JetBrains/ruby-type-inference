package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RSignatureContractContainer {

    private final Map<MethodInfo, RSignatureContract> contracts;

    private final Map<MethodInfo, Integer> myNumberOfCalls;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
        myNumberOfCalls = new HashMap<>();
    }

    public void reduce()
    {
        contracts.entrySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> myNumberOfCalls.get(entry.getKey()))))
                .forEachOrdered(entry -> {
                    final MethodInfo key = entry.getKey();
                    final RSignatureContract contract = contracts.get(key);
                    synchronized (contract) {
                        contract.minimize();
                    }
                });
        System.out.println("Finished");
    }

    public void addContract(@NotNull MethodInfo info, @NotNull RSignatureContract contract) {
        contracts.put(info, contract);
    }

    public boolean acceptSignature(@NotNull RSignature signature) {
        MethodInfo currInfo = signature.getMethodInfo();

        if (contracts.containsKey(currInfo)) {
            RSignatureContract contract = contracts.get(currInfo);

            return signature.getArgsInfo().equals(contract.getArgsInfo()) && contracts.get(currInfo).accept(signature);
        } else {
            return false;
        }
    }

    public void addSignature(@NotNull RSignature signature) {
        MethodInfo currInfo = signature.getMethodInfo();

        if (contracts.containsKey(currInfo)) {
            RSignatureContract contract = contracts.get(currInfo);

            synchronized (contract) {
                if (signature.getArgsInfo().size() == contract.getArgsInfo().size()) {
                    contract.addRSignature(signature);
                    myNumberOfCalls.compute(currInfo, (method, oldNumber) -> oldNumber != null ? oldNumber + 1 : 1);
                }
            }

        } else {
            RSignatureContract contract = new RSignatureContract(signature);
            contracts.put(currInfo, contract);
        }
    }

    public Set<MethodInfo> getKeySet() {
        return contracts.keySet();
    }

    public RSignatureContract getSignature(MethodInfo info) {
        return contracts.get(info);
    }
}