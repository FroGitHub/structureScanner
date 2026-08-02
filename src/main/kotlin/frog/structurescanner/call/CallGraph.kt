package frog.structurescanner.call

import frog.structurescanner.model.MethodCall
import frog.structurescanner.model.MethodNode

data class CallGraph(
    val nodes: Set<MethodNode>,
    val calls: Set<MethodCall>
)
