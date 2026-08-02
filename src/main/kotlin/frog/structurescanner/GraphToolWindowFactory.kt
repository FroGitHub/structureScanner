package frog.structurescanner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import frog.structurescanner.call.CallGraphBuilder
import frog.structurescanner.mapper.PsiMethodMapper
import frog.structurescanner.mermaid.MermaidRenderer
import frog.structurescanner.methods.MethodResolver
import frog.structurescanner.methods.MethodScanner
import frog.structurescanner.methods.MethodSourceResolver
import frog.structurescanner.model.CallType
import frog.structurescanner.model.MethodArgumentResolver
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities

class GraphToolWindowFactory : ToolWindowFactory {


    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val panel = JPanel(BorderLayout())
        val browser = JBCefBrowser()

        // =========================
        // Controls
        // =========================

        val depthSpinner = JSpinner(
            SpinnerNumberModel(5, 1, 10, 1)
        )

        val directCheckBox = JCheckBox("Direct")
        val implementationCheckBox = JCheckBox("Implementation", true)
        val referenceCheckBox = JCheckBox("Reference", true)
        val frameworkCheckBox = JCheckBox("Framework")
        val inheritedCheckBox = JCheckBox("Inherited")
        val overriddenCheckBox = JCheckBox("Overridden")

        val compactCheckBox = JCheckBox("Compact")

        val showArgumentTypesCheckBox =
            JCheckBox("Types")

        val showArgumentFieldsCheckBox =
            JCheckBox("Fields")

        val refreshButton = JButton("Refresh")

        // =========================
        // Toolbar
        // =========================

        fun section(
            title: String,
            vararg components: java.awt.Component
        ): JPanel {

            val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
            panel.border = javax.swing.BorderFactory.createTitledBorder(title)

            components.forEach {
                panel.add(it)
            }

            return panel
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6))

        toolbar.add(
            section(
                "Graph",
                JLabel("Depth"),
                depthSpinner,
                compactCheckBox
            )
        )

        toolbar.add(
            section(
                "Calls",
                directCheckBox,
                implementationCheckBox,
                referenceCheckBox,
                frameworkCheckBox,
                inheritedCheckBox,
                overriddenCheckBox
            )
        )

        toolbar.add(
            section(
                "Arguments",
                showArgumentTypesCheckBox,
                showArgumentFieldsCheckBox
            )
        )

        toolbar.add(refreshButton)

        panel.add(
            toolbar,
            BorderLayout.NORTH
        )

        panel.add(
            browser.component,
            BorderLayout.CENTER
        )

        // =========================
        // HTML
        // =========================

        fun showHtml(html: String) {
            SwingUtilities.invokeLater {
                browser.loadHTML(html)
            }
        }

        // =========================
        // Call types
        // =========================

        fun getVisibleCallTypes(): Set<CallType> {
            return buildSet {

                if (directCheckBox.isSelected) {
                    add(CallType.DIRECT)
                }

                if (implementationCheckBox.isSelected) {
                    add(CallType.IMPLEMENTATION)
                }

                if (referenceCheckBox.isSelected) {
                    add(CallType.METHOD_REFERENCE)
                }

                if (frameworkCheckBox.isSelected) {
                    add(CallType.FRAMEWORK)
                }

                if (inheritedCheckBox.isSelected) {
                    add(CallType.INHERITED)
                }

                if (overriddenCheckBox.isSelected) {
                    add(CallType.OVERRIDDEN)
                }
            }
        }

        // =========================
        // Render graph
        // =========================

        fun refreshGraph() {

            val editor =
                FileEditorManager
                    .getInstance(project)
                    .selectedTextEditor

            if (editor == null) {
                showHtml("<h1>No editor opened</h1>")
                return
            }

            val document = editor.document
            val offset = editor.caretModel.offset

            val maxDepth =
                depthSpinner.value as Int

            val visibleCallTypes =
                getVisibleCallTypes()

            val compact =
                compactCheckBox.isSelected

            val showArgumentTypes =
                showArgumentTypesCheckBox.isSelected

            val showArgumentFields =
                showArgumentFieldsCheckBox.isSelected

            ApplicationManager
                .getApplication()
                .executeOnPooledThread {

                    try {

                        val mermaid =
                            ReadAction.compute<String?, RuntimeException> {

                                val psiFile =
                                    PsiDocumentManager
                                        .getInstance(project)
                                        .getPsiFile(document)
                                        ?: return@compute null

                                val element =
                                    psiFile.findElementAt(offset)
                                        ?: return@compute null

                                val method =
                                    PsiTreeUtil.getParentOfType(
                                        element,
                                        PsiMethod::class.java
                                    )
                                        ?: return@compute null

                                val scanner =
                                    MethodScanner(
                                        MethodResolver()
                                    )

                                val mapper =
                                    PsiMethodMapper(
                                        MethodSourceResolver()
                                    )

                                val builder =
                                    CallGraphBuilder(
                                        scanner = scanner,
                                        mapper = mapper,
                                        argumentResolver =
                                            MethodArgumentResolver()
                                    )

                                val graph =
                                    builder.build(
                                        root = method,
                                        maxDepth = maxDepth
                                    )

                                MermaidRenderer().render(
                                    graph = graph,
                                    visibleCallTypes = visibleCallTypes,
                                    compact = compact,
                                    showArgumentTypes =
                                        showArgumentTypes,
                                    showArgumentFields =
                                        showArgumentFields
                                )
                            }

                        if (mermaid == null) {
                            showHtml(
                                """
                            <h1>Place cursor inside a method</h1>
                            """.trimIndent()
                            )
                            return@executeOnPooledThread
                        }

                        showHtml(createHtml(mermaid))

                    } catch (e: Exception) {

                        e.printStackTrace()

                        showHtml(
                            """
                        <h1>Failed to build graph</h1>
                        <pre>${e.message}</pre>
                        """.trimIndent()
                        )
                    }
                }
        }

        // =========================
        // Auto refresh
        // =========================

        val refreshableComponents = listOf(
            depthSpinner,
            directCheckBox,
            implementationCheckBox,
            referenceCheckBox,
            frameworkCheckBox,
            inheritedCheckBox,
            overriddenCheckBox,
            compactCheckBox,
            showArgumentTypesCheckBox,
            showArgumentFieldsCheckBox
        )

        refreshableComponents.forEach { component ->

            when (component) {

                is JSpinner -> {
                    component.addChangeListener {
                        refreshGraph()
                    }
                }

                is JCheckBox -> {
                    component.addActionListener {
                        refreshGraph()
                    }
                }
            }
        }

        refreshButton.addActionListener {
            refreshGraph()
        }

        // =========================
        // Content
        // =========================

        val content =
            ContentFactory
                .getInstance()
                .createContent(
                    panel,
                    "",
                    false
                )

        toolWindow.contentManager.addContent(content)

        refreshGraph()
    }

    private fun createHtml(
        mermaid: String
    ): String {
        return """
        <!DOCTYPE html>
        <html>

        <head>

            <meta charset="UTF-8">

            <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>

            <style>

                html,
                body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    overflow: hidden;
                    background: #f9fafb;
                }

                /* =========================
                   Toolbar
                   ========================= */

                #toolbar {
                    position: fixed;

                    top: 10px;
                    left: 10px;

                    z-index: 1000;

                    display: flex;
                    gap: 4px;

                    padding: 5px;

                    background: rgba(255, 255, 255, 0.95);

                    border: 1px solid #d1d5db;
                    border-radius: 8px;

                    box-shadow:
                        0 2px 6px rgba(0, 0, 0, 0.12);
                }

                #toolbar button {
                    min-width: 32px;
                    height: 30px;

                    padding: 0 9px;

                    border: 1px solid #d1d5db;
                    border-radius: 5px;

                    background: #f3f4f6;
                    color: #374151;

                    font-size: 13px;

                    cursor: pointer;
                }

                #toolbar button:hover {
                    background: #e5e7eb;
                }

                #toolbar button:active {
                    background: #d1d5db;
                }

                /* =========================
                   Graph
                   ========================= */

                #graph-container {
                    width: 100%;
                    height: 100%;

                    overflow: hidden;

                    cursor: grab;

                    background: #f9fafb;
                }

                #graph-container:active {
                    cursor: grabbing;
                }

                #graph {
                    display: inline-block;

                    transform-origin: 0 0;
                }

                /* =========================
                   Mermaid nodes
                   ========================= */

                .node rect,
                .node polygon,
                .node path {
                    fill: #e5e7eb !important;
                    stroke: #9ca3af !important;
                    stroke-width: 1.5px !important;
                }

                .nodeLabel {
                    color: #374151 !important;
                    fill: #374151 !important;
                }

                /* =========================
                   Mermaid edges
                   ========================= */

                .edgePath .path {
                    stroke: #4b5563 !important;
                    stroke-width: 3px !important;
                }

                .edgePath marker path {
                    fill: #4b5563 !important;
                    stroke: #4b5563 !important;
                }

                /* =========================
                   Edge labels
                   ========================= */

                .edgeLabel {
                    color: #374151 !important;

                    background: #ffffff !important;

                    border-radius: 4px;
                }

                .edgeLabel rect {
                    fill: #ffffff !important;
                    stroke: #d1d5db !important;

                    rx: 4;
                    ry: 4;
                }

                .edgeLabel span {
                    color: #374151 !important;
                }

            </style>

        </head>

        <body>

            <!-- =========================
                 Graph toolbar
                 ========================= -->

            <div id="toolbar">

                <button onclick="zoomOut()">
                    −
                </button>

                <button onclick="zoomIn()">
                    +
                </button>

                <button onclick="resetView()">
                    Reset
                </button>

                <button onclick="fitGraph()">
                    Fit
                </button>

            </div>

            <!-- =========================
                 Graph
                 ========================= -->

            <div id="graph-container">

                <div id="graph" class="mermaid">
                    $mermaid
                </div>

            </div>

            <script>

                mermaid.initialize({

                    startOnLoad: false,

                    theme: "base",

                    themeVariables: {

                        primaryColor: "#e5e7eb",
                        primaryTextColor: "#374151",
                        primaryBorderColor: "#9ca3af",

                        lineColor: "#4b5563",

                        secondaryColor: "#f3f4f6",
                        tertiaryColor: "#f9fafb",

                        edgeLabelBackground: "#ffffff",

                        fontFamily:
                            "-apple-system, BlinkMacSystemFont, " +
                            "\"Segoe UI\", sans-serif"
                    }
                });


                // =========================
                // View state
                // =========================

                let scale = 1;

                let translateX = 20;
                let translateY = 20;

                let dragging = false;

                let startX = 0;
                let startY = 0;


                // =========================
                // Transform
                // =========================

                function updateTransform() {

                    const graph =
                        document.getElementById("graph");

                    graph.style.transform =
                        "translate(" +
                        translateX +
                        "px, " +
                        translateY +
                        "px) " +
                        "scale(" +
                        scale +
                        ")";
                }


                // =========================
                // Zoom
                // =========================

                function zoomIn() {

                    scale *= 1.2;

                    scale = Math.min(
                        scale,
                        10
                    );

                    updateTransform();
                }


                function zoomOut() {

                    scale /= 1.2;

                    scale = Math.max(
                        scale,
                        0.1
                    );

                    updateTransform();
                }


                // =========================
                // Reset
                // =========================

                function resetView() {

                    scale = 1;

                    translateX = 20;
                    translateY = 20;

                    updateTransform();
                }


                // =========================
                // Fit
                // =========================

                function fitGraph() {

                    const container =
                        document.getElementById(
                            "graph-container"
                        );

                    const graph =
                        document.getElementById(
                            "graph"
                        );

                    const graphWidth =
                        graph.scrollWidth;

                    const graphHeight =
                        graph.scrollHeight;

                    const containerWidth =
                        container.clientWidth;

                    const containerHeight =
                        container.clientHeight;

                    if (
                        graphWidth <= 0 ||
                        graphHeight <= 0
                    ) {
                        return;
                    }

                    const scaleX =
                        containerWidth /
                        graphWidth;

                    const scaleY =
                        containerHeight /
                        graphHeight;

                    scale =
                        Math.min(
                            scaleX,
                            scaleY
                        ) * 0.9;

                    scale =
                        Math.max(
                            0.1,
                            Math.min(
                                scale,
                                10
                            )
                        );

                    translateX =
                        (
                            containerWidth -
                            graphWidth * scale
                        ) / 2;

                    translateY =
                        (
                            containerHeight -
                            graphHeight * scale
                        ) / 2;

                    updateTransform();
                }


                // =========================
                // Mouse wheel
                // =========================

                const container =
                    document.getElementById(
                        "graph-container"
                    );

                container.addEventListener(
                    "wheel",
                    function(event) {

                        event.preventDefault();

                        const factor =
                            event.deltaY < 0
                                ? 1.1
                                : 0.9;

                        scale *= factor;

                        scale =
                            Math.max(
                                0.1,
                                Math.min(
                                    scale,
                                    10
                                )
                            );

                        updateTransform();
                    }
                );


                // =========================
                // Drag
                // =========================

                container.addEventListener(
                    "mousedown",
                    function(event) {

                        dragging = true;

                        startX =
                            event.clientX -
                            translateX;

                        startY =
                            event.clientY -
                            translateY;
                    }
                );


                container.addEventListener(
                    "mousemove",
                    function(event) {

                        if (!dragging) {
                            return;
                        }

                        translateX =
                            event.clientX -
                            startX;

                        translateY =
                            event.clientY -
                            startY;

                        updateTransform();
                    }
                );


                container.addEventListener(
                    "mouseup",
                    function() {
                        dragging = false;
                    }
                );


                container.addEventListener(
                    "mouseleave",
                    function() {
                        dragging = false;
                    }
                );


                // =========================
                // Mermaid
                // =========================

                mermaid.run().then(() => {

                    updateTransform();

                    setTimeout(
                        fitGraph,
                        100
                    );
                });

            </script>

        </body>

        </html>
    """.trimIndent()
    }
}
