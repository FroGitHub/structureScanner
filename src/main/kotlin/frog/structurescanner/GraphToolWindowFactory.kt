package frog.structurescanner

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.concurrency.AppExecutorUtil
import frog.structurescanner.flow.FlowMermaidMapper
import frog.structurescanner.flow.FlowOptions
import frog.structurescanner.flow.FlowScanner
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

class GraphToolWindowFactory : ToolWindowFactory {

    private enum class Mode { CARET, DRILL, BACK, RELOAD }

    private class Frame(val ptr: SmartPsiElementPointer<PsiMethod>, val title: String)

    private class RenderResult(
        val html: String,
        val targets: Map<String, SmartPsiElementPointer<PsiMethod>>,
        val root: SmartPsiElementPointer<PsiMethod>?,
        val title: String?
    )

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!JBCefApp.isSupported()) {
            toolWindow.contentManager.addContent(
                ContentFactory.getInstance().createContent(
                    JLabel("JCEF is not supported in this IDE runtime"), "", false
                )
            )
            return
        }

        val browser = JBCefBrowser()
        Disposer.register(toolWindow.disposable, browser)

        // ВАЖЛИВО: query створюємо до першого loadHTML
        val drillQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        Disposer.register(toolWindow.disposable, drillQuery)
        val bridge = "window.drill = function(id) { ${drillQuery.inject("id")} };"

        // ---------- controls ----------
        val backButton = JButton(AllIcons.Actions.Back).apply { toolTipText = "Back"; isEnabled = false }
        val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)
        val depthSpinner = JSpinner(SpinnerNumberModel(2, 0, 10, 1))
        val hideThrowsCb = JCheckBox("Hide throws")
        val collapseCb = JCheckBox("Collapse chains", true)
        val boundaryCb = JCheckBox("Boundaries only")
        val boundaryField = JTextField("Repository|Client|Gateway|Stripe", 18)
        val breadcrumb = JLabel(" ").apply { border = BorderFactory.createEmptyBorder(0, 10, 4, 10) }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            add(backButton)
            add(refreshButton)
            add(JLabel("Depth"))
            add(depthSpinner)
            add(hideThrowsCb)
            add(collapseCb)
            add(boundaryCb)
            add(boundaryField)
        }
        val north = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(breadcrumb, BorderLayout.SOUTH)
        }
        val panel = JPanel(BorderLayout()).apply {
            add(north, BorderLayout.NORTH)
            add(browser.component, BorderLayout.CENTER)
        }

        // ---------- state (тільки з EDT) ----------
        val stack = ArrayList<Frame>()
        var targets: Map<String, SmartPsiElementPointer<PsiMethod>> = emptyMap()
        var generation = 0

        fun readOptions(): FlowOptions {
            val regex = if (boundaryCb.isSelected)
                runCatching { Regex(boundaryField.text.trim(), RegexOption.IGNORE_CASE) }.getOrNull()
            else null
            return FlowOptions(
                maxDepth = depthSpinner.value as Int,
                hideThrows = hideThrowsCb.isSelected,
                collapseChains = collapseCb.isSelected,
                boundaryRegex = regex
            )
        }

        fun updateChrome() {
            backButton.isEnabled = stack.size > 1
            breadcrumb.text = if (stack.isEmpty()) " " else stack.joinToString("  ›  ") { it.title }
        }

        fun show(requested: Mode, nodeId: String? = null) {
            val mode = if (requested == Mode.RELOAD && stack.isEmpty()) Mode.CARET else requested

            val rootPtr: SmartPsiElementPointer<PsiMethod>? = when (mode) {
                Mode.CARET -> null
                Mode.DRILL -> targets[nodeId] ?: return
                Mode.BACK -> stack.getOrNull(stack.size - 2)?.ptr ?: return
                Mode.RELOAD -> stack.last().ptr
            }

            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (rootPtr == null && editor == null) {
                browser.loadHTML(messageHtml("Відкрийте файл і поставте курсор у метод"))
                return
            }

            val document = editor?.document
            val offset = editor?.caretModel?.offset ?: 0
            val opts = readOptions()
            val gen = ++generation

            browser.loadHTML(messageHtml("Будуємо діаграму…"))

            ReadAction
                .nonBlocking<RenderResult> { build(project, document, offset, rootPtr, opts, bridge) }
                .inSmartMode(project)
                .expireWith(toolWindow.disposable)
                .finishOnUiThread(ModalityState.any()) { r ->
                    if (gen != generation) return@finishOnUiThread

                    val ptr = r.root
                    val title = r.title
                    if (ptr != null && title != null) {
                        when (mode) {
                            Mode.CARET -> { stack.clear(); stack += Frame(ptr, title) }
                            Mode.DRILL -> stack += Frame(ptr, title)
                            Mode.BACK -> stack.removeAt(stack.lastIndex)
                            Mode.RELOAD -> {}
                        }
                    }
                    targets = r.targets
                    updateChrome()
                    browser.loadHTML(r.html)
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }

        // ---------- listeners ----------
        refreshButton.addActionListener { show(Mode.CARET) }
        backButton.addActionListener { show(Mode.BACK) }
        depthSpinner.addChangeListener { show(Mode.RELOAD) }
        listOf(hideThrowsCb, collapseCb, boundaryCb).forEach { it.addActionListener { show(Mode.RELOAD) } }
        boundaryField.addActionListener { if (boundaryCb.isSelected) show(Mode.RELOAD) }

        drillQuery.addHandler { nodeId ->
            ApplicationManager.getApplication().invokeLater { show(Mode.DRILL, nodeId) }
            null
        }

        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(panel, "", false)
        )

        show(Mode.CARET)
    }

    // ---------- background (read action) ----------

    private fun build(
        project: Project,
        document: Document?,
        offset: Int,
        rootPtr: SmartPsiElementPointer<PsiMethod>?,
        opts: FlowOptions,
        bridge: String
    ): RenderResult {
        return try {
            val method = if (rootPtr != null) rootPtr.element else methodAtCaret(project, document, offset)
                ?: return RenderResult(messageHtml("Поставте курсор у метод"), emptyMap(), null, null)
            method ?: return RenderResult(messageHtml("Метод більше не існує"), emptyMap(), null, null)

            val graph = FlowScanner(opts).scan(method)
            val mermaid = FlowMermaidMapper().render(graph)

            RenderResult(
                html = diagramHtml(mermaid, graph.drill.keys, bridge),
                targets = graph.drill.toMap(),
                root = SmartPointerManager.createPointer(method),
                title = "${method.containingClass?.name}.${method.name}"
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            RenderResult(
                messageHtml("Помилка: " + StringUtil.escapeXmlEntities(e.message ?: e.javaClass.name)),
                emptyMap(), null, null
            )
        }
    }

    private fun methodAtCaret(project: Project, document: Document?, offset: Int): PsiMethod? {
        document ?: return null
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val element = psiFile.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
    }

    // ---------- html ----------

    private fun messageHtml(text: String) =
        "<html><body style=\"font-family:sans-serif;color:#374151;padding:16px\">$text</body></html>"

    private fun diagramHtml(mermaid: String, drillable: Collection<String>, bridge: String): String {
        val js = mermaid.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
        val ids = drillable.joinToString(",", "[", "]") { "\"$it\"" }
        return HTML_TEMPLATE
            .replace("__BRIDGE__", bridge)
            .replace("__DRILL__", ids)
            .replace("__SOURCE__", js)
    }

    private companion object {
        val HTML_TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<style>
    html, body { width:100%; height:100%; margin:0; overflow:hidden; background:#f9fafb;
                 font-family:-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    #toolbar { position:fixed; top:10px; left:10px; z-index:10; display:flex; gap:4px; padding:5px;
               background:rgba(255,255,255,.95); border:1px solid #d1d5db; border-radius:8px;
               box-shadow:0 2px 6px rgba(0,0,0,.12); }
    #toolbar button { min-width:32px; height:30px; padding:0 9px; border:1px solid #d1d5db;
                      border-radius:5px; background:#f3f4f6; color:#374151; font-size:13px; cursor:pointer; }
    #toolbar button:hover { background:#e5e7eb; }
    #viewport { width:100%; height:100%; overflow:hidden; cursor:grab; }
    #viewport.dragging { cursor:grabbing; }
    #canvas { display:inline-block; transform-origin:0 0; }
    g.node.drill rect, g.node.drill polygon { stroke:#2563eb !important; stroke-width:2px !important; }
    #error { padding:16px; color:#b91c1c; white-space:pre-wrap; }
</style>
</head>
<body>
<div id="toolbar">
    <button onclick="zoomBy(1/1.2)">−</button>
    <button onclick="zoomBy(1.2)">+</button>
    <button onclick="fit()">Fit</button>
    <button onclick="reset()">100%</button>
</div>
<div id="viewport"><div id="canvas"></div></div>

<script>
    __BRIDGE__

    const source = `__SOURCE__`;
    const drillable = new Set(__DRILL__);

    const vp = document.getElementById('viewport');
    const canvas = document.getElementById('canvas');

    let scale = 1, tx = 20, ty = 60;

    function apply() {
        canvas.style.transform = 'translate(' + tx + 'px,' + ty + 'px) scale(' + scale + ')';
    }

    function zoomAt(factor, cx, cy) {
        const next = Math.min(10, Math.max(0.05, scale * factor));
        const k = next / scale;
        tx = cx - (cx - tx) * k;
        ty = cy - (cy - ty) * k;
        scale = next;
        apply();
    }

    function zoomBy(factor) { zoomAt(factor, vp.clientWidth / 2, vp.clientHeight / 2); }
    function reset() { scale = 1; tx = 20; ty = 60; apply(); }

    function fit() {
        const svg = canvas.querySelector('svg');
        if (!svg) return;
        const vb = svg.viewBox && svg.viewBox.baseVal;
        const w = (vb && vb.width) || svg.clientWidth;
        const h = (vb && vb.height) || svg.clientHeight;
        if (!w || !h) return;
        scale = Math.min(10, Math.max(0.05, Math.min(vp.clientWidth / w, (vp.clientHeight - 50) / h) * 0.95));
        tx = (vp.clientWidth - w * scale) / 2;
        ty = 50 + (vp.clientHeight - 50 - h * scale) / 2;
        apply();
    }

    vp.addEventListener('wheel', function (e) {
        e.preventDefault();
        zoomAt(e.deltaY < 0 ? 1.1 : 1 / 1.1, e.clientX, e.clientY);
    }, { passive: false });

    let dragging = false, sx = 0, sy = 0, downX = 0, downY = 0, moved = 0;
    vp.addEventListener('mousedown', function (e) {
        dragging = true; moved = 0;
        downX = e.clientX; downY = e.clientY;
        sx = e.clientX - tx; sy = e.clientY - ty;
        vp.classList.add('dragging');
    });
    window.addEventListener('mousemove', function (e) {
        if (!dragging) return;
        moved = Math.max(moved, Math.abs(e.clientX - downX) + Math.abs(e.clientY - downY));
        tx = e.clientX - sx; ty = e.clientY - sy; apply();
    });
    window.addEventListener('mouseup', function () { dragging = false; vp.classList.remove('dragging'); });

    mermaid.initialize({
        startOnLoad: false,
        maxTextSize: 1000000,
        maxEdges: 2000,
        flowchart: { useMaxWidth: false },
        theme: 'base',
        themeVariables: {
            primaryColor: '#e5e7eb', primaryTextColor: '#374151', primaryBorderColor: '#9ca3af',
            lineColor: '#4b5563', edgeLabelBackground: '#ffffff'
        }
    });

    mermaid.render('flow', source)
        .then(function (r) {
            canvas.innerHTML = r.svg;
            canvas.querySelectorAll('g.node').forEach(function (g) {
                const m = /flowchart-(n\d+)-/.exec(g.id);
                if (m && drillable.has(m[1])) {
                    g.classList.add('drill');
                    g.style.cursor = 'pointer';
                    g.addEventListener('click', function (ev) {
                        if (moved > 4) return;          // це було перетягування, а не клік
                        ev.stopPropagation();
                        window.drill(m[1]);
                    });
                }
            });
            setTimeout(fit, 50);
        })
        .catch(function (e) {
            canvas.innerHTML = '<div id="error">Mermaid error: ' + e.message + '</div>';
        });
</script>
</body>
</html>
""".trimIndent()
    }
}
