package org.jetbrains.ruby.codeInsight.types.signature.contractTransition;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class ReferenceContractTransition implements ContractTransition {

    private final int myLink;

    public ReferenceContractTransition(int link) {
        myLink = link;
    }

    @NotNull
    @Override
    public Set<String> getValue(@NotNull List<Set<String>> readTypes) {
        return readTypes.get(myLink);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ReferenceContractTransition that = (ReferenceContractTransition) o;

        return myLink == that.myLink;
    }

    @Override
    public int hashCode() {
        return myLink;
    }
}