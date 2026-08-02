package frog.structurescanner.methods

import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.util.InheritanceUtil
import frog.structurescanner.model.ResolvedMethodCall
import frog.structurescanner.model.CallType

class MethodScanner(
    private val methodResolver: MethodResolver
) {

    fun scan(method: PsiMethod): List<ResolvedMethodCall> {

        val result = mutableListOf<ResolvedMethodCall>()

        method.accept(
            object : JavaRecursiveElementVisitor() {

                override fun visitMethodCallExpression(
                    expression: PsiMethodCallExpression
                ) {
                    super.visitMethodCallExpression(expression)

                    val resolvedMethod =
                        expression.resolveMethod()
                            ?: return

                    val qualifierClass =
                        (expression.methodExpression.qualifierExpression?.type
                                as? PsiClassType)
                            ?.resolve()

                    val resolvedCalls =
                        methodResolver.resolveImplementations(
                            method = resolvedMethod,
                            qualifierClass = qualifierClass
                        )

                    result.addAll(
                        resolvedCalls.map {
                            it.copy(
                                expression = expression
                            )
                        }
                    )
                }

                override fun visitMethodReferenceExpression(
                    expression: PsiMethodReferenceExpression
                ) {
                    super.visitMethodReferenceExpression(expression)

                    val resolvedMethod =
                        expression.resolve()
                                as? PsiMethod
                            ?: return

                    val resolvedCalls =
                        methodResolver.resolveImplementations(
                            method = resolvedMethod,
                            qualifierClass = null
                        )

                    result.addAll(
                        resolvedCalls.map {
                            it.copy(
                                callType = CallType.METHOD_REFERENCE
                            )
                        }
                    )
                }
            }
        )

        return result
    }
}
