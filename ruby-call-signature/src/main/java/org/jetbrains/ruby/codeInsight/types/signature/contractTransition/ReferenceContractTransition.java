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
}