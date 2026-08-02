package frog.structurescanner.model

data class MethodArgument(
    val name: String,
    val type: String,
    val fields: List<MethodArgumentField> = emptyList()
)

data class MethodArgumentField(
    val name: String,
    val type: String
)
