package org.jetbrains.ruby.runtime.signature;

import javafx.util.Pair;

import java.util.*;

public class RSignatureContract {

    private int counter = 0;

    private String myMethodName;
    private String myReceiverName;
    private String myGemName;
    private String myGemVersion;
    private List<String> myArgsInfo;
    private RSignatureContractNode startContractNode;

    private Vector<Vector<RSignatureContractNode>> levels;
    private HashMap<String, RSignatureContractNode> termNodes;

    private RSignatureContractNode createNodeAndAddToLevels(Integer index)
    {
        RSignatureContractNode newNode = new RSignatureContractNode(index);
        while(levels.size() <= index)
            levels.add(new Vector<>());
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
            RSignatureContractNode newNode = new RSignatureContractNode(type);
            termNodes.put(type, newNode);
        }

        return termNodes.get(type);
    }

    public RSignatureContractNode getTermNode(String type)
    {
        return termNodes.get(type);
    }

    public RSignatureContract(RSignature signature) {
        this.myMethodName = signature.getMethodName();
        this.myReceiverName = signature.getReceiverName();
        this.myGemName = signature.getGemName();
        this.myGemVersion = signature.getGemVersion();

        this.levels = new Vector<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        this.addRSignature(signature);
    }

    public RSignatureContract(final String methodName, final String receiverName,
                              final String gemName, final String gemVersion) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myGemName = gemName;
        this.myGemVersion = gemVersion;

        this.levels = new Vector<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);
    }
    public RSignatureContract(List<RSignature> signatures) {

        this.levels = new Vector<>();
        this.termNodes = new HashMap<>();
        this.startContractNode = this.createNodeAndAddToLevels(0);

        for (RSignature signature : signatures) {
            this.addRSignature(signature);
        }
    }

    public String getMethodName() {
        return myMethodName;
    }

    public String getReceiverName() {
        return myReceiverName;
    }

    public String getGemName() {
        return myGemName;
    }

    public String getGemVersion() {
        return myGemVersion;
    }

    public void addRSignature(RSignature signature) {
        this.counter++;
        RSignatureContractNode currNode = this.startContractNode;
        int i = 1;

        signature.fetch();

        String returnType = signature.getReturnTypeName();
        RSignatureContractNode termNode = getTermNode(returnType);
        if(termNode == null)
        {
            termNode = this.createTermNode(returnType);
        }

        if(signature.getArgsTypeName().size() == 1 && signature.getArgsTypeName().get(0).length() == 0)
        {
            this.startContractNode.addLink("no arguments", termNode);
            return;
        }

        for (RMethodArgument argument : signature.getArgsInfo()) {
            String type = argument.getType();
            if(!argument.getIsGiven())
                type = "-";

            if(i == signature.getArgsTypeName().size())
            {

                currNode.addLink(type, termNode);
                return;
            }


            if(currNode.goByTypeSymbol(type) == null)
            {
                RSignatureContractNode newNode = this.createNodeAndAddToLevels(i);//new RSignatureContractNode(i);
                currNode.addLink(type, newNode);
                currNode = newNode;
            }
            else
                currNode = currNode.goByTypeSymbol(type);

            i++;
        }
    }

    public Integer getNumberOfLevels()
    {
        return this.levels.size();
    }

    public void minimization()
    {
        for(int i = this.getNumberOfLevels() - 1; i > 0; i--)
        {
            Vector<RSignatureContractNode> level = levels.get(i);

            int sizeOfLevel = level.size();
            HashMap <RSignatureContractNode, RSignatureContractNode> representatives = new HashMap<>();
            List<Integer> uselessVertexex = new LinkedList<>();

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
                        uselessVertexex.add(v2);
                    }
                }
            }

            Vector<RSignatureContractNode> prevLevel = levels.get(i - 1);


            if(uselessVertexex.size() > 0) {
                for (RSignatureContractNode node : prevLevel) {
                    for (String type : node.getTransitionKeys()) {
                        RSignatureContractNode child = node.goByTypeSymbol(type);
                        node.addLink(type, representatives.get(child));
                    }
                }
            }
            for (Integer index : uselessVertexex) {
                if(this.levels.get(i).size() > index.intValue())
                    this.levels.get(i).remove(index.intValue());
            }
        }
    }

    public List<String> getStringPresentation() {
        List<String> answer = new ArrayList<>();

        Queue<Pair<RSignatureContractNode, String>> tmp = new LinkedList<>();
        tmp.add(new Pair(this.startContractNode, ""));

        while (!tmp.isEmpty())
        {
            RSignatureContractNode currNode = tmp.peek().getKey();
            String currString = tmp.peek().getValue();

            tmp.remove();


            if(currNode == null)
            {
                System.out.println("bad");
            }

            if(currNode.getNodeType() != null)
            {
                answer.add(currString + " -> " + currNode.getNodeType());
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
                    newVertexes.put(tmpNode, currString + ";" + transitionType);
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