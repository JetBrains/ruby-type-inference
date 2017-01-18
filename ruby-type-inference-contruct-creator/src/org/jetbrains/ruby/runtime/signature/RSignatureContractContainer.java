package org.jetbrains.ruby.runtime.signature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class RSignatureContractContainer {
    private Map<RMethodInfo, RSignatureContract> contracts;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
    }


    public void print(Path file)
    {
        contracts.entrySet().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> entry.getValue().getCounter())))
                .forEachOrdered(entry -> {
                    final RMethodInfo key = entry.getKey();
                    final RSignatureContract value = entry.getValue();

                    List<String> lines = new ArrayList();
                    lines.add("-----------");
                    lines.add("Number of calls:" + value.getCounter());
                    lines.add(key.toString());
                    lines.add(key.getPath());
                    lines.add(Integer.toString(key.getLine()));
                    contracts.get(key).minimization();
                    lines.addAll(value.getStringPresentation());

                    if(lines.size() > 9)
                    {
                        lines.add("OurClient");
                    }

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
