package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;

import java.util.*;

public class RSignatureContract implements SignatureContract {

    private int myNumberOfCalls = 0;

    public boolean locked = false;

    @NotNull
    private final RSignatureContractNode startContractNode;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;

    private final List<List<RSignatureContractNode>> levels;
    private final RSignatureContractNode termNode;

    private RSignatureContractNode createNodeAndAddToLevels(Integer index) {
        RSignatureContractNode newNode;

        newNode = new RSignatureContractNode();

        while (levels.size() <= index)
            levels.add(new ArrayList<>());
        levels.get(index).add(newNode);
        return newNode;
    }

    public List<ParameterInfo> getParamInfoList() {
        return myArgsInfo;
    }

    private RSignatureContractNode getTermNode() {
        return termNode;
    }

    public RSignatureContract(SignatureContract contract) {
        levels = new ArrayList<>();
        myArgsInfo = contract.getArgsInfo();
        startContractNode = new RSignatureContractNode(contract.getStartNode());

        Set<RSignatureContractNode> used = new HashSet<>();

        for (int i = 0; i < myArgsInfo.size() + 2; i++)
            levels.add(new ArrayList<>());

        levels.get(0).add(startContractNode);
        int level = 0;

        for (int i = 0; i <= myArgsInfo.size(); i++, level++) {
            for (RSignatureContractNode contractNode : levels.get(level)) {
                for (ContractTransition transition : contractNode.getTransitionKeys()) {
                    RSignatureContractNode node = contractNode.goByTransition(transition);

                    if (used.add(node)) {
                        levels.get(level + 1).add(node);
                    }
                }
            }
        }
        termNode = levels.get(levels.size() - 1).get(0);
    }

    RSignatureContract(RSignature signature) {
        myArgsInfo = signature.getArgsInfo();
        levels = new ArrayList<>();
        for (int i = 0; i < getArgsInfo().size() + 2; i++) {
            levels.add(new ArrayList<>());
        }

        termNode = this.createNodeAndAddToLevels(0);

        startContractNode = this.createNodeAndAddToLevels(levels.size() - 1);

        addRSignature(signature);
    }

    public RSignatureContract(@NotNull List<ParameterInfo> argsInfo,
                              @NotNull RSignatureContractNode startContractNode,
                              @NotNull RSignatureContractNode termNode,
                              @NotNull List<List<RSignatureContractNode>> levels) {
        this.startContractNode = startContractNode;
        myArgsInfo = argsInfo;
        this.levels = levels;
        this.termNode = termNode;

        // TODO recalculate mask
    }

    @NotNull
    @Override
    public RSignatureContractNode getStartNode() {
        return startContractNode;
    }

    @NotNull
    @Override
    public List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    private int getSize() {
        return myArgsInfo.size();
    }

    public int getNodeCount() {
        return levels.stream().map(List::size).reduce(0, (a, b) -> a + b);
    }

    @NotNull
    private ContractTransition calculateTransition(@NotNull List<String> argTypes, int argIndex, String type) {
        final int mask = getNewMask(argTypes, argIndex, type);

        if (mask > 0)
            return new ReferenceContractTransition(mask);
        else
            return new TypedContractTransition(type);
    }

    void addRSignature(RSignature signature) {
        myNumberOfCalls++;
        RSignatureContractNode currNode = startContractNode;

        String returnType = signature.getReturnTypeName();

        RSignatureContractNode termNode = getTermNode();

        final List<String> argsTypes = signature.getArgsTypes();
        for (int argIndex = 0; argIndex < argsTypes.size(); argIndex++) {
            final String type = argsTypes.get(argIndex);

            final ContractTransition transition = calculateTransition(signature.getArgsTypes(), argIndex, type);

            if (!currNode.containsKey(transition)) {
                final RSignatureContractNode newNode = createNodeAndAddToLevels(argIndex + 1);

                currNode.addLink(transition, newNode);

                currNode = newNode;
            } else {
                currNode = currNode.goByTransition(transition);
            }
        }

        final ContractTransition transition = calculateTransition(signature.getArgsTypes(), signature.getArgsTypes().size(), returnType);

        currNode.addLink(transition, termNode);
    }

    private int getNewMask(@NotNull List<String> argsTypes, int argIndex, @NotNull String type) {
        int tempMask = 0;

        for (int i = argIndex - 1; i >= 0; i--) {
            tempMask <<= 1;

            if (argsTypes.get(i).equals(type)) {
                tempMask |= 1;
            }
        }

        return tempMask;
    }

    void minimization() {
        int numberOfLevels = levels.size();

        for (int i = numberOfLevels - 1; i > 0; i--) {
            List<RSignatureContractNode> level = levels.get(i);

            HashMap<RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            List<RSignatureContractNode> uselessVertices = new ArrayList<>();

            for (RSignatureContractNode node : level) {
                representatives.put(node, node);
            }

            for (int v1 = 0; v1 < level.size(); v1++) {
                for (int v2 = v1 + 1; v2 < level.size(); v2++) {
                    RSignatureContractNode vertex1 = level.get(v1);
                    RSignatureContractNode vertex2 = level.get(v2);

                    boolean isSame = vertex1.getTransitions().size() == vertex2.getTransitions().size();

                    for (ContractTransition transition : vertex1.getTransitionKeys()) {

                        if (!vertex2.containsKey(transition) || vertex1.goByTransition(transition) != vertex2.goByTransition(transition)) {
                            isSame = false;
                        }
                    }

                    if (isSame) {
                        RSignatureContractNode vertex1presenter = representatives.get(vertex1);
                        representatives.put(vertex2, vertex1presenter);
                        uselessVertices.add(vertex2);
                    }
                }
            }

            List<RSignatureContractNode> prevLevel = levels.get(i - 1);


            if (uselessVertices.size() > 0) {
                for (RSignatureContractNode node : prevLevel) {
                    for (ContractTransition transition : node.getTransitionKeys()) {
                        RSignatureContractNode child = node.goByTransition(transition);
                        node.addLink(transition, representatives.get(child));
                    }
                }
            }

            level.removeAll(uselessVertices);
        }
    }


    public List<List<RSignatureContractNode>> getLevels() {
        return levels;
    }

    public int getNumberOfCalls() {
        return myNumberOfCalls;
    }

    private void mergeDFS(RSignatureContractNode node1, SignatureNode node2) {
        //TODO
        //        for (ContractTransition transition : node2.getTransitionKeys()) {
//            if (node1.getTransitionKeys().contains(transition)) {
//                mergeDFS(node1.goByTransition(transition), node2.goByTransition(transition));
//            } else {
//                node1.addLink(transition, node2.goByTransition(transition));
//            }
//        }
    }

    public void merge(SignatureContract additive) {
        mergeDFS(startContractNode, additive.getStartNode());
        minimization();
    }

    boolean accept(@NotNull RSignature signature) {
        RSignatureContractNode currNode = startContractNode;

        String returnType = signature.getReturnTypeName();

        final List<String> argsTypes = signature.getArgsTypes();
        for (int argIndex = 0; argIndex < argsTypes.size(); argIndex++) {
            final String type = argsTypes.get(argIndex);

            final ContractTransition transition = calculateTransition(signature.getArgsTypes(), argIndex, type);

            if (!currNode.containsKey(transition)) {
                return false;
            } else {
                currNode = currNode.goByTransition(transition);
            }
        }

        final ContractTransition transition = calculateTransition(signature.getArgsTypes(), signature.getArgsTypes().size(), returnType);

        return currNode.containsKey(transition);
    }
}