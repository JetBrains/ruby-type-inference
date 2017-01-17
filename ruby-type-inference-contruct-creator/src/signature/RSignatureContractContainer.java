package signature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RSignatureContractContainer {
    private Map<RMethodInfo, RSignatureContract> contracts;
    private int contractCount = 0;

    public RSignatureContractContainer() {
        contracts = new HashMap<>();
    }
    public RSignatureContract getContract(RMethodInfo info){
        return  contracts.get(info);
    }


    public void print()
    {
        for (RMethodInfo rMethodInfo : contracts.keySet()) {
            System.out.println("-----------");
            System.out.println("Number of calls:" + contracts.get(rMethodInfo).getCounter());
            System.out.println(rMethodInfo.toString());
            System.out.println(rMethodInfo.getPath());
            System.out.println(rMethodInfo.getLine());
            contracts.get(rMethodInfo).minimization();
            List<String> presentation = contracts.get(rMethodInfo).getStringPresentation();
            if(presentation.size() > 1){
                System.out.println("OurClient");
            }
            for (String s : presentation) {
                System.out.println(s);
            }
        }
    }

    public void addSignature(RSignature signature){
        RMethodInfo currInfo = new RMethodInfo(signature);
        this.contractCount++;

        //signature.fetch();

        if(contracts.containsKey(currInfo)){
            contracts.get(currInfo).addRSignature(signature);
        }
        else{
            RSignatureContract contract = new RSignatureContract(signature);
            contracts.put(currInfo, contract);
        }
    }
    public int getContractCount()
    {
        return this.contractCount;
    }
}
