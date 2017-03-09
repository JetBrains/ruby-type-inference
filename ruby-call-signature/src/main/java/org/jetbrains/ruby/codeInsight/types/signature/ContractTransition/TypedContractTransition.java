package org.jetbrains.ruby.codeInsight.types.signature.ContractTransition;

public class TypedContractTransition implements ContractTransition {

    private String type;

    public TypedContractTransition(String type) {
        this.type = type;
    }

    public void setType(String newType) {
        type = newType;
    }

    public String getType() {
        return type;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 17 + type.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object aThat) {
        if (this == aThat)
            return true;

        if (!(aThat instanceof TypedContractTransition))
            return false;

        TypedContractTransition that = (TypedContractTransition) aThat;

        return that.type.equals(this.type);
    }
}