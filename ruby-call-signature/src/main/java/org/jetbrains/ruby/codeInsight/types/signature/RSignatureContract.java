package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
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
    private final SignatureNode myTermNode;

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
                              @NotNull SignatureNode termNode,
                              @NotNull List<List<RSignatureContractNode>> levels) {
        myStartContractNode = startContractNode;
        myArgsInfo = argsInfo;
        myLevels = levels;
        myTermNode = termNode;

        // TODO recalculate mask
    }

    @NotNull
    @Override
    public SignatureNode getStartNode() {
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

        final List<String> argsTypes = tuple.getArgsTypes();
        for (int argIndex = 0; argIndex < argsTypes.size(); argIndex++) {
            final String type = argsTypes.get(argIndex);

            final ContractTransition transition = calculateTransition(tuple.getArgsTypes(), argIndex, type);

            if (!currNode.getTransitions().containsKey(transition)) {
                final RSignatureContractNode newNode = createNodeAndAddToLevels(argIndex + 1);

                currNode.addLink(transition, newNode);

                currNode = newNode;
            } else {

                currNode = ((RSignatureContractNode) currNode.getTransitions().get(transition));
            }
        }

        final ContractTransition transition = calculateTransition(tuple.getArgsTypes(), tuple.getArgsTypes().size(), returnType);

        currNode.addLink(transition, myTermNode);
    }

    void minimize() {
        int numberOfLevels = myLevels.size();

        for (int i = numberOfLevels - 1; i > 0; i--) {
            List<RSignatureContractNode> level = myLevels.get(i);

            HashMap<SignatureNode, SignatureNode> representatives = new HashMap<>();
            Set<SignatureNode> uselessVertices = new HashSet<>();

            for (SignatureNode node : level) {
                representatives.put(node, node);
            }

            for (int v1 = 0; v1 < level.size(); v1++) {
                for (int v2 = v1 + 1; v2 < level.size(); v2++) {
                    SignatureNode vertex1 = level.get(v1);
                    SignatureNode vertex2 = level.get(v2);

                    boolean isSame = vertex1.getTransitions().size() == vertex2.getTransitions().size();

                    for (ContractTransition transition : vertex1.getTransitions().keySet()) {

                        if (!vertex2.getTransitions().containsKey(transition) || vertex1.getTransitions().get(transition) != vertex2.getTransitions().get(transition)) {
                            isSame = false;
                        }
                    }

                    if (isSame) {
                        SignatureNode vertex1presenter = representatives.get(vertex1);
                        representatives.put(vertex2, vertex1presenter);
                        uselessVertices.add(vertex2);
                    }
                }
            }

            List<RSignatureContractNode> prevLevel = myLevels.get(i - 1);


            if (!uselessVertices.isEmpty()) {
                for (RSignatureContractNode node : prevLevel) {
                    for (ContractTransition transition : node.getTransitions().keySet()) {
                        RSignatureContractNode child = ((RSignatureContractNode) node.getTransitions().get(transition));
                        node.addLink(transition, representatives.get(child));
                    }
                }
            }

            //noinspection SuspiciousMethodCalls
            level.removeAll(uselessVertices);
        }
    }

    @TestOnly
    @NotNull
    public List<List<RSignatureContractNode>> getLevels() {
        return myLevels;
    }

    public void mergeWith(@NotNull RSignatureContract additive) {
        Set<NodeWithTransition> markedTransitions = getMarkedTransitionsBFS(myStartContractNode, additive.getStartNode());

        int levelID = 0;
        Map<SignatureNode, SignatureNode> linkToParentNode = new HashMap<>();

        for (List<RSignatureContractNode> level : myLevels) {

            for (RSignatureContractNode node : level) {
                Set<ContractTransition> transitions = node.getTransitions().keySet();
                Set<SignatureNode> children = new HashSet<>();
                Set<SignatureNode> multipleEdgeChildren = new HashSet<>();

                for (ContractTransition transition : transitions) {
                    SignatureNode childNode = node.getTransitions().get(transition);
                    if (children.contains(childNode)) {
                        multipleEdgeChildren.add(childNode);
                    } else {
                        children.add(childNode);
                    }
                }

                for (ContractTransition transition : transitions) {
                    SignatureNode childNode = node.getTransitions().get(transition);

                    if (!multipleEdgeChildren.contains(childNode)) {
                        continue;
                    }

                    final NodeWithTransition nodeWithTransition = new NodeWithTransition(
                            linkToParentNode.getOrDefault(node, node),
                            transition);

                    if (markedTransitions.contains(nodeWithTransition)) {

                        SignatureNode nodeToClone = node.getTransitions().get(transition);
                        RSignatureContractNode clonedNode;

                        clonedNode = createNodeAndAddToLevels(levelID + 1);
                        for (ContractTransition tmpTransition : nodeToClone.getTransitions().keySet()) {
                            clonedNode.addLink(tmpTransition, nodeToClone.getTransitions().get(tmpTransition));
                        }

                        linkToParentNode.put(clonedNode, nodeToClone);

                        node.addLink(transition, clonedNode);
                    }
                }
            }
            levelID++;
        }

        mergeDfs(myStartContractNode, additive.getStartNode(), 0, additive.myTermNode);
        minimize();
    }

    boolean accept(@NotNull RTuple signature) {
        SignatureNode currNode = myStartContractNode;

        String returnType = signature.getReturnTypeName();

        final List<String> argsTypes = signature.getArgsTypes();
        for (int argIndex = 0; argIndex < argsTypes.size(); argIndex++) {
            final String type = argsTypes.get(argIndex);

            final ContractTransition transition = calculateTransition(signature.getArgsTypes(), argIndex, type);

            if (!currNode.getTransitions().containsKey(transition)) {
                return false;
            } else {
                currNode = currNode.getTransitions().get(transition);
            }
        }

        final ContractTransition transition = calculateTransition(signature.getArgsTypes(), signature.getArgsTypes().size(), returnType);

        return currNode.getTransitions().containsKey(transition);
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
    private Set<NodeWithTransition> getMarkedTransitionsBFS(@NotNull SignatureNode oldNode,
                                                            @NotNull SignatureNode newNode) {
        Set<NodeWithTransition> result = new HashSet<>();

        Queue<PairOfNodes> bfsQueue = new LinkedList<>();
        bfsQueue.add(new PairOfNodes(oldNode, newNode));

        while (!bfsQueue.isEmpty()) {
            PairOfNodes currPair = bfsQueue.poll();
            oldNode = currPair.myOldNode;
            newNode = currPair.myNewNode;

            for (ContractTransition transition : newNode.getTransitions().keySet()) {
                if (oldNode.getTransitions().containsKey(transition)) {
                    result.add(new NodeWithTransition(oldNode, transition));
                    bfsQueue.add(currPair.pairGoByTransition(transition));
                }
            }
        }

        return result;
    }

    private void mergeDfs(@NotNull RSignatureContractNode oldNode,
                          @NotNull SignatureNode newNode,
                          int level,
                          @NotNull SignatureNode newTermNode) {

        level++;

        for (ContractTransition transition : newNode.getTransitions().keySet()) {
            final SignatureNode nextNewNode = newNode.getTransitions().get(transition);

            if (oldNode.getTransitions().containsKey(transition)) {
                mergeDfs(((RSignatureContractNode) oldNode.getTransitions().get(transition)), nextNewNode, level, newTermNode);
            } else {
                if (nextNewNode == newTermNode) {
                    oldNode.addLink(transition, myTermNode);
                    return;
                }

                final RSignatureContractNode node = createNodeAndAddToLevels(level);
                oldNode.addLink(transition, node);
                mergeDfs(node, nextNewNode, level, newTermNode);
            }
        }
    }

    @NotNull
    private RSignatureContractNode createNodeAndAddToLevels(int index) {
        RSignatureContractNode newNode = new RSignatureContractNode();

        if (myLevels.size() <= index) {
            throw new IndexOutOfBoundsException("Trying to add to the level " + index
                    + " when the number of levels is " + myLevels.size());
        }
        myLevels.get(index).add(newNode);
        return newNode;
    }

    private static class NodeWithTransition {
        @NotNull
        private final SignatureNode myNode;
        @NotNull
        private final ContractTransition myTransition;

        NodeWithTransition(@NotNull SignatureNode node, @NotNull ContractTransition transition) {
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
        private final SignatureNode myOldNode;
        @NotNull
        private final SignatureNode myNewNode;

        @NotNull
        PairOfNodes pairGoByTransition(@NotNull ContractTransition transition) {
            return new PairOfNodes(myOldNode.getTransitions().get(transition), myNewNode.getTransitions().get(transition));
        }

        PairOfNodes(@NotNull SignatureNode node1, @NotNull SignatureNode node2) {
            myOldNode = node1;
            myNewNode = node2;
        }
    }
}

