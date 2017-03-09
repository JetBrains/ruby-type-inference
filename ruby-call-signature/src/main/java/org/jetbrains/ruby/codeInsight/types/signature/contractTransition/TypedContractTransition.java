package org.jetbrains.ruby.codeInsight.types.signature.contractTransition;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TypedContractTransition implements ContractTransition {

    @NotNull
    private final String myType;

    public TypedContractTransition(@NotNull String type) {
        this.myType = type;
    }

    @NotNull
    @Override
    public Set<String> getValue(@NotNull List<Set<String>> readTypes) {
        return Collections.singleton(myType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final TypedContractTransition that = (TypedContractTransition) o;

        return myType.equals(that.myType);
    }

    @Override
    public int hashCode() {
        return myType.hashCode();
    }
}