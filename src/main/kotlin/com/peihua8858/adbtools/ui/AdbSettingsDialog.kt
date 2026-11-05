package com.peihua8858.adbtools.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent

class AdbSettingsDialog(
    project: Project,
    private val state: AdbToolState,
) : DialogWrapper(project) {

    private val adbField = JBTextField(state.settings.state.adbPath)
    private val outputField = JBTextField(state.settings.state.outputDir)

    init {
        title = "ADB 工具箱设置"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = transparentPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }

        adbField.emptyText.text = "如 ~/Library/Android/sdk/platform-tools/adb"
        outputField.emptyText.text = "如 ~/Downloads/adb-output"

        form.add(label("adb 路径（留空自动检测）"))
        form.add(adbField.stretchWidth())
        form.add(spacer(8))
        form.add(label("输出目录（留空使用项目下 adb-output）"))
        form.add(outputField.stretchWidth())
        form.add(spacer(8))

        val buttonRow = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(JButton("打开输出目录").apply { addActionListener { state.openOutputDirectory() } })
        }
        form.add(buttonRow.stretchWidth())
        form.add(spacer(8))

        form.add(
            JBLabel("当前 adb：${state.adbPath ?: "未检测到"}").apply {
                font = uiFont(11)
                foreground = subtleColor
            }.stretchWidth(),
        )
        form.add(
            JBLabel("当前输出目录：${state.outputDirectory().absolutePath}").apply {
                font = uiFont(11)
                foreground = subtleColor
            }.stretchWidth(),
        )

        val root = transparentPanel(BorderLayout()).apply {
            preferredSize = Dimension(JBUI.scale(560), JBUI.scale(180))
            add(form, BorderLayout.CENTER)
        }
        return root
    }

    override fun doOKAction() {
        state.settings.state.adbPath = adbField.text.trim()
        state.settings.state.outputDir = outputField.text.trim()
        super.doOKAction()
        state.onSettingsChanged()
    }

    private fun label(text: String): JComponent =
        JBLabel(text).apply {
            font = uiFont(12, bold = true)
            border = JBUI.Borders.emptyBottom(2)
        }.stretchWidth()
}
