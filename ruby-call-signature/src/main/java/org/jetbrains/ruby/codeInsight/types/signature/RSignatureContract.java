package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;

import java.util.*;

public class RSignatureContract implements SignatureContract {

    @NotNull
    private final RSignatureContractNode myStartContractNode;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<List<RSignatureContractNode>> myLevels;
    @NotNull
    private final RSignatureContractNode myTermNode;

    @NotNull
    public List<ParameterInfo> getParamInfoList() {
        return myArgsInfo;
    }

    @NotNull
    private RSignatureContractNode getTermNode() {
        return myTermNode;
    }

    public RSignatureContract(@NotNull RTuple tuple) {
        myArgsInfo = tuple.getArgsInfo();
        myLevels = new ArrayList<>();
        for (int i = 0; i < getArgsInfo().size() + 2; i++) {
            myLevels.add(new ArrayList<>());
        }

        myStartContractNode = createNodeAndAddToLevels(0);

        myTermNode = createNodeAndAddToLevels(myLevels.size() - 1);

        addRTuple(tuple);
    }

    public RSignatureContract(@NotNull List<ParameterInfo> argsInfo,
                              @NotNull RSignatureContractNode startContractNode,
                              @NotNull RSignatureContractNode termNode,
                              @NotNull List<List<RSignatureContractNode>> levels) {
        myStartContractNode = startContractNode;
        myArgsInfo = argsInfo;
        myLevels = levels;
        myTermNode = termNode;

        // TODO recalculate mask
    }

    @NotNull
    @Override
    public RSignatureContractNode getStartNode() {
        return myStartContractNode;
    }

    @NotNull
    @Override
    public List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    public int getNodeCount() {
        return myLevels.stream().map(List::size).reduce(0, (a, b) -> a + b);
    }

    public void addRTuple(@NotNull RTuple tuple) {
        RSignatureContractNode currNode = myStartContractNode;

        String returnType = tuple.getReturnTypeName();

        RSignatureContractNode termNode = getTermNode();

        final List<String> argsTypes = tuple.getArgsTypes();
        for (int argIndex = 0; argIndex < argsTypes.size(); argIndex++) {
            final String type = argsTypes.get(argIndex);

            final ContractTransition transition = calculateTransition(tuple.getArgsTypes(), argIndex, type);

            if (!currNode.containsKey(transition)) {
                final RSignatureContractNode newNode = createNodeAndAddToLevels(argIndex + 1);

                currNode.addLink(transition, newNode);

                currNode = newNode;
            } else {

                currNode = currNode.goByTransition(transition);
            }
        }

        final ContractTransition transition = calculateTransition(tuple.getArgsTypes(), tuple.getArgsTypes().size(), returnType);

        currNode.addLink(transition, termNode);
    }

    void minimize() {
        int numberOfLevels = myLevels.size();

        for (int i = numberOfLevels - 1; i > 0; i--) {
            List<RSignatureContractNode> level = myLevels.get(i);

            HashMap<RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            Set<RSignatureContractNode> uselessVertices = new HashSet<>();

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

            List<RSignatureContractNode> prevLevel = myLevels.get(i - 1);


            if (!uselessVertices.isEmpty()) {
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

    @NotNull
    public List<List<RSignatureContractNode>> getLevels() {
        return myLevels;
    }

    public void mergeWith(@NotNull RSignatureContract additive) {
        Set<NodeWithTransition> markedTransitions = getMarkedTransitionsBFS(myStartContractNode, additive.getStartNode());

        int levelID = 0;
        Map<RSignatureContractNode, RSignatureContractNode> linkToParentNode = new HashMap<>();

        for (List<RSignatureContractNode> level : myLevels) {

            for (RSignatureContractNode node : level) {
                Set<ContractTransition> transitions = node.getTransitionKeys();
                Set<RSignatureContractNode> children = new HashSet<>();
                Set<RSignatureContractNode> multipleEdgeChildren = new HashSet<>();

                for (ContractTransition transition : transitions) {
                    RSignatureContractNode childNode = node.goByTransition(transition);
                    if (children.contains(childNode)) {
                        multipleEdgeChildren.add(childNode);
                    } else {
                        children.add(childNode);
                    }
                }

                for (ContractTransition transition : transitions) {

                    RSignatureContractNode childNode = node.goByTransition(transition);

                    if (!multipleEdgeChildren.contains(childNode)) {
                        continue;
                    }

                    final NodeWithTransition nodeWithTransition = new NodeWithTransition(
                            linkToParentNode.keySet().contains(node) ? linkToParentNode.get(node) : node,
                            transition);

                    if (markedTransitions.contains(nodeWithTransition)) {

                        RSignatureContractNode nodeToClone = node.goByTransition(transition);
                        RSignatureContractNode clonedNode;

                        clonedNode = createNodeAndAddToLevels(levelID + 1);
                        for (ContractTransition tmpTransition : nodeToClone.getTransitionKeys()) {
                            clonedNode.addLink(tmpTransition, nodeToClone.goByTransition(tmpTransition));
                        }

                        linkToParentNode.put(clonedNode, nodeToClone);

                        node.addLink(transition, clonedNode);
                    }
                }
            }
            levelID++;
        }

        mergeDfs(getStartNode(), additive.getStartNode(), 0, additive.getTermNode());
        minimize();
    }

    boolean accept(@NotNull RTuple signature) {
        RSignatureContractNode currNode = myStartContractNode;

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

    @NotNull
    private ContractTransition calculateTransition(@NotNull List<String> argTypes, int argIndex, @NotNull String type) {
        final int mask = getNewMask(argTypes, argIndex, type);

        if (mask > 0)
            return new ReferenceContractTransition(mask);
        else
            return new TypedContractTransition(type);
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

    @NotNull
    private Set<NodeWithTransition> getMarkedTransitionsBFS(@NotNull RSignatureContractNode oldNode,
                                                            @NotNull RSignatureContractNode newNode) {
        Set<NodeWithTransition> result = new HashSet<>();

        Queue<PairOfNodes> bfsQueue = new LinkedList<>();
        bfsQueue.add(new PairOfNodes(oldNode, newNode));

        while (!bfsQueue.isEmpty()) {
            PairOfNodes currPair = bfsQueue.poll();
            oldNode = currPair.myOldNode;
            newNode = currPair.myNewNode;

            for (ContractTransition transition : newNode.getTransitions().keySet()) {
                if (oldNode.containsKey(transition)) {
                    result.add(new NodeWithTransition(oldNode, transition));
                    bfsQueue.add(currPair.pairGoByTransition(transition));
                }
            }
        }

        return result;
    }

    private void mergeDfs(@NotNull RSignatureContractNode oldNode,
                          @NotNull RSignatureContractNode newNode,
                          int level,
                          @NotNull RSignatureContractNode newTermNode) {

        level++;

        for (ContractTransition transition : newNode.getTransitionKeys()) {
            if (oldNode.getTransitionKeys().contains(transition)) {
                mergeDfs(oldNode.goByTransition(transition), newNode.goByTransition(transition), level, newTermNode);
            } else {
                RSignatureContractNode node;

                if (newNode.goByTransition(transition) == newTermNode)
                    node = myTermNode;
                else
                    node = createNodeAndAddToLevels(level);

                oldNode.addLink(transition, node);
                mergeDfs(node, newNode.goByTransition(transition), level, newTermNode);
            }
        }
    }

    @NotNull
    private RSignatureContractNode createNodeAndAddToLevels(int index) {
        RSignatureContractNode newNode = new RSignatureContractNode();

        while (myLevels.size() <= index)
            myLevels.add(new ArrayList<>());
        myLevels.get(index).add(newNode);
        return newNode;
    }

    private static class NodeWithTransition {
        @NotNull
        private final RSignatureContractNode myNode;
        @NotNull
        private final ContractTransition myTransition;

        NodeWithTransition(@NotNull RSignatureContractNode node, @NotNull ContractTransition transition) {
            myNode = node;
            myTransition = transition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final NodeWithTransition that = (NodeWithTransition) o;

            //noinspection SimplifiableIfStatement
            if (!myNode.equals(that.myNode)) return false;
            return myTransition.equals(that.myTransition);
        }

        @Override
        public int hashCode() {
            int result = myNode.hashCode();
            result = 31 * result + myTransition.hashCode();
            return result;
        }
    }

    private static class PairOfNodes {
        @NotNull
        private final RSignatureContractNode myOldNode;
        @NotNull
        private final RSignatureContractNode myNewNode;

        @NotNull
        PairOfNodes pairGoByTransition(@NotNull ContractTransition transition) {
            return new PairOfNodes(myOldNode.goByTransition(transition), myNewNode.goByTransition(transition));
        }

        PairOfNodes(@NotNull RSignatureContractNode node1, @NotNull RSignatureContractNode node2) {
            myOldNode = node1;
            myNewNode = node2;
        }
    }
}

