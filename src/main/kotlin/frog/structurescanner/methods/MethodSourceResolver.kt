package frog.structurescanner.methods

import com.intellij.psi.PsiMethod
import frog.structurescanner.model.MethodSource

class MethodSourceResolver {

    fun resolve(method: PsiMethod): MethodSource {
        val virtualFile = method.containingFile?.virtualFile
            ?: return MethodSource.LIBRARY

        return when {
            virtualFile.path.contains("/src/main/") ||
            virtualFile.path.contains("/src/test/") ->
                MethodSource.PROJECT

            virtualFile.path.contains(".m2") ||
            virtualFile.path.contains("gradle") ->
                MethodSource.LIBRARY

            else ->
                MethodSource.LIBRARY
        }
    }
}
