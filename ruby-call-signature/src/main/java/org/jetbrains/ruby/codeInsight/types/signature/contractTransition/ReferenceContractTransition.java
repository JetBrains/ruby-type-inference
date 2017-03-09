package org.jetbrains.ruby.codeInsight.types.signature.contractTransition;

public class ReferenceContractTransition implements ContractTransition {

    public int link;

    public ReferenceContractTransition(int link) {
        this.link = link;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 17 + link;
        return hash;
    }

    @Override
    public boolean equals(Object aThat) {
        if (this == aThat)
            return true;

        if (!(aThat instanceof ReferenceContractTransition))
            return false;

        ReferenceContractTransition that = (ReferenceContractTransition) aThat;

        return that.link == this.link;
    }
}