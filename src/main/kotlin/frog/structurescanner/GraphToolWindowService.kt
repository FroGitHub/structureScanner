package frog.structurescanner

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.ui.jcef.JBCefBrowser

@Service(Service.Level.PROJECT)
class GraphToolWindowService(
    private val project: Project
) {

    private val controller = GraphToolWindowController(project)

    fun setBrowser(browser: JBCefBrowser) {
        controller.setBrowser(browser)
    }

    fun setMethod(method: PsiMethod) {
        controller.setMethod(method)
    }

    fun refresh() {
        controller.refresh()
    }
}
