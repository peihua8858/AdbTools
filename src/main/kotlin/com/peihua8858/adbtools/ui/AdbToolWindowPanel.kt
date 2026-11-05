package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.action.ActionCategory
import com.peihua8858.adbtools.action.ActionRegistry
import com.peihua8858.adbtools.action.AdbAction
import com.peihua8858.adbtools.adb.AdbSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

class AdbToolWindowPanel(
    project: Project,
    private val settings: AdbSettings,
) : JPanel(BorderLayout()), Disposable {

    private val state = AdbToolState(project, settings)
    private val deviceBar = DeviceBar(state, project)
    private val nav = ActionNavPanel(state) { category, action -> select(category, action) }
    private val editor = ActionEditorPanel(project, state)
    private val console = ConsolePanel(state)

    private var restoringSplit = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(10)

        val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, console).apply {
            border = JBUI.Borders.empty()
            dividerSize = JBUI.scale(6)
            isContinuousLayout = true
            resizeWeight = 0.4
        }
        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nav, rightSplit).apply {
            border = JBUI.Borders.empty()
            dividerSize = JBUI.scale(6)
            isContinuousLayout = true
            resizeWeight = 0.0
            dividerLocation = JBUI.scale(210)
        }

        add(deviceBar, BorderLayout.NORTH)
        add(mainSplit, BorderLayout.CENTER)

        state.addChangeListener {
            deviceBar.refreshState()
            editor.refreshState()
            console.refresh()
        }

        val savedCategory = ActionRegistry.categories.firstOrNull { it.id == settings.state.lastCategoryId }
        val savedAction = savedCategory?.actions?.firstOrNull { it.id == settings.state.lastActionId }
        if (savedCategory != null && savedAction != null) {
            nav.setSelection(savedCategory.id, savedAction.id)
            editor.show(savedAction)
        } else {
            editor.showEmpty()
        }

        SwingUtilities.invokeLater {
            val height = rightSplit.height
            if (height > 0) {
                restoreConsoleHeight(rightSplit)
            }
        }
        rightSplit.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (splitRestored || rightSplit.height <= 0) return
                restoreConsoleHeight(rightSplit)
            }
        })
        rightSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
            if (restoringSplit) return@addPropertyChangeListener
            val height = rightSplit.height
            if (height <= 0) return@addPropertyChangeListener
            val fraction = (height - rightSplit.dividerLocation).toFloat() / height
            settings.state.consoleFraction = fraction.coerceIn(MIN_CONSOLE_FRACTION, MAX_CONSOLE_FRACTION)
        }
    }

    private var splitRestored = false

    private fun restoreConsoleHeight(split: JSplitPane) {
        if (splitRestored) return
        splitRestored = true
        val fraction = settings.state.consoleFraction
            .coerceIn(MIN_CONSOLE_FRACTION, MAX_CONSOLE_FRACTION)
        restoringSplit = true
        split.dividerLocation = ((1f - fraction) * split.height).toInt()
        restoringSplit = false
    }

    private fun select(category: ActionCategory, action: AdbAction) {
        state.rememberSelection(category.id, action.id)
        nav.setSelection(category.id, action.id)
        editor.show(action)
    }

    override fun dispose() {
        state.dispose()
    }

    companion object {
        private const val MIN_CONSOLE_FRACTION = 0.25f
        private const val MAX_CONSOLE_FRACTION = 0.8f
    }
}
