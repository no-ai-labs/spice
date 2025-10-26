package io.github.noailabs.spice.graph

/**
 * Represents a directed acyclic graph (DAG) of nodes.
 */
data class Graph(
    val id: String,
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val entryPoint: String
)

/**
 * Represents a directed edge between two nodes.
 */
data class Edge(
    val from: String,
    val to: String,
    val condition: (NodeResult) -> Boolean = { true }
)
