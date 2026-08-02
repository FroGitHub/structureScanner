package frog.structurescanner.mapper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import frog.structurescanner.methods.MethodSourceResolver
import frog.structurescanner.model.MethodNode
import frog.structurescanner.model.MethodSource

class PsiMethodMapper(
    private val sourceResolver: MethodSourceResolver
) {

    fun toNode(
        method: PsiMethod,
        displayClass: PsiClass? = null
    ): MethodNode {

        val className =
            displayClass?.qualifiedName
                ?: method.containingClass?.qualifiedName
                ?: "<unknown>"

        return MethodNode(
            id = createId(
                method = method,
                className = className
            ),
            className = className,
            methodName = method.name,
            parameters = method.parameterList.parameters
                .map { it.type.canonicalText },
            returnType = method.returnType?.canonicalText ?: "void",
            source = sourceResolver.resolve(method)
        )
    }

    private fun createId(
        method: PsiMethod,
        className: String
    ): String {

        val parameters =
            method.parameterList.parameters
                .joinToString(",") {
                    it.type.canonicalText
                }

        return "$className#${method.name}($parameters)"
    }
}
