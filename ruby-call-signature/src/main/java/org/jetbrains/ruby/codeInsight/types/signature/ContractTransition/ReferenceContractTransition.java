package org.jetbrains.ruby.codeInsight.types.signature.ContractTransition;

public class ReferenceContractTransition implements ContractTransition {

    private int link;

    public ReferenceContractTransition(int link) {
        this.link = link;
    }

    public void setLink(int newLink) {
        link = newLink;
    }

    public int getLink() {
        return link;
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