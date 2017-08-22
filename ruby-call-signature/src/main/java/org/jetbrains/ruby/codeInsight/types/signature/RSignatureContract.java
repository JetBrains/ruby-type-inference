package org.jetbrains.ruby.codeInsight.types.signature;

import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;

import java.util.*;

import static org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TransitionHelper.calculateTransition;

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
        myLevels = new ArrayList<>(getArgsInfo().size() + 2);
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

    private RSignatureContract(@NotNull SignatureContract source) {
        myArgsInfo = source.getArgsInfo();
        myLevels = new ArrayList<>(getArgsInfo().size() + 2);
        for (int i = 0; i < getArgsInfo().size() + 2; i++) {
            myLevels.add(new ArrayList<>());
        }

        final Map<SignatureNode, kotlin.Pair<RSignatureContractNode, Integer>> oldNodesToNewWithLayerNumber = new HashMap<>();
        final Queue<SignatureNode> q = new ArrayDeque<>();

        final RSignatureContractNode newStartNode = createNodeAndAddToLevels(0);
        myLevels.get(0).add(newStartNode);
        oldNodesToNewWithLayerNumber.put(newStartNode, new kotlin.Pair<>(newStartNode, 0));
        q.add(source.getStartNode());

        while (!q.isEmpty()) {
            final SignatureNode oldSourceNode = q.poll();
            final kotlin.Pair<RSignatureContractNode, Integer> newSourceNodeAndLevel = oldNodesToNewWithLayerNumber.get(oldSourceNode);

            oldSourceNode.getTransitions().forEach((contractTransition, oldTargetNode) -> {
                final kotlin.Pair<RSignatureContractNode, Integer> newTargetNodeWithLayer =
                        oldNodesToNewWithLayerNumber.computeIfAbsent(oldTargetNode, old -> {
                            final RSignatureContractNode newNode = createNodeAndAddToLevels(newSourceNodeAndLevel.getSecond() + 1);
                            q.add(newNode);
                            return new kotlin.Pair<>(
                                    newNode,
                                    newSourceNodeAndLevel.getSecond() + 1
                            );
                        });

                newSourceNodeAndLevel.getFirst().addLink(contractTransition, newTargetNodeWithLayer.getFirst());
            });
        }

        myStartContractNode = newStartNode;

        if (myLevels.get(myLevels.size() - 1).size() != 1) {
            throw new AssertionError("Incorrect # of nodes on the last level: "
                    + myLevels.get(myLevels.size() - 1));
        }
        myTermNode = myLevels.get(myLevels.size() - 1).iterator().next();
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

    @NotNull
    public synchronized SignatureContract copy() {
        final Map<SignatureNode, RSignatureContractNode> oldToNew = new HashMap<>();

        RSignatureContractNode newStartNode = new RSignatureContractNode();
        oldToNew.put(myStartContractNode, newStartNode);

        for (int i = 0; i < myLevels.size() - 1; ++i) {
            for (final RSignatureContractNode oldSourceNode : myLevels.get(i)) {

                final RSignatureContractNode newSourceNode = oldToNew.get(oldSourceNode);
                oldSourceNode.getTransitions().forEach((transition, oldTargetNode) -> {
                    final RSignatureContractNode newTargetNode = oldToNew.computeIfAbsent(oldTargetNode,
                            (x) -> new RSignatureContractNode());
                    newSourceNode.addLink(transition, newTargetNode);
                });
            }
        }

        return new Immutable(newStartNode, oldToNew.size(), myArgsInfo);
    }

    public synchronized void addRTuple(@NotNull RTuple tuple) {
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

    synchronized void minimize() {
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

    private void AddToBfsQueueAndUse(@NotNull SignatureNode oldNode, @NotNull SignatureNode newNode, @NotNull Queue<Pair<PairOfNodes, Integer>> bfsQueue, @NotNull Set<PairOfNodes> used, Integer level) {
        PairOfNodes newPairOfNodes = new PairOfNodes(oldNode, newNode);

        if (!used.contains(newPairOfNodes)) {
            used.add(newPairOfNodes);
            bfsQueue.add(new Pair<>(newPairOfNodes, level));
        }
    }

    public synchronized void mergeWith(@NotNull SignatureContract additive) {
        // TODO synchronize on additive (can't do this plainly due to the possible deadlock)???
        Set<PairOfNodes> used = new HashSet<>();
        Queue<Pair<PairOfNodes, Integer>> bfsQueue = new LinkedList<>();
        PairOfNodes startPairOfNodes = new PairOfNodes(getStartNode(), additive.getStartNode());
        bfsQueue.add(new Pair<>(startPairOfNodes, 0));

        while (!bfsQueue.isEmpty()) {
            Pair<PairOfNodes, Integer> currItem = bfsQueue.poll();
            SignatureNode oldNode = currItem.getKey().myOldNode;
            SignatureNode newNode = currItem.getKey().myNewNode;
            Integer level = currItem.getValue();

            Map<SignatureNode, Integer> childNodesWithPows = new HashMap<>();

            for (ContractTransition transition : oldNode.getTransitions().keySet()) {
                SignatureNode node = oldNode.getTransitions().get(transition);

                if (childNodesWithPows.containsKey(node)) {
                    Integer oldPow = childNodesWithPows.get(node);
                    childNodesWithPows.put(node, oldPow + 1);
                } else {
                    childNodesWithPows.put(node, 1);
                }
            }

            for (ContractTransition transition : newNode.getTransitions().keySet()) {

                if (oldNode.getTransitions().containsKey(transition)) {
                    SignatureNode node = oldNode.getTransitions().get(transition);
                    if (childNodesWithPows.get(node) == 1) {
                        AddToBfsQueueAndUse(node, newNode.getTransitions().get(transition), bfsQueue, used, level + 1);
                        continue;
                    }

                    Integer oldPow = childNodesWithPows.get(node);
                    childNodesWithPows.put(node, oldPow - 1);
                }


                RSignatureContractNode node = createNodeAndAddToLevels(level + 1);

                if (oldNode.getTransitions().keySet().contains(transition)) {
                    SignatureNode nodeToClone = oldNode.getTransitions().get(transition);

                    for (ContractTransition contractTransition : nodeToClone.getTransitions().keySet()) {
                        node.getTransitions().put(contractTransition, nodeToClone.getTransitions().get(contractTransition));
                    }
                }
                oldNode.getTransitions().put(transition, node);

                AddToBfsQueueAndUse(node, newNode.getTransitions().get(transition), bfsQueue, used, level + 1);
            }
        }

        minimize();
    }

    @NotNull
    private RSignatureContractNode createNodeAndAddToLevels(int index) {
        RSignatureContractNode newNode = new RSignatureContractNode();

        //TODO
        if(index == myLevels.size() - 1 && !myLevels.get(index).isEmpty()) {
            return myLevels.get(index).get(0);
        }

        if (myLevels.size() <= index) {
            throw new IndexOutOfBoundsException("Trying to add to the level " + index
                    + " when the number of levels is " + myLevels.size());
        }
        myLevels.get(index).add(newNode);
        return newNode;
    }

    public static RSignatureContract mergeMutably(@NotNull SignatureContract first, @NotNull SignatureContract second) {
        if (first instanceof RSignatureContract) {
            ((RSignatureContract) first).mergeWith(second);
            return ((RSignatureContract) first);
        } else {
            return mergeMutably(new RSignatureContract(first), second);
        }
    }

    private static class Immutable implements SignatureContract {
        @NotNull
        private final SignatureNode myStartNode;

        private final int myNodeCount;
        @NotNull
        private final List<ParameterInfo> myArgsInfo;


        private Immutable(@NotNull SignatureNode startNode, int nodeCount, @NotNull List<ParameterInfo> argsInfo) {
            myStartNode = startNode;
            myNodeCount = nodeCount;
            myArgsInfo = argsInfo;
        }

        @Override
        public int getNodeCount() {
            return myNodeCount;
        }

        @NotNull
        @Override
        public SignatureNode getStartNode() {
            return myStartNode;
        }

        @NotNull
        @Override
        public List<ParameterInfo> getArgsInfo() {
            return myArgsInfo;
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

