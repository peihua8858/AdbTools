package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.adb.ProcessEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.delay
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ProcessKillPanel(
    private val state: AdbToolState,
) : JPanel() {

    private val keywordField = JBTextField()
    private val queryButton = JButton("查询")
    private val killButton = JButton("Kill 选中（0）")
    private val selectAllButton = JButton("全选")
    private val clearSelectionButton = JButton("取消全选")

    private val listPanel = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val listScroll = JBScrollPane(listPanel).apply {
        preferredSize = Dimension(0, JBUI.scale(200))
        verticalScrollBar.unitIncrement = JBUI.scale(16)
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }
    private val emptyHint = JBLabel("输入包名后点击查询，结果会显示在这里").apply {
        font = uiFont(11)
        foreground = subtleColor
        border = JBUI.Borders.empty(2, 4)
    }

    private var processes: List<ProcessEntry> = emptyList()
    private val checked = mutableSetOf<String>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false

        keywordField.emptyText.text = "如 genie"
        keywordField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateButtons()
            override fun removeUpdate(e: DocumentEvent) = updateButtons()
            override fun changedUpdate(e: DocumentEvent) = updateButtons()
        })

        add(fieldLabel("包名（支持部分匹配）"))
        val queryRow = transparentPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            add(keywordField, BorderLayout.CENTER)
            add(queryButton, BorderLayout.EAST)
        }
        add(queryRow.stretchWidth())
        add(spacer(6))

        val actionRow = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(killButton)
            add(selectAllButton)
            add(clearSelectionButton)
        }
        add(actionRow.stretchWidth())
        add(spacer(6))

        listPanel.add(emptyHint.stretchWidth())
        add(listScroll.stretchWidth())

        queryButton.addActionListener { query() }
        killButton.addActionListener { killSelected() }
        selectAllButton.addActionListener {
            checked.clear()
            processes.take(MAX_PROCESS_ROWS).forEach { checked += it.pid }
            rebuildList()
        }
        clearSelectionButton.addActionListener {
            checked.clear()
            rebuildList()
        }

        updateButtons()
    }

    fun refreshState() = updateButtons()

    private fun query() {
        val keyword = keywordField.text.trim()
        state.runTask("查询进程") {
            val list = state.queryProcesses(keyword)
            ApplicationManager.getApplication().invokeLater { render(list) }
            if (list.isEmpty()) {
                warning("未找到包含 '$keyword' 的进程")
            } else {
                success("共找到 ${list.size} 个进程")
            }
        }
    }

    private fun killSelected() {
        val pids = checked.toList()
        if (pids.isEmpty()) return
        val keyword = keywordField.text.trim()
        state.runTask("Kill 选中进程") {
            killPids(pids)
            delay(1000)
            val remaining = state.queryProcesses(keyword)
            ApplicationManager.getApplication().invokeLater { render(remaining) }
            if (remaining.isEmpty()) {
                success("已无匹配进程")
            } else {
                info("当前剩余 ${remaining.size} 个进程")
            }
        }
    }

    private fun render(list: List<ProcessEntry>) {
        processes = list
        checked.clear()
        rebuildList()
    }

    private fun rebuildList() {
        listPanel.removeAll()
        if (processes.isEmpty()) {
            listPanel.add(emptyHint.stretchWidth())
        } else {
            processes.take(MAX_PROCESS_ROWS).forEach { entry ->
                val box = JCheckBox("PID=${entry.pid}  ${entry.name}", checked.contains(entry.pid)).apply {
                    font = uiFont(12)
                    isOpaque = false
                    addActionListener {
                        if (isSelected) checked += entry.pid else checked -= entry.pid
                        updateButtons()
                    }
                }
                listPanel.add(box.stretchWidth())
            }
            if (processes.size > MAX_PROCESS_ROWS) {
                listPanel.add(
                    JBLabel("仅显示前 $MAX_PROCESS_ROWS 个进程，请使用更精确的关键字").apply {
                        font = uiFont(11)
                        foreground = subtleColor
                        border = JBUI.Borders.empty(4, 4, 0, 0)
                    }.stretchWidth(),
                )
            }
        }
        listPanel.revalidate()
        listPanel.repaint()
        updateButtons()
    }

    private fun updateButtons() {
        keywordField.isEnabled = !state.busy
        queryButton.isEnabled = !state.busy && keywordField.text.isNotBlank()
        killButton.isEnabled = !state.busy && checked.isNotEmpty()
        killButton.text = "Kill 选中（${checked.size}）"
        selectAllButton.isEnabled = !state.busy && processes.isNotEmpty()
        clearSelectionButton.isEnabled = !state.busy && processes.isNotEmpty()
    }

    private fun fieldLabel(text: String): JComponent =
        JBLabel(text).apply {
            font = uiFont(12, bold = true)
            border = JBUI.Borders.emptyBottom(2)
        }.stretchWidth()

    companion object {
        private const val MAX_PROCESS_ROWS = 100
    }
}
