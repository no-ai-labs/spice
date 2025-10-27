package io.github.noailabs.spice.graph

import io.github.noailabs.spice.graph.middleware.Middleware

/**
 * Represents a directed acyclic graph (DAG) of nodes.
 */
data class Graph(
    val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String,
    val middleware: List<Middleware> = emptyList()
)

/**
 * Represents a directed edge between two nodes.
 */
data class Edge(
    val from: String,
    val to: String,
    val condition: (NodeResult) -> Boolean = { true }
)
