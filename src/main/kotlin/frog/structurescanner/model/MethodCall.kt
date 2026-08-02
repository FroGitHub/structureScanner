package frog.structurescanner.model

data class MethodCall(
    val from: MethodNode,
    val to: MethodNode,
    val callType: CallType,

    val arguments: List<MethodArgument> = emptyList()
)
