package frog.structurescanner.flow

class FlowMermaidMapper {

    fun render(g: FlowGraph): String = buildString {
        appendLine("flowchart TD")

        g.scopes.forEach { scope ->
            val nodes = g.nodes.filter { it.scope == scope.id }
            if (nodes.isEmpty()) return@forEach
            appendLine("    subgraph ${scope.id} [\"${esc(scope.title)}\"]")
            nodes.forEach { appendLine("        ${shape(it, g)}") }
            appendLine("    end")
        }

        g.edges.forEach { e ->
            val arrow = if (e.label != null) "-- \"${esc(e.label)}\" -->" else "-->"
            appendLine("    ${e.from} $arrow ${e.to}")
        }
    }

    private fun shape(n: FlowNode, g: FlowGraph): String {
        val body = n.label.split("\n").joinToString("<br/>") { esc(it) }
        val l = "\"${if (n.id in g.drill) "▸ $body" else body}\""
        return when (n.kind) {
            NodeKind.START, NodeKind.RETURN, NodeKind.THROW -> "${n.id}([$l])"
            NodeKind.ACTION   -> "${n.id}[$l]"
            NodeKind.DECISION -> "${n.id}{$l}"
        }
    }

    private fun esc(s: String) = s
        .replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;")
        .replace(Regex("\\s+"), " ").take(120)
}
