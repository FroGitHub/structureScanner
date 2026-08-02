package frog.structurescanner

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.ui.jcef.JBCefBrowser
import frog.structurescanner.call.CallGraphBuilder
import frog.structurescanner.mapper.PsiMethodMapper
import frog.structurescanner.mermaid.MermaidRenderer
import frog.structurescanner.methods.MethodResolver
import frog.structurescanner.methods.MethodScanner
import frog.structurescanner.methods.MethodSourceResolver
import frog.structurescanner.model.MethodArgumentResolver

class GraphToolWindowController(
    private val project: Project
) {

    private var rootMethod: PsiMethod? = null
    private var browser: JBCefBrowser? = null

    fun setBrowser(browser: JBCefBrowser) {
        this.browser = browser
    }

    fun setMethod(method: PsiMethod) {
        rootMethod = method
        refresh()
    }

    fun refresh() {
        val method = rootMethod ?: return
        val browser = browser ?: return

        val scanner = MethodScanner(
            MethodResolver()
        )

        val mapper = PsiMethodMapper(
            MethodSourceResolver()
        )

        val builder = CallGraphBuilder(
            scanner = scanner,
            mapper = mapper,
            argumentResolver = MethodArgumentResolver()
        )

        val graph = builder.build(
            root = method,
            maxDepth = 5
        )

        val mermaid = MermaidRenderer()
            .render(graph)

        browser.loadHTML(
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
            </head>

            <body>
                <div class="mermaid">
                    $mermaid
                </div>

                <script>
                    mermaid.initialize({
                        startOnLoad: true
                    });
                </script>
            </body>
            </html>
            """.trimIndent()
        )
    }
}
