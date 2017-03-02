package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.Nullable;

public final class Pair<A, B> {
    @Nullable
    private final A myFirst;
    @Nullable
    private final B mySecond;

    public Pair(@Nullable A first, @Nullable B second) {
        myFirst = first;
        mySecond = second;
    }

    public A getFirst() {
        return myFirst;
    }

    public B getSecond() {
        return mySecond;
    }
}
