package frog.structurescanner.call

import com.intellij.psi.PsiMethod
import frog.structurescanner.mapper.PsiMethodMapper
import frog.structurescanner.methods.MethodScanner
import frog.structurescanner.model.MethodArgumentResolver
import frog.structurescanner.model.MethodCall
import frog.structurescanner.model.MethodNode

class CallGraphBuilder(
    private val scanner: MethodScanner,
    private val mapper: PsiMethodMapper,
    private val argumentResolver: MethodArgumentResolver
) {

    fun build(
        root: PsiMethod,
        maxDepth: Int = 5
    ): CallGraph {

        val nodes = mutableSetOf<MethodNode>()
        val calls = mutableSetOf<MethodCall>()
        val visited = mutableSetOf<String>()

        traverse(
            method = root,
            depth = 0,
            maxDepth = maxDepth,
            nodes = nodes,
            calls = calls,
            visited = visited
        )

        return CallGraph(
            nodes = nodes,
            calls = calls
        )
    }

    private fun traverse(
        method: PsiMethod,
        depth: Int,
        maxDepth: Int,
        nodes: MutableSet<MethodNode>,
        calls: MutableSet<MethodCall>,
        visited: MutableSet<String>
    ) {
        if (depth > maxDepth) {
            return
        }

        val node = mapper.toNode(method)

        if (!visited.add(node.id)) {
            return
        }

        nodes.add(node)

        val resolvedCalls =
            scanner.scan(method)

        for (resolvedCall in resolvedCalls) {

            val calledMethod =
                resolvedCall.method

            val calledNode =
                mapper.toNode(calledMethod)

            nodes.add(calledNode)

            val arguments =
                resolvedCall.expression
                    ?.let { expression ->
                        argumentResolver.resolve(
                            call = expression,
                            targetMethod = calledMethod
                        )
                    }
                    ?: emptyList()

            calls.add(
                MethodCall(
                    from = node,
                    to = calledNode,
                    callType = resolvedCall.callType,
                    arguments = arguments
                )
            )

            traverse(
                method = calledMethod,
                depth = depth + 1,
                maxDepth = maxDepth,
                nodes = nodes,
                calls = calls,
                visited = visited
            )
        }
    }
}
