package org.jetbrains.ruby.codeInsight.types.signature.contractTransition;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReferenceContractTransition implements ContractTransition {

    private final int myMask;

    public ReferenceContractTransition(int mask) {
        myMask = mask;
    }

    @NotNull
    @Override
    public Set<String> getValue(@NotNull List<Set<String>> readTypes) {
        int tmpMask = myMask;
        int cnt = 0;

        Set<String> ans = null;

        while (tmpMask > 0) {
            if (tmpMask % 2 == 1) {
                if (ans == null) {
                    ans = new HashSet<>();
                    ans.addAll(readTypes.get(cnt));
                } else {
                    ans.retainAll(readTypes.get(cnt));
                }
            }

            tmpMask /= 2;
            cnt++;
        }

        return ans;
    }

    public int getMask() {
        return myMask;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReferenceContractTransition that = (ReferenceContractTransition) o;

        return myMask == that.myMask;
    }

    @Override
    public int hashCode() {
        return myMask;
    }
}