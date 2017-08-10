package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    public RSignatureContract(RSignature signature) {
        myArgsInfo = signature.getArgsInfo();
        levels = new ArrayList<>();
        for (int i = 0; i < getArgsInfo().size() + 2; i++) {
            levels.add(new ArrayList<>());
        }

        startContractNode = this.createNodeAndAddToLevels(0);

        termNode = this.createNodeAndAddToLevels(levels.size() - 1);

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

    public void addRSignature(RSignature signature) {
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

    void minimize() {
        int numberOfLevels = levels.size();

        for (int i = numberOfLevels - 1; i > 0; i--) {
            List<RSignatureContractNode> level = levels.get(i);

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

    int getNumberOfCalls() {
        return myNumberOfCalls;
    }

    private Set<NodeWithTransition> getMarkedTransitionsBFS(RSignatureContractNode oldNode, RSignatureContractNode newNode) {
        Set<NodeWithTransition> result = new HashSet<>();

        Queue<PairOfNodes> bfsQueue = new LinkedList<>();
        bfsQueue.add(new PairOfNodes(oldNode, newNode));

        while (!bfsQueue.isEmpty()) {
            PairOfNodes currPair = bfsQueue.poll();
            oldNode = currPair.oldNode;
            newNode = currPair.newNode;

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
                    node = termNode;
                else
                    node = createNodeAndAddToLevels(level);

                oldNode.addLink(transition, node);
                mergeDfs(node, newNode.goByTransition(transition), level, newTermNode);
            }
        }
    }

    public void mergeWith(RSignatureContract additive) {
        Set<NodeWithTransition> markedTransitions = getMarkedTransitionsBFS(startContractNode, additive.getStartNode());

        int levelID = 0;
        Map<RSignatureContractNode, RSignatureContractNode> linkToParentNode = new HashMap<>();

        for (List<RSignatureContractNode> level : levels) {

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

                    NodeWithTransition nodeWithTransition = new NodeWithTransition(node, transition);

                    if (linkToParentNode.keySet().contains(node)) {
                        nodeWithTransition.node = linkToParentNode.get(node);
                    }

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

    class NodeWithTransition {
        RSignatureContractNode node;
        ContractTransition transition;

        NodeWithTransition(RSignatureContractNode node, ContractTransition transition) {
            this.node = node;
            this.transition = transition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final NodeWithTransition that = (NodeWithTransition) o;

            //noinspection SimplifiableIfStatement
            if (node != null ? !node.equals(that.node) : that.node != null) return false;
            return transition != null ? transition.equals(that.transition) : that.transition == null;
        }

        @Override
        public int hashCode() {
            int result = node != null ? node.hashCode() : 0;
            result = 31 * result + (transition != null ? transition.hashCode() : 0);
            return result;
        }
    }

    class PairOfNodes {
        RSignatureContractNode oldNode;
        RSignatureContractNode newNode;

        PairOfNodes pairGoByTransition(ContractTransition transition) {
            return new PairOfNodes(oldNode.goByTransition(transition), newNode.goByTransition(transition));
        }

        PairOfNodes(@NotNull RSignatureContractNode node1, @Nullable RSignatureContractNode node2) {
            oldNode = node1;
            newNode = node2;
        }
    }
}

