package org.jetbrains.ruby.runtime.signature;

import java.util.*;

public class RSignatureContractContainer {
    private Map<RMethodInfo, RSignatureContract> contracts;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
    }


    public void print()
    {
        contracts.entrySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> entry.getValue().getCounter())))
                .forEachOrdered(entry -> {
                    final RMethodInfo key = entry.getKey();
                    final RSignatureContract value = entry.getValue();
                    System.out.println("-----------");
                    System.out.println("Number of calls:" + value.getCounter());
                    System.out.println(key.toString());
                    System.out.println(key.getPath());
                    System.out.println(key.getLine());
                    contracts.get(key).minimization();
                    List<String> presentation = value.getStringPresentation();
                    if (presentation.size() > 1) {
                        System.out.println("OurClient");
                    }
                    for (String s : presentation) {
                        System.out.println(s);
                    }
                });
    }

    public void addSignature(RSignature signature){
        RMethodInfo currInfo = new RMethodInfo(signature);

        if(contracts.containsKey(currInfo)){
            contracts.get(currInfo).addRSignature(signature);
        }
        else{
            RSignatureContract contract = new RSignatureContract(signature);
            contracts.put(currInfo, contract);
        }
    }
}
