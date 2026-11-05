package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.action.ActionRegistry
import com.peihua8858.adbtools.adb.AdbSettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.TransferHandler

class AdbToolWindowSmokeTest : BasePlatformTestCase() {
    fun testOpenActionRegistered() {
        assertNotNull(
            "Tools 菜单入口应已注册",
            ActionManager.getInstance().getAction("AdbTools.OpenToolWindow"),
        )
    }

    @Suppress("DEPRECATION")
    fun testFactoryCreatesSwingContent() {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.getToolWindow("AdbTools")
            ?: manager.registerToolWindow("AdbTools", true, ToolWindowAnchor.RIGHT, project, true)

        AdbToolWindowFactory().createToolWindowContent(project, toolWindow)

        assertTrue(
            "工具窗口应包含至少一个内容页",
            toolWindow.contentManager.contents.isNotEmpty(),
        )
    }

    fun testPanelBuildsOnEdt() {
        val settings = ApplicationManager.getApplication().getService(AdbSettings::class.java)
        var panel: AdbToolWindowPanel? = null
        ApplicationManager.getApplication().invokeAndWait {
            panel = AdbToolWindowPanel(project, settings)
        }
        assertNotNull(panel)
        assertTrue("主面板应包含设备栏和内容区", panel!!.componentCount >= 2)
        panel!!.dispose()
    }

    fun testNavClickWorksOnTextLabels() {
        val settings = ApplicationManager.getApplication().getService(AdbSettings::class.java)
        val category = ActionRegistry.categories.first()
        val action = category.actions.first()
        var state: AdbToolState? = null
        var nav: ActionNavPanel? = null
        var picked: String? = null

        ApplicationManager.getApplication().invokeAndWait {
            state = AdbToolState(project, settings)
            nav = ActionNavPanel(state!!) { _, selected -> picked = selected.id }
        }

        ApplicationManager.getApplication().invokeAndWait {
            val header = findLabel(nav!!, category.title)
            assertNotNull("应能定位分类标题标签", header)
            click(header!!)
            val title = findLabel(nav!!, action.title)
            assertNotNull("展开后应能定位功能标题标签", title)
            click(title!!)
        }

        assertEquals("点击功能标题文字也应触发选择", action.id, picked)
        state?.dispose()
    }

    fun testInstallAcceptsDroppedApk() {
        val settings = ApplicationManager.getApplication().getService(AdbSettings::class.java)
        val action = ActionRegistry.categories.flatMap { it.actions }.first { it.id == "app_install" }
        val apk = File.createTempFile("adbtools-drop", ".apk").apply {
            writeText("apk")
            deleteOnExit()
        }
        var state: AdbToolState? = null
        var input: JBTextField? = null

        ApplicationManager.getApplication().invokeAndWait {
            state = AdbToolState(project, settings)
            val panel = ActionEditorPanel(project, state!!)
            panel.show(action)
            input = findComponent(panel, JBTextField::class.java)
        }

        val target = input!!
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                flavor == DataFlavor.javaFileListFlavor

            override fun getTransferData(flavor: DataFlavor): Any = listOf(apk)
        }
        ApplicationManager.getApplication().invokeAndWait {
            val accepted = target.transferHandler.importData(
                TransferHandler.TransferSupport(target, transferable),
            )
            assertTrue("拖入 APK 应被接受", accepted)
        }

        assertEquals("拖入后路径应写入输入框", apk.absolutePath, target.text)
        state?.dispose()
    }

    private fun findLabel(root: Component, text: String): JLabel? {
        if (root is JLabel && root.text == text) return root
        if (root is Container) {
            root.components.forEach { child -> findLabel(child, text)?.let { return it } }
        }
        return null
    }

    private fun <T : Component> findComponent(root: Component, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root is Container) {
            for (child in root.components) {
                findComponent(child, type)?.let { return it }
            }
        }
        return null
    }

    private fun click(component: JComponent) {
        component.dispatchEvent(
            MouseEvent(
                component,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                component.width / 2,
                component.height / 2,
                1,
                false,
                MouseEvent.BUTTON1,
            ),
        )
    }
}
