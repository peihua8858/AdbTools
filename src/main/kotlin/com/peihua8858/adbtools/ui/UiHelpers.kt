package com.peihua8858.adbtools.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel

internal val subtleColor: java.awt.Color =
    JBColor.namedColor("Label.infoForeground", JBColor(0x767676, 0x9B9B9B))

internal val navHoverColor: java.awt.Color = JBColor(ColorHolder.hoverLight, ColorHolder.hoverDark)
internal val navSelectedColor: java.awt.Color = JBColor(ColorHolder.selectedLight, ColorHolder.selectedDark)

private object ColorHolder {
    val hoverLight = java.awt.Color(0xEAF1FC)
    val hoverDark = java.awt.Color(0x31445E)
    val selectedLight = java.awt.Color(0xD6E4FA)
    val selectedDark = java.awt.Color(0x3B567A)
}

internal fun uiFont(size: Int, bold: Boolean = false): Font =
    UIUtil.getLabelFont().deriveFont(if (bold) Font.BOLD else Font.PLAIN, size.toFloat())

internal fun transparentPanel(layout: LayoutManager = BorderLayout()): JPanel =
    JPanel(layout).apply { isOpaque = false }

internal fun JComponent.stretchWidth(): JComponent {
    alignmentX = Component.LEFT_ALIGNMENT
    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    return this
}

internal fun spacer(height: Int = 4): JComponent =
    JPanel().apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(height))
        preferredSize = Dimension(0, JBUI.scale(height))
        minimumSize = Dimension(0, JBUI.scale(height))
    }
