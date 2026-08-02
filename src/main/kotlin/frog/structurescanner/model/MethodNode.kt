package frog.structurescanner.model

data class MethodNode(
    val id: String,
    val className: String,
    val methodName: String,
    val parameters: List<String>,
    val returnType: String,
    val source: MethodSource
)

enum class MethodSource {
    PROJECT,
    LIBRARY,
    JDK
}
