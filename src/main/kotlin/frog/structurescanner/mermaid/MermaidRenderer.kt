package frog.structurescanner.mermaid

import frog.structurescanner.call.CallGraph
import frog.structurescanner.model.CallType
import frog.structurescanner.model.MethodCall
import frog.structurescanner.model.MethodNode

class MermaidRenderer {

    fun render(
        graph: CallGraph,
        visibleCallTypes: Set<CallType> = CallType.entries.toSet(),
        compact: Boolean = false,
        showArgumentTypes: Boolean = true,
        showArgumentFields: Boolean = true
    ): String {

        val result = StringBuilder()

        result.appendLine("flowchart TD")

        val visibleCalls =
            graph.calls.filter {
                it.callType in visibleCallTypes
            }

        // =========================
        // Visible nodes
        // =========================

        val visibleNodes = buildSet {

            visibleCalls.forEach { call ->
                add(call.from)
                add(call.to)
            }

            // Always keep root
            graph.nodes.firstOrNull()?.let {
                add(it)
            }
        }

        visibleNodes.forEach { node ->

            val id = mermaidId(node)

            val nodeLabel =
                label(
                    node = node,
                    compact = compact
                )

            result.appendLine(
                "    $id[\"${escape(nodeLabel)}\"]"
            )
        }

        // =========================
        // Visible calls
        // =========================

        visibleCalls.forEach { call ->

            val fromId =
                mermaidId(call.from)

            val toId =
                mermaidId(call.to)

            val edgeLabel =
                buildEdgeLabel(
                    call = call,
                    showArgumentTypes = showArgumentTypes,
                    showArgumentFields = showArgumentFields
                )

            result.appendLine(
                "    $fromId -->|\"$edgeLabel\"| $toId"
            )
        }

        return result.toString()
    }

    private fun buildEdgeLabel(
        call: MethodCall,
        showArgumentTypes: Boolean,
        showArgumentFields: Boolean
    ): String {

        if (call.arguments.isEmpty()) {
            return escape(call.callType.name)
        }

        val argumentText =
            call.arguments.joinToString("<br/>") { argument ->

                val fields =
                    if (showArgumentFields && argument.fields.isNotEmpty()) {
                        argument.fields.joinToString(", ") {
                            "${it.name}: ${it.type}"
                        }
                    } else {
                        null
                    }

                val argument =
                    when {
                        showArgumentTypes && fields != null ->
                            "${argument.name}: ${argument.type} [$fields]"

                        showArgumentTypes ->
                            "${argument.name}: ${argument.type}"

                        fields != null ->
                            "${argument.name} [$fields]"

                        else ->
                            argument.name
                    }

                escape(argument)
            }

        return "${escape(call.callType.name)}<br/>$argumentText"
    }

    private fun mermaidId(node: MethodNode): String {
        return "node_" + node.id.hashCode().toUInt().toString(16)
    }

    private fun label(
        node: MethodNode,
        compact: Boolean
    ): String {

        val className =
            node.className.substringAfterLast(".")

        if (compact) {
            return "$className.${node.methodName}()"
        }

        val parameters =
            node.parameters.joinToString(", ") {
                formatType(it)
            }

        val returnType =
            formatType(node.returnType)

        return "$className.${node.methodName}($parameters): $returnType"
    }

    private fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun formatType(type: String): String {
        return type
            .replace(Regex("""\b[\w.]+\.""")) {
                it.value.substringAfterLast(".")
            }
    }
}
