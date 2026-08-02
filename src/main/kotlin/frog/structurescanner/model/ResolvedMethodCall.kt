package frog.structurescanner.model

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression

data class ResolvedMethodCall(
    val method: PsiMethod,
    val callType: CallType,
    val expression: PsiMethodCallExpression? = null,
    val displayClass: PsiClass? = null
)
