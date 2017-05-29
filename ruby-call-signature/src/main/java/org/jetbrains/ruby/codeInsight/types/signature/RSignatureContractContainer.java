package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class RSignatureContractContainer {

    private Map<MethodInfo, RSignatureContract> contracts;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
    }

    private int dfs(RSignatureContractNode node, Set<RSignatureContractNode> used, int ans) {
        int answer = ans;
        if (!used.contains(node))
            answer++;
        used.add(node);
        for (ContractTransition transition : node.getTransitionKeys()) {
            answer = dfs(node.goByTransition(transition), used, answer);
        }
        return answer;
    }

    public void printInfo() {
        try {
            File file = new File("/home/viuginick/Soft/ruby-type-inference/ide-plugin/resources/log.txt");
            PrintWriter writer = new PrintWriter(file);

            contracts.entrySet().stream()
                    .sorted(Collections.reverseOrder(Comparator.comparingInt(entry -> entry.getValue().getNumberOfCalls())))
                    .forEachOrdered(entry -> {
                        final MethodInfo key = entry.getKey();
                        Set used = new HashSet<RSignatureContractNode>();
                        //railties 5.0.1 & Rails::Initializable::Initializer & initialize & 22 & 7 \\
                        //writer.println(key.getName() + "(" + entry.getValue().getNumberOfCalls() + ")");
                        //writer.println(key.getClassInfo().getClassFQN());
                        writer.println(key.getClassInfo().getGemInfo().getName() + " " +
                                key.getClassInfo().getGemInfo().getVersion() + " & " +
                                key.getClassInfo().getClassFQN() + " & " +
                                key.getName() + " & " + entry.getValue().getNumberOfCalls() + " & " + dfs(contracts.get(key).getStartNode(), used, 0)
                        );
                        //"(" + key.getLocation().getPath() + key.getLocation().getLineno() + ")"
                        //writer.println(dfs(contracts.get(key).getStartNode(), used, 0));

                    });

        } catch (IOException e) {
            // do something
        }
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

    public void merge(RSignatureContractContainer additive) {

        for (MethodInfo methodInfo : additive.getKeySet()) {
            if (getKeySet().contains(methodInfo)) {
                contracts.get(methodInfo).merge(additive.getSignature(methodInfo));
            } else {
                addContract(methodInfo, additive.getSignature(methodInfo));
            }
        }
    }

    public Set<MethodInfo> getKeySet() {
        return contracts.keySet();
    }

    public RSignatureContract getSignature(MethodInfo info) {
        return contracts.get(info);
    }
}