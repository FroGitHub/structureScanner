package frog.structurescanner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import frog.structurescanner.call.CallGraphBuilder
import frog.structurescanner.mapper.PsiMethodMapper
import frog.structurescanner.methods.MethodResolver
import frog.structurescanner.methods.MethodScanner
import frog.structurescanner.methods.MethodSourceResolver
import frog.structurescanner.mermaid.MermaidRenderer
import frog.structurescanner.model.MethodArgumentResolver


class AnalyzeMethodAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

        val psiFile = PsiDocumentManager
            .getInstance(project)
            .getPsiFile(editor.document)
            ?: return

        val element = psiFile.findElementAt(editor.caretModel.offset)
            ?: return

        val method = PsiTreeUtil.getParentOfType(
            element,
            PsiMethod::class.java
        ) ?: return

        val scanner = MethodScanner(
            MethodResolver()
        )

        val mapper = PsiMethodMapper(
            MethodSourceResolver()
        )

        val graphBuilder = CallGraphBuilder(
            scanner,
            mapper,
            MethodArgumentResolver()
        )

        val graph = graphBuilder.build(method)

        println(graph)

        val renderer = MermaidRenderer()
        val mermaid = renderer.render(graph)

        println(mermaid)
    }
}
