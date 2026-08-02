package frog.structurescanner.methods

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.InheritanceUtil
import frog.structurescanner.model.ResolvedMethodCall
import frog.structurescanner.model.CallType

class MethodResolver {

    fun resolveInheritedMethods(
        method: PsiMethod
    ): List<ResolvedMethodCall> {

        return method.findSuperMethods().map {
            ResolvedMethodCall(
                method = it,
                callType = CallType.INHERITED
            )
        }
    }

    fun resolveImplementations(
        method: PsiMethod,
        qualifierClass: PsiClass? = null
    ): List<ResolvedMethodCall> {

        // =========================
        // Spring Data Repository
        // =========================

        if (isSpringDataRepository(qualifierClass)) {
            return listOf(
                ResolvedMethodCall(
                    method = method,
                    callType = CallType.IMPLEMENTATION,
                    displayClass = qualifierClass
                )
            )
        }

        // =========================
        // External / library method
        // =========================

        if (method.containingFile?.virtualFile?.path
                ?.contains("/src/main/") != true) {

            return listOf(
                ResolvedMethodCall(
                    method = method,
                    callType = CallType.DIRECT
                )
            )
        }

        // =========================
        // Project implementation
        // =========================

        val overridingMethods =
            OverridingMethodsSearch
                .search(method)
                .findAll()

        if (overridingMethods.isEmpty()) {
            return listOf(
                ResolvedMethodCall(
                    method = method,
                    callType = CallType.DIRECT
                )
            )
        }

        return overridingMethods.map {
            ResolvedMethodCall(
                method = it,
                callType = CallType.IMPLEMENTATION
            )
        }
    }

    private fun resolveRepositoryMethod(
        method: PsiMethod,
        repositoryClass: PsiClass
    ): PsiMethod {

        if (method.containingClass == repositoryClass) {
            return method
        }

        return repositoryClass
            .findMethodsByName(
                method.name,
                true
            )
            .firstOrNull {
                it.parameterList.parametersCount ==
                        method.parameterList.parametersCount
            }
            ?: method
    }

    private fun isSpringDataRepository(
        psiClass: PsiClass?
    ): Boolean {

        if (psiClass == null) {
            return false
        }

        return InheritanceUtil.isInheritor(
            psiClass,
            "org.springframework.data.repository.Repository"
        )
    }
}
