package org.jetbrains.ruby.codeInsight.types.signature.contractTransition;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TransitionHelper {
    private TransitionHelper() {
    }

    @NotNull
    public static ContractTransition calculateTransition(@NotNull List<String> argTypes, int argIndex, @NotNull String type) {
        final int mask = getNewMask(argTypes, argIndex, type);

        if (mask > 0)
            return new ReferenceContractTransition(mask);
        else
            return new TypedContractTransition(type);
    }

    private static int getNewMask(@NotNull List<String> argsTypes, int argIndex, @NotNull String type) {
        int tempMask = 0;

        for (int i = argIndex - 1; i >= 0; i--) {
            tempMask <<= 1;

            if (argsTypes.get(i).equals(type)) {
                tempMask |= 1;
            }
        }

        return tempMask;
    }
}
