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

    public boolean acceptTuple(@NotNull RTuple tuple) {
        MethodInfo currInfo = tuple.getMethodInfo();

        if (myContracts.containsKey(currInfo)) {
            RSignatureContract contract = myContracts.get(currInfo);

            return tuple.getArgsInfo().equals(contract.getArgsInfo()) && myContracts.get(currInfo).accept(tuple);
        } else {
            return false;
        }
    }

    public void addTuple(@NotNull RTuple tuple) {
        MethodInfo currInfo = tuple.getMethodInfo();

        if (myContracts.containsKey(currInfo)) {
            RSignatureContract contract = myContracts.get(currInfo);

            synchronized (contract) {
                if (tuple.getArgsInfo().size() == contract.getArgsInfo().size()) {
                    contract.addRTuple(tuple);
                    myNumberOfCalls.compute(currInfo, (method, oldNumber) -> oldNumber != null ? oldNumber + 1 : 1);
                }
            }

        } else {
            RSignatureContract contract = new RSignatureContract(tuple);
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