package org.jetbrains.ruby.codeInsight.types.signature;

import java.util.*;

public class RSignatureContractContainer {

    private Map<MethodInfo, RSignatureContract> contracts;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
    }

    public void reduction()
    {
        contracts.entrySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> entry.getValue().getNumberOfCalls())))
                .forEachOrdered(entry -> {
                    final MethodInfo key = entry.getKey();
                    contracts.get(key).minimization();
                    contracts.get(key).compression();
                });
        System.out.println("Finished");
    }

    public void addSignature(RSignature signature) {
        MethodInfo currInfo = signature.getMethodInfo();

        if (contracts.containsKey(currInfo)) {
            contracts.get(currInfo).addRSignature(signature);
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