package frog.structurescanner.model

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter

class MethodArgumentResolver {

    fun resolve(
        call: PsiMethodCallExpression,
        targetMethod: PsiMethod
    ): List<MethodArgument> {

        val parameters =
            targetMethod.parameterList.parameters

        val arguments =
            call.argumentList.expressions

        return parameters.mapIndexedNotNull { index, parameter ->

            val expression =
                arguments.getOrNull(index)
                    ?: return@mapIndexedNotNull null

            val type =
                parameter.type.presentableText

            val fields =
                resolveFields(
                    expression = expression,
                    parameter = parameter
                )

            MethodArgument(
                name = parameter.name,
                type = type,
                fields = fields
            )
        }
    }

    private fun resolveFields(
        expression: PsiExpression,
        parameter: PsiParameter
    ): List<MethodArgumentField> {

        val type =
            parameter.type as? PsiClassType
                ?: return emptyList()

        val psiClass =
            type.resolve()
                ?: return emptyList()

        return psiClass.allFields
            .filterNot {
                it.hasModifierProperty(PsiModifier.STATIC)
            }
            .map {
                MethodArgumentField(
                    name = it.name,
                    type = it.type.presentableText
                )
            }
    }
}
