package com.peihua8858.adbtools.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab

class AdbToolWindowFactory : ToolWindowFactory {
    companion object {
        const val TOOL_WINDOW_ID = "AdbTools"
    }

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab(focusOnClickInside = true) {
            AdbToolWindowContent(project)
        }
    }
}
