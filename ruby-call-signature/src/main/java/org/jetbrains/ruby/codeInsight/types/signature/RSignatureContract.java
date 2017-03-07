package org.jetbrains.ruby.codeInsight.types.signature;

import java.util.*;

public class RSignatureContract {

    private int counter = 0;
    private int mySize = 0;

    private RSignatureContractNode startContractNode;

    private List<List<RSignatureContractNode>> levels;
    private Map<String, RSignatureContractNode> termNodes;

    private RSignatureContractNode createNodeAndAddToLevels(Integer index)
    {
        RSignatureContractNode newNode;

        if (index < mySize)
            newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.argNode);
        else
            newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.returnNode);

        while(levels.size() <= index)
            levels.add(new ArrayList<>());
        levels.get(index).add(newNode);
        return newNode;
    }

    public int getCounter()
    {
        return counter;
    }

    private RSignatureContractNode createTermNode(String type)
    {
        if(!termNodes.containsKey(type)) {
            RSignatureContractNode newNode = new RSignatureContractNode(RSignatureContractNode.ContractNodeType.returnTypeNode);
            termNodes.put(type, newNode);
        }

        return termNodes.get(type);
    }

    private RSignatureContractNode getTermNode(String type)
    {
        return termNodes.get(type);
    }

    public RSignatureContract(RSignature signature) {
        this.mySize = signature.getArgsInfo().size();

        this.levels = new ArrayList<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        this.addRSignature(signature);
    }

    public RSignatureContractNode getStartNode() {
        return startContractNode;
    }


    public void addRSignature(RSignature signature) {
        this.counter++;
        RSignatureContractNode currNode = this.startContractNode;
        int currParamId = 1;

        String returnType = signature.getReturnTypeName();

        RSignatureContractNode termNode = getTermNode(returnType);

        if (termNode == null) {
            termNode = this.createTermNode(returnType);
        }

        for (String type : signature.getArgsTypes()) {

            int tempMask = 0;
            for (int j = (currParamId - 1); j < signature.getArgsInfo().size(); j++) {
                tempMask <<= 1;
                if (signature.getArgsTypes().get(j).equals(type)) {
                    tempMask |= 1;
                }
            }

            if (returnType.equals(type)) {
                tempMask <<= 1;
                tempMask |= 1;
            }


            if (currNode.goByTypeSymbol(type) == null) {
                RSignatureContractNode newNode;

                newNode = this.createNodeAndAddToLevels(currParamId);

                currNode.addLink(type, newNode);

                newNode.setMask(tempMask);
                currNode = newNode;
            } else {
                currNode = currNode.goByTypeSymbol(type);
                currNode.updateMask(tempMask);
            }
            currParamId++;
        }

        currNode.addLink(returnType, termNode);
    }

    public void minimization()
    {
        int numberOfLevels = levels.size();

        for (int i = numberOfLevels - 1; i > 0; i--)
        {
            List<RSignatureContractNode> level = levels.get(i);

            HashMap <RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            List<Integer> uselessVertices = new ArrayList<>();

            for (RSignatureContractNode node : level) {
                representatives.put(node, node);
            }

            for(int v1 = 0; v1 < level.size(); v1++){
                for(int v2 = v1 + 1; v2 < level.size(); v2++){
                    RSignatureContractNode vertex1 = level.get(v1);
                    RSignatureContractNode vertex2 = level.get(v2);

                    boolean isSame = true;

                    for (String type : vertex1.getTransitionKeys()) {
                        if(vertex1.goByTypeSymbol(type) != vertex2.goByTypeSymbol(type))
                        {
                            isSame = false;
                        }
                    }

                    for (String type : vertex2.getTransitionKeys()) {
                        if(vertex1.goByTypeSymbol(type) != vertex2.goByTypeSymbol(type))
                        {
                            isSame = false;
                        }
                    }

                    if(isSame)
                    {
                        RSignatureContractNode vertex1presenter = representatives.get(vertex1);
                        representatives.put(vertex2, vertex1presenter);
                        uselessVertices.add(v2);
                    }
                }
            }

            List<RSignatureContractNode> prevLevel = levels.get(i - 1);


            if (uselessVertices.size() > 0) {
                for (RSignatureContractNode node : prevLevel) {
                    for (String type : node.getTransitionKeys()) {
                        RSignatureContractNode child = node.goByTypeSymbol(type);
                        node.addLink(type, representatives.get(child));
                    }
                }
            }
            for (int index : uselessVertices) {
                if (this.levels.get(i).size() > index)
                    this.levels.get(i).remove(index);
            }
        }
    }


    public List<String> getStringPresentation() {
        List<String> answer = new ArrayList<>();

        Queue<javafx.util.Pair<RSignatureContractNode, String>> tmp = new ArrayDeque<>();
        tmp.add(new javafx.util.Pair<>(this.startContractNode, null));

        while (!tmp.isEmpty())
        {
            RSignatureContractNode currNode = tmp.peek().getKey();
            String currString = tmp.peek().getValue();

            tmp.remove();

            if (currNode.getNodeType() == RSignatureContractNode.ContractNodeType.returnNode)
            {
                StringJoiner joiner = new StringJoiner("|");

                for (String transitionType : currNode.getTransitionKeys()) {
                    joiner.add(transitionType);
                }
                if (currString == null)
                    answer.add("()" + " -> " + joiner.toString());
                else
                    answer.add("(" + currString + ")" + " -> " + joiner.toString());
                continue;
            }

            Map <RSignatureContractNode, String> newVertexes = new HashMap<>();

            for (String transitionType : currNode.getTransitionKeys()) {
                RSignatureContractNode tmpNode = currNode.goByTypeSymbol(transitionType);

                if(newVertexes.containsKey(tmpNode))
                {
                    String oldValue = newVertexes.get(tmpNode);
                    newVertexes.remove(tmpNode);
                    newVertexes.put(tmpNode, oldValue + "|" + transitionType);
                }
                else
                {
                    if(transitionType.equals("no arguments"))
                        newVertexes.put(tmpNode, "");
                    else {
                        if (currString == null)
                            newVertexes.put(tmpNode, transitionType);
                        else
                            newVertexes.put(tmpNode, currString + ";" + transitionType);
                    }
                }
            }
            for (RSignatureContractNode node : newVertexes.keySet()) {
                tmp.add(new javafx.util.Pair<>(node, newVertexes.get(node)));
            }
        }

        return answer;
    }

    public void compression() {
        compressionDFS(getStartNode());
    }

    private void compressionDFS(RSignatureContractNode node) {
        int commonMask = -1;
        boolean subtreesIsSame = true;

        for (String type : node.getTransitionKeys()) {
            if (commonMask == -1)
                commonMask = node.goByTypeSymbol(type).getMask();
            else {
                commonMask &= node.goByTypeSymbol(type).getMask();
            }
        }

        if (commonMask > 0 && node.getTransitionKeys().size() > 1) {
            RSignatureContractNode node1 = null;
            RSignatureContractNode node2 = null;

            for (String type : node.getTransitionKeys()) {
                if (node1 == null)
                    node2 = node.goByTypeSymbol(type);

                if (node1 != null && node2 != null) {
                    if (!checkSameSubtreesDFS(node1, node2, commonMask << 1, type))
                        subtreesIsSame = false;
                }
            }
            if (subtreesIsSame) {
                //конденсация собстна
            }
        }
        for (String type : node.getTransitionKeys()) {
            compressionDFS(node.goByTypeSymbol(type));
        }
    }

    private boolean checkSameSubtreesDFS(RSignatureContractNode node1, RSignatureContractNode node2, int mask, String type) {
        if (mask % 2 == 0) {
            return checkSameSubtreesDFS(node1.goByTypeSymbol(type), node2.goByTypeSymbol(type), mask << 1, type);
        } else {
            if (node1.getTransitionKeys().size() != node2.getTransitionKeys().size()) {
                return false;
            } else {
                for (String typeTransition : node1.getTransitionKeys()) {
                    if (!node2.getTransitionKeys().contains(typeTransition)) {
                        return false;
                    }
                }
                Set<RSignatureContractNode> used = new HashSet<>();

                for (String typeTransition : node1.getTransitionKeys()) {
                    if (!used.contains(node1.goByTypeSymbol(typeTransition))) {
                        checkSameSubtreesDFS(node1.goByTypeSymbol(typeTransition), node2.goByTypeSymbol(typeTransition), mask << 1, type);
                        used.add(node1.goByTypeSymbol(typeTransition));
                    }
                }
            }
        }
        return true;
    }

}