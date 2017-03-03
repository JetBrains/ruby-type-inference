package org.jetbrains.ruby.runtime.signature;

import javafx.util.Pair;

import java.util.*;

public class RSignatureContract {

    private int counter = 0;

    private String myMethodName;
    private String myReceiverName;
    private String myGemName;
    private String myGemVersion;
    private List<RMethodArgument> myArgsInfo;
    private RSignatureContractNode startContractNode;

    private List<List<RSignatureContractNode>> levels;
    private Map<String, RSignatureContractNode> termNodes;

    private RSignatureContractNode createNodeAndAddToLevels(Integer index)
    {
        RSignatureContractNode newNode;

        if (index < myArgsInfo.size())
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
        this.myMethodName = signature.getMethodName();
        this.myReceiverName = signature.getReceiverName();
        this.myGemName = signature.getGemName();
        this.myGemVersion = signature.getGemVersion();
        this.myArgsInfo = signature.getArgsInfo();

        this.levels = new ArrayList<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        this.addRSignature(signature);
    }

    public RSignatureContract(List<RSignature> signatures) {

        this.levels = new ArrayList<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        for (RSignature signature : signatures) {
            this.addRSignature(signature);
        }
    }

    public RSignatureContractNode getStartNode() {
        return startContractNode;
    }


    public void addRSignature(RSignature signature) {
        this.counter++;
        RSignatureContractNode currNode = this.startContractNode;
        int i = 1;

        if (signature.getCallMid().equals("nil")) {
            for (RMethodArgument argument : signature.getArgsInfo()) {
                argument.setIsGiven(true);
            }
        } else
            signature.fetch();

        String returnType = signature.getReturnTypeName();

        RSignatureContractNode termNode = getTermNode(returnType);
        if(termNode == null)
        {
            termNode = this.createTermNode(returnType);
        }

        for (RMethodArgument argument : signature.getArgsInfo()) {
            String type = argument.getType();
            if(!argument.getIsGiven())
                type = "-";

            //Temp mask configuration
            int tempMask = 0;
            for (int j = (i - 1); j < signature.getArgsInfo().size(); j++)
            {
                tempMask <<= 1;
                if (signature.getArgsInfo().get(j).getType().equals(type))
                {
                    tempMask |= 1;
                }
            }

            if (returnType.equals(type)) {
                tempMask <<= 1;
                tempMask |= 1;
            }
            //---

            if(currNode.goByTypeSymbol(type) == null)
            {
                RSignatureContractNode newNode;

                newNode = this.createNodeAndAddToLevels(i);

                currNode.addLink(type, newNode, returnType);

                newNode.setMask(tempMask);
                currNode = newNode;
            } else {
                currNode = currNode.goByTypeSymbol(type);
                currNode.updateMask(tempMask);
            }
            i++;
        }

        currNode.addLink(returnType, termNode, returnType);
    }

    public Integer getNumberOfLevels()
    {
        return this.levels.size();
    }

    public void minimization()
    {
        for(int i = this.getNumberOfLevels() - 1; i > 0; i--)
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
                        node.addLink(type, representatives.get(child), node.getReachableTerm());
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

        Queue<Pair<RSignatureContractNode, String>> tmp = new ArrayDeque<>();
        tmp.add(new Pair<>(this.startContractNode, null));

        while (!tmp.isEmpty())
        {
            RSignatureContractNode currNode = tmp.peek().getKey();
            String currString = tmp.peek().getValue();

            tmp.remove();


            if(currNode == null)
            {
                System.out.println("bad");
            }

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
                tmp.add(new Pair<>(node, newVertexes.get(node)));
            }
        }

        return answer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignatureContract that = (RSignatureContract) o;

        return myMethodName.equals(that.myMethodName) &&
                myReceiverName.equals(that.myReceiverName) &&
                myGemName.equals(that.myGemName) &&
                myGemVersion.equals(that.myGemVersion);

    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        return result;
    }
}