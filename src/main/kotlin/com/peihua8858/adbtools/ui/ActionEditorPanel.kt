package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.action.ActionField
import com.peihua8858.adbtools.action.AdbAction
import com.peihua8858.adbtools.action.FieldKind
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.TransferHandler
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ActionEditorPanel(
    private val project: Project,
    private val state: AdbToolState,
) : JPanel(BorderLayout()) {

    private val content = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val scroll = JBScrollPane(content).apply {
        border = JBUI.Borders.empty()
        verticalScrollBar.unitIncrement = JBUI.scale(16)
    }

    private var currentAction: AdbAction? = null
    private val values = LinkedHashMap<String, String>()
    private val fieldViews = mutableListOf<Pair<ActionField, JComponent>>()
    private val inputComponents = mutableListOf<JComponent>()

    private var runButton: JButton? = null
    private var stopButton: JButton? = null
    private var statusLabel: JBLabel? = null
    private var processPanel: ProcessKillPanel? = null
    private var lastBusy = false

    init {
        isOpaque = false
        add(scroll, BorderLayout.CENTER)
        showEmpty()
    }

    fun showEmpty() {
        currentAction = null
        fieldViews.clear()
        inputComponents.clear()
        processPanel = null
        runButton = null
        stopButton = null
        statusLabel = null
        content.removeAll()
        content.add(spacer(20))
        content.add(
            JBLabel("选择一个功能").apply {
                font = uiFont(14, bold = true)
                border = JBUI.Borders.emptyLeft(4)
            }.stretchWidth(),
        )
        content.add(
            JBLabel("展开左侧分类，点击功能查看参数并执行").apply {
                font = uiFont(11)
                foreground = subtleColor
                border = JBUI.Borders.empty(4, 4, 0, 0)
            }.stretchWidth(),
        )
        content.revalidate()
        content.repaint()
    }

    fun show(action: AdbAction) {
        currentAction = action
        values.clear()
        action.fields.forEach { values[it.id] = state.defaultFor(it) }
        fieldViews.clear()
        inputComponents.clear()
        processPanel = null
        runButton = null
        stopButton = null
        statusLabel = null
        lastBusy = state.busy

        content.removeAll()
        content.add(
            JBLabel(action.title).apply { font = uiFont(14, bold = true) }.stretchWidth(),
        )
        content.add(
            JBLabel(action.summary).apply {
                font = uiFont(11)
                foreground = subtleColor
            }.stretchWidth(),
        )
        content.add(spacer(8))

        if (action.id == "process_kill_by_package") {
            val panel = ProcessKillPanel(state)
            processPanel = panel
            content.add(panel.stretchWidth())
        } else {
            action.fields.forEach { addField(it) }
            content.add(spacer(4))
            addRunRow()
        }

        updateVisibility()
        updateRunState(force = true)
        content.revalidate()
        content.repaint()
        scroll.verticalScrollBar.value = 0
    }

    fun refreshState() = updateRunState()

    private fun addField(field: ActionField) {
        val view = when (field.kind) {
            FieldKind.TEXT -> textView(field)
            FieldKind.FILE -> fileView(field)
            FieldKind.CHECKBOX -> checkView(field)
            FieldKind.SELECT -> selectView(field)
            FieldKind.INFO -> JBLabel(field.label).apply {
                font = uiFont(11)
                foreground = subtleColor
            }
        }
        fieldViews += field to view
        content.add(view.stretchWidth())
        content.add(spacer(6))
    }

    private fun textView(field: ActionField): JComponent {
        val column = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        column.add(fieldLabel(field.label))
        val input = JBTextField(values[field.id].orEmpty())
        if (field.placeholder.isNotEmpty()) input.emptyText.text = field.placeholder
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTextChanged(field, input.text)
            override fun removeUpdate(e: DocumentEvent) = onTextChanged(field, input.text)
            override fun changedUpdate(e: DocumentEvent) = onTextChanged(field, input.text)
        })
        column.add(input.stretchWidth())
        inputComponents += input

        val history = state.fieldHistory(field.id)
        if (history.isNotEmpty()) {
            val historyRow = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
            historyRow.add(JBLabel("最近：").apply { font = uiFont(11); foreground = subtleColor })
            history.forEach { past ->
                historyRow.add(
                    JButton(past).apply {
                        font = uiFont(11)
                        margin = JBUI.insets(2, 6)
                        toolTipText = past
                        addActionListener { input.text = past }
                    },
                )
            }
            column.add(historyRow.stretchWidth())
        }

        if (field.hint.isNotEmpty()) column.add(hintLabel(field.hint))
        return column
    }

    private fun fileView(field: ActionField): JComponent {
        val column = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        column.add(fieldLabel(field.label))
        val input = JBTextField(values[field.id].orEmpty())
        if (field.placeholder.isNotEmpty()) input.emptyText.text = field.placeholder
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTextChanged(field, input.text)
            override fun removeUpdate(e: DocumentEvent) = onTextChanged(field, input.text)
            override fun changedUpdate(e: DocumentEvent) = onTextChanged(field, input.text)
        })
        val choose = JButton("选择").apply {
            addActionListener {
                val chosen = FileChooser.chooseFile(
                    FileChooserDescriptorFactory.createSingleFileDescriptor("apk"),
                    project,
                    null,
                )
                if (chosen != null) input.text = chosen.path
            }
        }
        val row = transparentPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            add(input, BorderLayout.CENTER)
            add(choose, BorderLayout.EAST)
        }
        column.add(row.stretchWidth())
        inputComponents += input
        inputComponents += choose
        installFileDrop(input, column)
        if (field.hint.isNotEmpty()) column.add(hintLabel(field.hint))
        return column
    }

    private fun installFileDrop(input: JBTextField, zone: JComponent) {
        input.toolTipText = "可直接把文件拖到输入框"
        val handler = object : TransferHandler() {
            override fun getSourceActions(component: JComponent?): Int = TransferHandler.COPY

            override fun canImport(support: TransferHandler.TransferSupport): Boolean =
                support.isDrop && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

            override fun importData(support: TransferHandler.TransferSupport): Boolean {
                val file = droppedFile(support.transferable) ?: return false
                input.text = file.absolutePath
                return true
            }
        }
        input.transferHandler = handler
        zone.transferHandler = handler
    }

    private fun droppedFile(transferable: Transferable): File? = runCatching {
        (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
            ?.filterIsInstance<File>()
            ?.firstOrNull { it.isFile }
    }.getOrNull()

    private fun checkView(field: ActionField): JComponent {
        val column = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val box = JCheckBox(field.label, values[field.id] == "true").apply {
            font = uiFont(12)
            isOpaque = false
            addActionListener {
                values[field.id] = isSelected.toString()
                onValueChanged()
            }
        }
        column.add(box.stretchWidth())
        inputComponents += box
        if (field.hint.isNotEmpty()) column.add(hintLabel(field.hint))
        return column
    }

    private fun selectView(field: ActionField): JComponent {
        val column = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        column.add(fieldLabel(field.label))
        val options = field.options
        val combo = JComboBox(options.map { it.label }.toTypedArray())
        val current = options.indexOfFirst { it.value == (values[field.id] ?: field.defaultValue) }
        if (current >= 0) {
            combo.selectedIndex = current
            values[field.id] = options[current].value
        }
        combo.addItemListener {
            val index = combo.selectedIndex
            if (index in options.indices) {
                values[field.id] = options[index].value
                onValueChanged()
            }
        }
        column.add(combo.stretchWidth())
        inputComponents += combo
        if (field.hint.isNotEmpty()) column.add(hintLabel(field.hint))
        return column
    }

    private fun addRunRow() {
        val run = JButton("执行").apply {
            addActionListener { currentAction?.let { state.run(it, values.toMap()) } }
        }
        val stop = JButton("停止").apply {
            isVisible = false
            addActionListener { state.stop() }
        }
        val status = JBLabel().apply {
            font = uiFont(11)
            foreground = subtleColor
        }
        runButton = run
        stopButton = stop
        statusLabel = status
        val row = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            add(run)
            add(stop)
            add(status)
        }
        content.add(row.stretchWidth())
    }

    private fun onTextChanged(field: ActionField, text: String) {
        values[field.id] = text
        onValueChanged()
    }

    private fun onValueChanged() {
        updateVisibility()
        updateRunState()
    }

    private fun updateVisibility() {
        fieldViews.forEach { (field, view) -> view.isVisible = field.visibleWhen(values) }
        content.revalidate()
        content.repaint()
    }

    private fun updateRunState(force: Boolean = false) {
        val action = currentAction
        if (action == null) return
        val confirmRequired = action.fields.any { it.id == "confirm" } && values["confirm"] != "true"
        val deviceRequired = action.requiresDevice && state.selectedSerial == null

        runButton?.apply {
            isEnabled = !state.busy && !confirmRequired && !deviceRequired
            text = if (state.busy) "执行中..." else "执行"
        }
        statusLabel?.text = when {
            deviceRequired -> "请先选择设备"
            confirmRequired -> "请先勾选确认项"
            state.busy -> "运行中：${state.runningTitle.orEmpty()}"
            else -> ""
        }
        inputComponents.forEach { it.isEnabled = !state.busy }
        processPanel?.refreshState()

        if (force || lastBusy != state.busy) {
            lastBusy = state.busy
            stopButton?.isVisible = state.busy
            content.revalidate()
            content.repaint()
        }
    }

    private fun fieldLabel(text: String): JComponent =
        JBLabel(text).apply {
            font = uiFont(12, bold = true)
            border = JBUI.Borders.emptyBottom(2)
        }.stretchWidth()

    private fun hintLabel(text: String): JComponent =
        JBLabel(text).apply {
            font = uiFont(11)
            foreground = subtleColor
            toolTipText = text
        }.stretchWidth()
}
