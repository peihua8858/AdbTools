package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.adb.LogLevel
import com.peihua8858.adbtools.adb.LogLine
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.ScrollPaneConstants
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class ConsolePanel(
    private val state: AdbToolState,
) : JPanel(BorderLayout()) {

    private val textPane = JTextPane().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = UIUtil.getPanelBackground()
    }
    private val scroll = JBScrollPane(textPane).apply {
        border = JBUI.Borders.empty()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        verticalScrollBar.unitIncrement = JBUI.scale(16)
    }
    private val autoScroll = JCheckBox("自动滚动", true).apply { font = uiFont(12); isOpaque = false }
    private val onlyIssues = JCheckBox("只看问题", false).apply { font = uiFont(12); isOpaque = false }
    private val runningLabel = JBLabel().apply {
        font = uiFont(11)
        foreground = subtleColor
        isVisible = false
    }
    private val stopButton = JButton("停止").apply {
        isVisible = false
        addActionListener { state.stop() }
    }

    private val styles = mutableMapOf<LogLevel, Style>()
    private var placeholderStyle: Style? = null
    private var renderedCount = 0
    private var renderedFilter = false
    private var placeholderShown = false
    private var lastBusy = false

    init {
        isOpaque = false

        val titleLabel = JBLabel("输出").apply { font = uiFont(13, bold = true) }
        val runningRow = transparentPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            add(runningLabel)
            add(stopButton)
        }
        val titleRow = transparentPanel(BorderLayout()).apply {
            add(titleLabel, BorderLayout.WEST)
            add(runningRow, BorderLayout.EAST)
        }

        val optionsRow = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(autoScroll)
            add(onlyIssues)
        }
        val copyCommandButton = JButton("复制命令").apply {
            addActionListener { state.copyLastCommand() }
        }
        val toolsRow = transparentPanel(BorderLayout()).apply {
            add(optionsRow, BorderLayout.WEST)
            val right = transparentPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
                add(copyCommandButton)
            }
            add(right, BorderLayout.EAST)
        }

        val actionRow = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(JButton("清空").apply { addActionListener { state.clearOutput() } })
            add(JButton("复制").apply { addActionListener { state.copyOutput() } })
            add(JButton("保存").apply { addActionListener { state.saveOutput() } })
        }

        val header = transparentPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyBottom(4)
            add(titleRow.stretchWidth())
            add(toolsRow.stretchWidth())
            add(actionRow.stretchWidth())
        }

        onlyIssues.addActionListener { rebuildFromState() }

        add(header, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)

        refresh()
    }

    fun refresh() {
        runningLabel.text = if (state.busy) "运行中：${state.runningTitle.orEmpty()}" else ""
        runningLabel.isVisible = state.busy
        if (lastBusy != state.busy) {
            lastBusy = state.busy
            stopButton.isVisible = state.busy
            revalidate()
        }

        val snapshot = state.outputSnapshot()
        val lines = if (onlyIssues.isSelected) {
            snapshot.filter { it.level == LogLevel.WARNING || it.level == LogLevel.ERROR }
        } else {
            snapshot
        }

        when {
            lines.isEmpty() -> showPlaceholder()
            placeholderShown || onlyIssues.isSelected != renderedFilter || lines.size < renderedCount -> rebuild(lines)
            lines.size > renderedCount -> append(lines.subList(renderedCount, lines.size))
        }

        if (autoScroll.isSelected) {
            textPane.caretPosition = textPane.document.length
        }
    }

    private fun rebuildFromState() {
        renderedCount = -1
        renderedFilter = !onlyIssues.isSelected
        refresh()
    }

    private fun rebuild(lines: List<LogLine>) {
        val doc = textPane.styledDocument
        doc.remove(0, doc.length)
        renderedCount = 0
        renderedFilter = onlyIssues.isSelected
        placeholderShown = false
        append(lines)
    }

    private fun append(lines: List<LogLine>) {
        val doc = textPane.styledDocument
        lines.forEach { line ->
            val isDivider = line.level == LogLevel.DIVIDER || line.text.isEmpty()
            val text = if (isDivider) DIVIDER_TEXT else line.text
            val style = styleFor(if (isDivider) LogLevel.DIVIDER else line.level)
            doc.insertString(doc.length, "$text\n", style)
        }
        renderedCount += lines.size
    }

    private fun showPlaceholder() {
        val doc = textPane.styledDocument
        doc.remove(0, doc.length)
        val style = placeholderStyle ?: doc.addStyle("placeholder", null).apply {
            StyleConstants.setForeground(this, subtleColor)
            StyleConstants.setItalic(this, true)
        }.also { placeholderStyle = it }
        doc.insertString(
            0,
            if (onlyIssues.isSelected) {
                "没有警告或错误输出"
            } else {
                "暂无输出，执行功能后命令输出会显示在这里"
            },
            style,
        )
        placeholderShown = true
        renderedFilter = onlyIssues.isSelected
        renderedCount = 0
    }

    private fun styleFor(level: LogLevel): Style {
        styles[level]?.let { return it }
        val doc: StyledDocument = textPane.styledDocument
        val style = doc.addStyle(level.name, null)
        StyleConstants.setForeground(style, colorFor(level))
        if (level == LogLevel.TITLE || level == LogLevel.COMMAND) {
            StyleConstants.setBold(style, true)
        }
        styles[level] = style
        return style
    }

    private fun colorFor(level: LogLevel): Color = when (level) {
        LogLevel.COMMAND, LogLevel.INFO -> JBColor.namedColor("Label.infoForeground", JBColor(0x6E6E6E, 0xA9A9A9))
        LogLevel.SUCCESS -> JBColor(0x1A7F37, 0x6AAB73)
        LogLevel.WARNING -> JBColor(0x9A6700, 0xE2B93D)
        LogLevel.ERROR -> JBColor(0xC62828, 0xFF6B68)
        LogLevel.DIVIDER -> subtleColor
        else -> UIUtil.getLabelForeground()
    }

    companion object {
        private val DIVIDER_TEXT = "─".repeat(80)
    }
}
