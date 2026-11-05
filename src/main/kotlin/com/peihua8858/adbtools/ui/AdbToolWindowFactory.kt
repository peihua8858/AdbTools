package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.adb.AdbSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class AdbToolWindowFactory : ToolWindowFactory {
    companion object {
        const val TOOL_WINDOW_ID = "AdbTools"
    }

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val settings = ApplicationManager.getApplication().getService(AdbSettings::class.java)
            ?: error("AdbSettings 服务未注册")
        val panel = AdbToolWindowPanel(project, settings)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        content.isCloseable = false
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
