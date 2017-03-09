package org.jetbrains.ruby.codeInsight.types.signature.contractTransition;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public interface ContractTransition {
    /**
     * Return literal type set of this transition. This method respects reference transitions
     * which types depend on some previous passed values.
     *
     * @param readTypes previously read literal types. Set represents possible type unions
     * @return computed literal type set for this transition
     */
    @NotNull
    Set<String> getValue(@NotNull List<Set<String>> readTypes);
}
