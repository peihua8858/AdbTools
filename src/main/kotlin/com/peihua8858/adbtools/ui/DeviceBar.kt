package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.action.ActionRegistry
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem

class DeviceBar(
    private val state: AdbToolState,
    private val project: Project,
) : JPanel(BorderLayout()) {

    private val info = transparentPanel(GridBagLayout())
    private val deviceLabel = JBLabel().apply { font = uiFont(13, bold = true) }
    private val adbLabel = JBLabel().apply { font = uiFont(11) }
    private val refreshButton = JButton("刷新设备")
    private val settingsButton = JButton("设置")
    private val rebootButton = JButton("重启设备")
    private val adbRestartButton = JButton("重启 adb")

    private val rebootAction = ActionRegistry.categories.flatMap { it.actions }.first { it.id == "device_reboot" }
    private val adbRestartAction = ActionRegistry.categories.flatMap { it.actions }.first { it.id == "adb_restart" }

    init {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(6)

        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(0, 0, 2, 0)
        }
        info.add(deviceLabel, constraints)
        constraints.gridy = 1
        constraints.insets = JBUI.insets(0)
        info.add(adbLabel, constraints)

        val buttonRow = transparentPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            add(refreshButton)
            add(settingsButton)
            add(rebootButton)
            add(adbRestartButton)
        }
        val buttons = transparentPanel(GridBagLayout()).apply {
            val c = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                weighty = 1.0
                anchor = GridBagConstraints.EAST
            }
            add(buttonRow, c)
        }

        add(info, BorderLayout.CENTER)
        add(buttons, BorderLayout.EAST)

        listOf<JComponent>(info, deviceLabel, adbLabel).forEach { component ->
            component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            component.toolTipText = "点击选择设备"
            component.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) showDeviceMenu()
                }
            })
        }

        refreshButton.addActionListener { state.refreshDevices() }
        settingsButton.addActionListener { AdbSettingsDialog(project, state).show() }
        rebootButton.addActionListener { state.run(rebootAction, emptyMap()) }
        adbRestartButton.addActionListener { state.run(adbRestartAction, emptyMap()) }

        refreshState()
    }

    fun refreshState() {
        deviceLabel.text = "${state.selectedDevice()?.displayName ?: "设备：未连接"}  ▾"
        val error = state.adbError
        adbLabel.text = error ?: state.adbPath ?: "正在检测 adb..."
        adbLabel.foreground = if (error != null) JBColor.RED else subtleColor
        refreshButton.isEnabled = !state.busy
        rebootButton.isEnabled = !state.busy && state.selectedSerial != null
        adbRestartButton.isEnabled = !state.busy
    }

    private fun showDeviceMenu() {
        val menu = JPopupMenu()
        val devices = state.devices()
        if (devices.isEmpty()) {
            menu.add(JMenuItem("未检测到已连接设备").apply { isEnabled = false })
        } else {
            devices.forEach { device ->
                val item = JRadioButtonMenuItem(device.displayName, device.serial == state.selectedSerial)
                item.isEnabled = device.isOnline
                item.addActionListener { state.selectDevice(device.serial) }
                menu.add(item)
            }
        }
        menu.addSeparator()
        menu.add(JMenuItem("刷新设备列表").apply { addActionListener { state.refreshDevices() } })
        menu.show(info, 0, info.height)
    }
}
