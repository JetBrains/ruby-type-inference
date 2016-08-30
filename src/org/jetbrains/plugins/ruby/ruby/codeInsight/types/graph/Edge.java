package org.jetbrains.plugins.ruby.ruby.codeInsight.types.graph;

import org.jetbrains.annotations.NotNull;

public class Edge<T> {
    @NotNull
    private final Node<T> myFrom;
    @NotNull
    private final Node<T> myTo;

    private final int weight;

    public Edge(@NotNull final Node<T> fromNode, @NotNull final Node<T> toNode, final int weight) {
        this.myFrom = fromNode;
        this.myTo = toNode;
        this.weight = weight;
    }

    @NotNull
    public Node<T> getFrom() {
        return myFrom;
    }

    @NotNull
    public Node<T> getTo() {
        return myTo;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isBetween(@NotNull final Node<T> from, @NotNull final Node<T> to) {
        return (this.myFrom == from && this.myTo == to);
    }
}
