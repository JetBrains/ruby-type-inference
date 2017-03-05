package org.jetbrains.ruby.codeInsight.types.signature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class RSignatureContractContainer {
    private Map<MethodInfo, RSignatureContract> contracts;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
    }


    public void print(Path file)
    {
        contracts.entrySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> entry.getValue().getCounter())))
                .forEachOrdered(entry -> {
                    final MethodInfo key = entry.getKey();
                    final RSignatureContract value = entry.getValue();

                    List<String> lines = new ArrayList<>();
                    lines.add("-----------");
                    lines.add("Number of calls:" + value.getCounter());
                    lines.add(key.toString());
                    lines.add("Mask:" + Integer.toString(value.getStartNode().getMask(), 2));

                    contracts.get(key).minimization();
                    lines.addAll(value.getStringPresentation());

                    try {
                        Files.write(file, lines, StandardOpenOption.APPEND);
                    } catch (IOException e)
                    {
                        System.out.println("IOException");
                    }

                });
        System.out.println("Finished");
    }

    public void addSignature(RSignature signature){
        MethodInfo currInfo = signature.getMethodInfo();

        if(contracts.containsKey(currInfo)){
            contracts.get(currInfo).addRSignature(signature);
        }
        else{
            RSignatureContract contract = new RSignatureContract(signature);
            contracts.put(currInfo, contract);
        }
    }
}