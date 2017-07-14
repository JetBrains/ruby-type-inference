package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

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
                    contracts.get(key).locked = true;
                    contracts.get(key).minimization();
                    contracts.get(key).locked = false;
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

            contract.locked = true;
            if (signature.getArgsInfo().size() == contract.getArgsInfo().size())
                contracts.get(currInfo).addRSignature(signature);
            else {
                //System.out.println("Collision");
            }
            contract.locked = false;

        } else {
            RSignatureContract contract = new RSignatureContract(signature);
            contract.locked = true;
            contracts.put(currInfo, contract);
            contract.locked = false;
        }
    }

    public Set<MethodInfo> getKeySet() {
        return contracts.keySet();
    }

    public RSignatureContract getSignature(MethodInfo info) {
        return contracts.get(info);
    }
}