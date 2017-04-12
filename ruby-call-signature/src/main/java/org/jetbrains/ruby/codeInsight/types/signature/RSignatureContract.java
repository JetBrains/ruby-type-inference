package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

        if (index < getSize())
            newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.argNode);
        else
            newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.returnNode);

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

    public RSignatureContract(RSignature signature) {
        this.levels = new ArrayList<>();
        this.termNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.returnTypeNode);
        this.myArgsInfo = signature.getArgsInfo();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        this.addRSignature(signature);
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

    public void addRSignature(RSignature signature) {
        myNumberOfCalls++;
        RSignatureContractNode currNode = this.startContractNode;

        String returnType = signature.getReturnTypeName();

        RSignatureContractNode termNode = getTermNode();

        final List<String> argsTypes = signature.getArgsTypes();
        for (int argIndex = 0; argIndex < argsTypes.size(); argIndex++) {
            final String type = argsTypes.get(argIndex);

            final int mask = getNewMask(signature.getArgsTypes(), argIndex, type);

            ContractTransition transition = null;

            if (mask > 0)
                transition = new ReferenceContractTransition(mask);
            else
                transition = new TypedContractTransition(type);

            if (currNode.goByTransition(transition) == null) {
                final RSignatureContractNode newNode = createNodeAndAddToLevels(argIndex + 1);

                currNode.addLink(transition, newNode);

                currNode = newNode;
            } else {
                currNode = currNode.goByTransition(transition);
            }
        }

        final int mask = getNewMask(signature.getArgsTypes(), signature.getArgsTypes().size(), returnType);
        if (mask > 0) {
            currNode.addLink(new ReferenceContractTransition(mask), termNode);
        } else {
            currNode.addLink(new TypedContractTransition(returnType), termNode);
        }
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

    public void minimization() {
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

                        if (vertex1.goByTransition(transition) != vertex2.goByTransition(transition)) {
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
            for (RSignatureContractNode node : uselessVertices) {
                this.levels.get(i).remove(node);
            }
        }
    }


    public List<List<RSignatureContractNode>> getLevels() {
        return levels;
    }

    public int getNumberOfCalls() {
        return myNumberOfCalls;
    }
}