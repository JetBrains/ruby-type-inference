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
        this.startContractNode = this.createNodeAndAddToLevels(0);
        this.myArgsInfo = signature.getArgsInfo();

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

            final int mask = getNewMask(signature.getArgsTypes(), argIndex, returnType, type);
            final TypedContractTransition transition = new TypedContractTransition(type);

            if (currNode.goByTypeSymbol(transition) == null) {
                final RSignatureContractNode newNode = createNodeAndAddToLevels(argIndex + 1);

                currNode.addLink(transition, newNode);

                newNode.setMask(mask);
                currNode = newNode;
            } else {
                currNode = currNode.goByTypeSymbol(transition);
                currNode.updateMask(mask);
            }
        }

        currNode.addLink(new TypedContractTransition(returnType), termNode);
    }

    private int getNewMask(@NotNull List<String> argsTypes, int argIndex, String returnType, String addedType) {
        int tempMask = 0;
        for (int j = argIndex + 1; j < argsTypes.size(); j++) {
            tempMask <<= 1;
            if (argsTypes.get(j).equals(addedType)) {
                tempMask |= 1;
            }
        }

        if (returnType.equals(addedType)) {
            tempMask <<= 1;
            tempMask |= 1;
        }
        return tempMask;
    }

    public void minimization() {
        int numberOfLevels = levels.size();

        for (int i = numberOfLevels - 1; i > 0; i--) {
            List<RSignatureContractNode> level = levels.get(i);

            HashMap<RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            List<Integer> uselessVertices = new ArrayList<>();

            for (RSignatureContractNode node : level) {
                representatives.put(node, node);
            }

            for (int v1 = 0; v1 < level.size(); v1++) {
                for (int v2 = v1 + 1; v2 < level.size(); v2++) {
                    RSignatureContractNode vertex1 = level.get(v1);
                    RSignatureContractNode vertex2 = level.get(v2);

                    boolean isSame = vertex1.getTransitions().size() == vertex2.getTransitions().size();

                    for (ContractTransition transition : vertex1.getTransitionKeys()) {

                        if (vertex1.goByTypeSymbol(transition) != vertex2.goByTypeSymbol(transition)) {
                            isSame = false;
                        }
                    }

                    if (isSame) {
                        RSignatureContractNode vertex1presenter = representatives.get(vertex1);
                        representatives.put(vertex2, vertex1presenter);
                        uselessVertices.add(v2);
                    }
                }
            }

            List<RSignatureContractNode> prevLevel = levels.get(i - 1);


            if (uselessVertices.size() > 0) {
                for (RSignatureContractNode node : prevLevel) {
                    for (ContractTransition transition : node.getTransitionKeys()) {
                        RSignatureContractNode child = node.goByTypeSymbol(transition);
                        node.addLink(transition, representatives.get(child));
                    }
                }
            }
            for (int index : uselessVertices) {
                if (this.levels.get(i).size() > index)
                    this.levels.get(i).remove(index);
            }
        }
    }

    public void compression() {
        compressionDFS(getStartNode(), 0);
        minimization();
    }

    private void compressionDFS(RSignatureContractNode node, int level) {
        int commonMask = -1;


        for (ContractTransition transition : node.getTransitionKeys()) {
            commonMask &= node.goByTypeSymbol(transition).getMask();
        }

        if (commonMask > 0 && node.getTransitionKeys().size() > 1) {
            for (ContractTransition transition : node.getTransitionKeys()) {
                updateSubtreeTypeDFS(node.goByTypeSymbol(transition), commonMask, level, level + 1, transition);
            }
        }
        for (ContractTransition transition : node.getTransitionKeys()) {
            compressionDFS(node.goByTypeSymbol(transition), level + 1);
        }
    }

    private void updateSubtreeTypeDFS(RSignatureContractNode node, int mask, int parentLevel, int level, ContractTransition transition) {

        if (mask % 2 == 1) {
            ReferenceContractTransition newTransition = new ReferenceContractTransition(parentLevel);

            node.setReferenceLinks();
            node.changeTransitionType(transition, newTransition);
        }

        for (ContractTransition typeTransition : node.getTransitionKeys()) {
            updateSubtreeTypeDFS(node.goByTypeSymbol(typeTransition), mask >> 1, parentLevel, level + 1, transition);
        }
    }

}