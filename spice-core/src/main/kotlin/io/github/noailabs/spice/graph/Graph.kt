package io.github.noailabs.spice.graph

import io.github.noailabs.spice.Identifiable
import io.github.noailabs.spice.graph.middleware.Middleware

/**
 * Represents a directed graph of nodes.
 * By default enforces acyclic (DAG) structure, but can allow cycles when needed.
 * Implements Identifiable to allow graph registration in GraphRegistry.
 */
data class Graph(
    override val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList(),
    val allowCycles: Boolean = false
) : Identifiable

/**
 * Represents a directed edge between two nodes.
 */
data class Edge(
    val from: String,
    val to: String,
    val condition: (NodeResult) -> Boolean = { true }
)
