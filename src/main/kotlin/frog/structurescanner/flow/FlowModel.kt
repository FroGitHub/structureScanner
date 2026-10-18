package frog.structurescanner.flow

import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer

enum class NodeKind { START, ACTION, DECISION, RETURN, THROW }

class FlowNode(val id: String, val kind: NodeKind, var label: String, val scope: String)
class FlowEdge(val from: String, val to: String, val label: String?)
class FlowScope(val id: String, val title: String)

data class FlowOptions(
    val maxDepth: Int = 2,
    val hideThrows: Boolean = false,
    val collapseChains: Boolean = true,
    val boundaryRegex: Regex? = null      // null = показувати все
)

class FlowGraph {
    val nodes = mutableListOf<FlowNode>()
    val edges = mutableListOf<FlowEdge>()
    val scopes = mutableListOf<FlowScope>()

    /** nodeId -> метод, у який можна зайти кліком */
    val drill = mutableMapOf<String, SmartPsiElementPointer<PsiMethod>>()

    fun outgoing(id: String) = edges.filter { it.from == id }
    fun incoming(id: String) = edges.filter { it.to == id }

    fun removeNode(n: FlowNode) {
        nodes.remove(n)
        edges.removeAll { it.from == n.id || it.to == n.id }
        drill.remove(n.id)
    }

    /** Прибирає вузол, з'єднуючи його вхідні ребра з вихідними */
    fun bypass(n: FlowNode) {
        val ins = incoming(n.id)
        val outs = outgoing(n.id)
        val added = ins.flatMap { i -> outs.map { o -> FlowEdge(i.from, o.to, i.label ?: o.label) } }
        removeNode(n)
        added.distinctBy { Triple(it.from, it.to, it.label) }.forEach { edges += it }
    }
}
