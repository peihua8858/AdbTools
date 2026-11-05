package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.action.ActionCategory
import com.peihua8858.adbtools.action.ActionRegistry
import com.peihua8858.adbtools.action.AdbAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ActionNavPanel(
    private val state: AdbToolState,
    private val onSelect: (ActionCategory, AdbAction) -> Unit,
) : JPanel(BorderLayout()) {

    private val searchField = JBTextField()
    private val listPanel = transparentPanel()

    private var selectedCategoryId: String? = null
    private var selectedActionId: String? = null
    private val expandedIds = mutableSetOf<String>()

    init {
        isOpaque = false
        preferredSize = Dimension(JBUI.scale(210), 0)
        minimumSize = Dimension(JBUI.scale(160), 0)

        searchField.emptyText.text = "搜索功能"
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = rebuild()
            override fun removeUpdate(e: DocumentEvent) = rebuild()
            override fun changedUpdate(e: DocumentEvent) = rebuild()
        })

        listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)
        listPanel.border = JBUI.Borders.emptyBottom(6)

        val scroll = JBScrollPane(listPanel).apply {
            border = JBUI.Borders.emptyTop(4)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(searchField, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        rebuild()
    }

    fun setSelection(categoryId: String?, actionId: String?) {
        selectedCategoryId = categoryId
        selectedActionId = actionId
        if (categoryId != null) expandedIds += categoryId
        rebuild()
    }

    fun refresh() = rebuild()

    private fun rebuild() {
        listPanel.removeAll()
        val keyword = searchField.text.trim()
        val categories = ActionRegistry.categories

        if (keyword.isNotEmpty()) {
            val filtered = categories.mapNotNull { category ->
                if (category.title.contains(keyword, ignoreCase = true)) {
                    category
                } else {
                    val matched = category.actions.filter {
                        it.title.contains(keyword, ignoreCase = true) ||
                            it.summary.contains(keyword, ignoreCase = true)
                    }
                    if (matched.isEmpty()) null else category.copy(actions = matched)
                }
            }
            if (filtered.isEmpty()) {
                listPanel.add(subtleLabel("未找到匹配功能", paddingTop = 10))
            }
            filtered.forEach { category ->
                listPanel.add(sectionLabel(category.title))
                category.actions.forEach { action -> addActionRow(category, action, indent = 18) }
            }
        } else {
            val favoriteIds = state.favoriteIds()
            if (favoriteIds.isNotEmpty()) {
                val index = categories
                    .flatMap { category -> category.actions.map { it.id to (category to it) } }
                    .toMap()
                val favorites = favoriteIds.mapNotNull { index[it] }
                if (favorites.isNotEmpty()) {
                    listPanel.add(sectionLabel("收藏"))
                    favorites.forEach { (category, action) ->
                        addActionRow(category, action, indent = 8, favorite = true)
                    }
                    listPanel.add(spacer(6))
                }
            }

            categories.forEach { category ->
                val expanded = category.id in expandedIds
                listPanel.add(categoryHeader(category, expanded))
                if (expanded) {
                    category.actions.forEach { action -> addActionRow(category, action, indent = 22) }
                }
            }
        }

        listPanel.revalidate()
        listPanel.repaint()
    }

    private fun addActionRow(
        category: ActionCategory,
        action: AdbAction,
        indent: Int,
        favorite: Boolean = state.favoriteIds().contains(action.id),
    ) {
        val selected = category.id == selectedCategoryId && action.id == selectedActionId
        val title = JBLabel(action.title).apply {
            font = uiFont(12, bold = selected)
            toolTipText = action.summary
        }
        val star = JBLabel(if (favorite) "★" else "☆").apply {
            font = uiFont(14)
            foreground = if (favorite) JBColor(0xD19A2E, 0xE0B65A) else subtleColor
            border = JBUI.Borders.emptyLeft(6)
            toolTipText = if (favorite) "取消收藏" else "收藏"
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return
                    state.toggleFavorite(action.id)
                    rebuild()
                }
            })
        }
        val row = transparentPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, indent, 4, 4)
            add(title, BorderLayout.CENTER)
            add(star, BorderLayout.EAST)
        }
        if (selected) {
            row.isOpaque = true
            row.background = navSelectedColor
        }
        installRowHandlers(row, selected = selected, excluded = star) {
            onSelect(category, action)
        }
        listPanel.add(row.stretchWidth())
    }

    /**
     * 点击 / 悬停必须装到行和所有子组件上：带 tooltip 的 JLabel 会被 ToolTipManager
     * 注册鼠标监听并成为事件目标，只装在行上会导致点文字无反应。
     */
    private fun installRowHandlers(
        row: JComponent,
        selected: Boolean = false,
        excluded: JComponent? = null,
        onClick: () -> Unit,
    ) {
        fun attach(component: JComponent) {
            component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            component.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (component !== excluded && e.button == MouseEvent.BUTTON1) onClick()
                }

                override fun mouseEntered(e: MouseEvent) {
                    if (selected) return
                    row.isOpaque = true
                    row.background = navHoverColor
                    row.repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    if (selected) return
                    if (!row.contains(SwingUtilities.convertPoint(component, e.point, row))) {
                        row.isOpaque = false
                        row.repaint()
                    }
                }
            })
            if (component is Container) {
                component.components.filterIsInstance<JComponent>().forEach { attach(it) }
            }
        }
        attach(row)
    }

    private fun categoryHeader(category: ActionCategory, expanded: Boolean): JComponent {
        val row = transparentPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            border = JBUI.Borders.empty(5, 6)
            add(JBLabel(if (expanded) "▾" else "▸").apply { font = uiFont(12) })
            add(JBLabel(category.title).apply { font = uiFont(13, bold = true) })
        }
        installRowHandlers(row) {
            if (category.id in expandedIds) expandedIds -= category.id else expandedIds += category.id
            rebuild()
        }
        return row.stretchWidth()
    }

    private fun sectionLabel(text: String): JComponent =
        JBLabel(text).apply {
            font = uiFont(11, bold = true)
            foreground = subtleColor
            border = JBUI.Borders.empty(8, 8, 2, 8)
        }.stretchWidth()

    private fun subtleLabel(text: String, paddingTop: Int = 0): JComponent =
        JBLabel(text).apply {
            font = uiFont(11)
            foreground = subtleColor
            border = JBUI.Borders.empty(paddingTop, 8, 0, 8)
        }.stretchWidth()
}
