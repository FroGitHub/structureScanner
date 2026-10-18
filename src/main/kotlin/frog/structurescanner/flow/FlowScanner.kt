package frog.structurescanner.flow

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.PsiUtil

data class Exit(val node: FlowNode, val label: String? = null)

class Fragment(
    val entry: FlowNode?,
    val exits: List<Exit>,
    val returns: List<Exit> = emptyList()
)

class FlowScanner(private val opts: FlowOptions) {

    private val graph = FlowGraph()
    private var counter = 0

    private data class Ctx(val scope: FlowScope, val depth: Int, val stack: Set<PsiMethod>)

    fun scan(root: PsiMethod): FlowGraph {
        val scope = newScope(root)
        val start = node(NodeKind.START, title(root), scope)
        val body = body(root, Ctx(scope, 0, setOf(root)))
        link(listOf(Exit(start)), body.entry)
        prune(start)
        FlowSimplifier.apply(graph, opts)
        return graph
    }

    // ---------- statements ----------

    private fun body(m: PsiMethod, c: Ctx): Fragment =
        m.body?.let { block(it, c) } ?: EMPTY

    private fun block(b: PsiCodeBlock, c: Ctx): Fragment =
        b.statements.fold(EMPTY) { acc, s -> seq(acc, stmt(s, c)) }

    private fun stmt(s: PsiStatement, c: Ctx): Fragment = when (s) {
        is PsiBlockStatement  -> block(s.codeBlock, c)
        is PsiIfStatement     -> ifStmt(s, c)
        is PsiThrowStatement  -> throwStmt(s, c)
        is PsiReturnStatement -> returnStmt(s, c)
        is PsiTryStatement    -> tryStmt(s, c)
        is PsiLoopStatement   -> loopStmt(s, c)
        else                  -> calls(s, c)      // switch поки що плоско
    }

    private fun ifStmt(s: PsiIfStatement, c: Ctx): Fragment {
        val t = s.thenBranch?.let { stmt(it, c) } ?: EMPTY
        val e = s.elseBranch?.let { stmt(it, c) } ?: EMPTY
        if (t.entry == null && e.entry == null) return calls(s.condition, c)

        val d = node(NodeKind.DECISION, s.condition?.text.orEmpty(), c.scope)
        val exits = mutableListOf<Exit>()
        for ((frag, label) in listOf(t to "Так", e to "Ні")) {
            if (frag.entry != null) {
                link(listOf(Exit(d, label)), frag.entry)
                exits += frag.exits
            } else exits += Exit(d, label)
        }
        return seq(calls(s.condition, c), Fragment(d, exits, t.returns + e.returns))
    }

    private fun loopStmt(s: PsiLoopStatement, c: Ctx): Fragment {
        val body = s.body?.let { stmt(it, c) } ?: EMPTY
        if (body.entry == null) return EMPTY

        val text = when (s) {
            is PsiForeachStatement -> "for each ${s.iterationParameter.name} in ${s.iteratedValue?.text}"
            is PsiWhileStatement   -> "while ${s.condition?.text}"
            is PsiDoWhileStatement -> "do-while ${s.condition?.text}"
            is PsiForStatement     -> "for ${s.condition?.text ?: "…"}"
            else                   -> "loop"
        }
        val d = node(NodeKind.DECISION, text, c.scope)
        link(listOf(Exit(d, "Так")), body.entry)
        link(body.exits, d)                                  // ребро назад
        return Fragment(d, listOf(Exit(d, "Ні")), body.returns)
    }

    private fun throwStmt(s: PsiThrowStatement, c: Ctx): Fragment =
        Fragment(node(NodeKind.THROW, "Throw " + s.exception?.text.orEmpty(), c.scope), emptyList())

    private fun returnStmt(s: PsiReturnStatement, c: Ctx): Fragment {
        val pre = calls(s.returnValue, c)
        val r = node(NodeKind.RETURN, "Return " + s.returnValue?.text.orEmpty(), c.scope)
        return seq(pre, Fragment(r, emptyList(), listOf(Exit(r))))
    }

    private fun tryStmt(s: PsiTryStatement, c: Ctx): Fragment {
        val tryF = s.tryBlock?.let { block(it, c) } ?: EMPTY
        val catches = s.catchSections.map { sec ->
            (sec.catchType?.presentableText ?: "Exception") to
                    (sec.catchBlock?.let { block(it, c) } ?: EMPTY)
        }.filter { it.second.entry != null }
        if (tryF.entry == null || catches.isEmpty()) return tryF

        val d = node(NodeKind.DECISION, "Виняток: ${catches.joinToString { it.first }}?", c.scope)
        link(tryF.exits, d)
        val exits = mutableListOf(Exit(d, "Ні"))
        var returns = tryF.returns
        catches.forEach { (type, f) ->
            link(listOf(Exit(d, "Так ($type)")), f.entry)
            exits += f.exits
            returns = returns + f.returns
        }
        return Fragment(tryF.entry, exits, returns)
    }

    // ---------- calls ----------

    private fun calls(e: PsiElement?, c: Ctx): Fragment {
        if (e == null) return EMPTY
        return collectCalls(e).fold(EMPTY) { acc, call -> seq(acc, callFragment(call, c)) }
    }

    // post-order: qualifier → аргументи → сам виклик
    private fun collectCalls(root: PsiElement): List<PsiMethodCallExpression> {
        val result = mutableListOf<PsiMethodCallExpression>()
        root.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                result += expression
            }
        })
        return result
    }

    private fun callFragment(call: PsiMethodCallExpression, c: Ctx): Fragment {
        val target = call.resolveMethod() ?: return EMPTY
        if (!isProjectCode(target)) return EMPTY
        if (isTrivialGetter(target)) return EMPTY

        if (isTrivialSetter(target)) {
            val n = node(NodeKind.ACTION, setterLabel(call, target), c.scope)
            return Fragment(n, listOf(Exit(n)))
        }

        val label = title(target)
        val impls = resolveTargets(target)
        if (impls.isNotEmpty() && impls.all(::isTrivialGetter)) return EMPTY

        return when (impls.size) {
            0 -> leaf(label, null, c)
            1 -> expandOrLeaf(label, impls.single(), c)
            else -> {
                val d = node(NodeKind.DECISION, "$label: яка реалізація?", c.scope)
                val exits = mutableListOf<Exit>()
                impls.forEach { impl ->
                    val f = expandOrLeaf(title(impl), impl, c)
                    link(listOf(Exit(d, impl.containingClass?.name)), f.entry)
                    exits += f.exits
                }
                Fragment(d, exits)
            }
        }
    }

    private fun expandOrLeaf(label: String, m: PsiMethod, c: Ctx): Fragment {
        val recursive = m in c.stack
        return if (recursive || c.depth >= opts.maxDepth)
            leaf(label, m.takeIf { !recursive && it.body != null }, c)
        else inline(label, m, c)
    }

    /** Вузол без розкриття; якщо є куди зайти — його можна клікнути */
    private fun leaf(label: String, drillInto: PsiMethod?, c: Ctx): Fragment {
        val n = node(NodeKind.ACTION, label, c.scope)
        if (drillInto != null) graph.drill[n.id] = SmartPointerManager.createPointer(drillInto)
        return Fragment(n, listOf(Exit(n)))
    }

    private fun inline(label: String, m: PsiMethod, c: Ctx): Fragment {
        val action = node(NodeKind.ACTION, label, c.scope)
        val inner = body(m, Ctx(newScope(m), c.depth + 1, c.stack + m))
        link(listOf(Exit(action)), inner.entry)
        return Fragment(action, if (inner.entry == null) listOf(Exit(action)) else inner.exits + inner.returns)
    }

    private fun resolveTargets(m: PsiMethod): List<PsiMethod> =
        if (m.containingClass?.isInterface == true || m.hasModifierProperty(PsiModifier.ABSTRACT))
            OverridingMethodsSearch.search(m).findAll().filter(::isProjectCode)
        else listOf(m)

    private fun isProjectCode(m: PsiMethod): Boolean {
        val vf = m.containingFile?.virtualFile ?: return false
        return ProjectFileIndex.getInstance(m.project).isInSourceContent(vf)
    }

    // ---------- getters / setters ----------

    private fun isTrivialGetter(m: PsiMethod): Boolean {
        if (m.isConstructor || m.parameterList.parametersCount != 0) return false
        val body = m.body
            ?: return !m.hasModifierProperty(PsiModifier.ABSTRACT) && !m.hasModifierProperty(PsiModifier.NATIVE)
        val ret = body.statements.singleOrNull() as? PsiReturnStatement ?: return false
        return isFieldRef(ret.returnValue)
    }

    private fun isTrivialSetter(m: PsiMethod): Boolean {
        if (m.isConstructor || m.parameterList.parametersCount != 1) return false
        val body = m.body
            ?: return !m.hasModifierProperty(PsiModifier.ABSTRACT) && !m.hasModifierProperty(PsiModifier.NATIVE)
        val assign = (body.statements.singleOrNull() as? PsiExpressionStatement)
            ?.expression as? PsiAssignmentExpression ?: return false
        return isFieldRef(assign.lExpression) && isParamRef(assign.rExpression)
    }

    private fun isFieldRef(e: PsiExpression?) =
        (PsiUtil.skipParenthesizedExprDown(e) as? PsiReferenceExpression)?.resolve() is PsiField

    private fun isParamRef(e: PsiExpression?) =
        (PsiUtil.skipParenthesizedExprDown(e) as? PsiReferenceExpression)?.resolve() is PsiParameter

    private fun setterLabel(call: PsiMethodCallExpression, target: PsiMethod): String {
        val qualifier = call.methodExpression.qualifierExpression?.text?.plus(".") ?: ""
        val args = call.argumentList.expressions
        val simple = args.all { it is PsiLiteralExpression || it is PsiReferenceExpression }
        return "$qualifier${target.name}(${if (simple) args.joinToString { it.text } else "…"})"
    }

    // ---------- graph helpers ----------

    private fun link(from: List<Exit>, to: FlowNode?) {
        if (to == null) return
        from.forEach { graph.edges += FlowEdge(it.node.id, to.id, it.label) }
    }

    private fun seq(a: Fragment, b: Fragment): Fragment = when {
        b.entry == null -> a
        a.entry == null -> b
        a.exits.isEmpty() -> a                       // після throw/return код недосяжний
        else -> { link(a.exits, b.entry); Fragment(a.entry, b.exits, a.returns + b.returns) }
    }

    private fun node(kind: NodeKind, label: String, scope: FlowScope) =
        FlowNode("n${counter++}", kind, label, scope.id).also { graph.nodes += it }

    private fun newScope(m: PsiMethod) =
        FlowScope("s${counter++}", title(m)).also { graph.scopes += it }

    private fun title(m: PsiMethod) = "${m.containingClass?.name}.${m.name}"

    private fun prune(start: FlowNode) {
        val reached = mutableSetOf(start.id)
        val queue = ArrayDeque(listOf(start.id))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            graph.edges.filter { it.from == id }.forEach { if (reached.add(it.to)) queue.add(it.to) }
        }
        graph.nodes.removeAll { it.id !in reached }
        graph.edges.removeAll { it.from !in reached || it.to !in reached }
        graph.drill.keys.retainAll(reached)
    }

    private companion object {
        val EMPTY = Fragment(null, emptyList())
    }
}
