package org.jetbrains.plugins.ruby.ruby.codeInsight.types.graph;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Node<T> {
    @NotNull
    private final T myVertex;
    @NotNull
    private final List<Edge<T>> myEdges = new ArrayList<>();

    public Node(@NotNull final T vertex) {
        this.myVertex = vertex;
    }

    @NotNull
    public T getVertex() {
        return myVertex;
    }

    @NotNull
    public List<Edge<T>> getEdges() {
        return myEdges;
    }

    public boolean hasEdgeTo(@NotNull final Node<T> node) {
        return findEdge(node).isPresent();
    }

    public boolean addEdgeTo(@NotNull final Node<T> node, final int weight) {
        if (hasEdgeTo(node)) {
            return false;
        }

        final Edge<T> newEdge = new Edge<>(this, node, weight);
        return myEdges.add(newEdge);
    }

    public boolean removeEdge(@NotNull final Edge<T> edge) {
        return myEdges.remove(edge);
    }

    public boolean removeEdgeTo(@NotNull final Node<T> node) {
        final Optional<Edge<T>> optional = findEdge(node);
        if (optional.isPresent()) {
            return myEdges.remove(optional.get());
        }

        return false;
    }

    @NotNull
    private Optional<Edge<T>> findEdge(@NotNull final Node<T> node) {
        return myEdges.stream()
                .filter(edge -> edge.isBetween(this, node))
                .findFirst();
    }
}
