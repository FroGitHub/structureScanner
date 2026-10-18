package frog.structurescanner.flow

object FlowSimplifier {

    fun apply(g: FlowGraph, o: FlowOptions) {
        if (o.hideThrows) hideThrows(g)
        o.boundaryRegex?.let { boundaryOnly(g, it) }
        if (o.collapseChains) collapseChains(g)
    }

    // happy path: прибираємо throw і розвилки, що від цього стали безглуздими
    private fun hideThrows(g: FlowGraph) {
        g.nodes.filter { it.kind == NodeKind.THROW }.forEach { g.removeNode(it) }
        while (true) {
            val d = g.nodes.firstOrNull {
                it.kind == NodeKind.DECISION && g.outgoing(it.id).size < 2
            } ?: break
            if (g.outgoing(d.id).isEmpty()) g.removeNode(d) else g.bypass(d)
        }
    }

    // лишаємо лише виклики, що збігаються з патерном (Repository|Client|...)
    private fun boundaryOnly(g: FlowGraph, re: Regex) {
        g.nodes
            .filter { it.kind == NodeKind.ACTION && !re.containsMatchIn(it.label) }
            .forEach { g.bypass(it) }
    }

    // лінійні ланцюжки ACTION → ACTION в одному scope згортаємо в один вузол
    private fun collapseChains(g: FlowGraph) {
        while (true) {
            val merged = g.nodes.firstNotNullOfOrNull { a ->
                if (a.kind != NodeKind.ACTION || a.id in g.drill) return@firstNotNullOfOrNull null
                val e = g.outgoing(a.id).singleOrNull()?.takeIf { it.label == null }
                    ?: return@firstNotNullOfOrNull null
                val b = g.nodes.find { it.id == e.to } ?: return@firstNotNullOfOrNull null
                if (b.kind != NodeKind.ACTION || b.id in g.drill || b.scope != a.scope) return@firstNotNullOfOrNull null
                if (g.incoming(b.id).size != 1) return@firstNotNullOfOrNull null
                Triple(a, b, e)
            } ?: break

            val (a, b, e) = merged
            a.label += "\n" + b.label
            val rewired = g.outgoing(b.id).map { FlowEdge(a.id, it.to, it.label) }
            g.edges.remove(e)
            g.removeNode(b)
            g.edges += rewired
        }
    }
}
