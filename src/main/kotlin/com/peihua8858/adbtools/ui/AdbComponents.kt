package com.peihua8858.adbtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation as GestureOrientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peihua8858.adbtools.action.ActionCategory
import com.peihua8858.adbtools.action.ActionField
import com.peihua8858.adbtools.action.ActionRegistry
import com.peihua8858.adbtools.action.AdbAction
import com.peihua8858.adbtools.action.FieldKind
import com.peihua8858.adbtools.adb.LogLevel
import com.peihua8858.adbtools.adb.ProcessEntry
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.awt.Cursor

@Composable
fun DeviceSection(state: AdbToolState) {
    var showDevices by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val device = state.selectedDevice()
    val rebootAction = remember {
        ActionRegistry.categories.flatMap { it.actions }.first { it.id == "device_reboot" }
    }
    val adbRestartAction = remember {
        ActionRegistry.categories.flatMap { it.actions }.first { it.id == "adb_restart" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = device?.displayName ?: "设备：未连接",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = state.adbError ?: state.adbPath ?: "正在检测 adb...",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = if (state.adbError != null) JewelTheme.globalColors.text.error else JewelTheme.globalColors.text.info,
                    ),
                )
            }
            OutlinedButton(onClick = { state.refreshDevices() }, enabled = !state.busy) { Text("刷新") }
            OutlinedButton(onClick = { showDevices = !showDevices }) { Text("设备") }
            OutlinedButton(onClick = { showSettings = !showSettings }) { Text("设置") }
            OutlinedButton(
                onClick = { state.run(rebootAction, emptyMap()) },
                enabled = !state.busy && device != null,
            ) { Text("重启设备") }
            OutlinedButton(
                onClick = { state.run(adbRestartAction, emptyMap()) },
                enabled = !state.busy,
            ) { Text("重启 adb") }
        }

        if (showDevices) {
            Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
            if (state.devices.isEmpty()) {
                Text("未检测到已连接设备，请确认 USB 调试已开启", style = subtleStyle())
            } else {
                state.devices.forEach { item ->
                    RadioButtonRow(
                        text = item.displayName,
                        selected = item.serial == state.selectedSerial,
                        onClick = { state.selectDevice(item.serial) },
                        enabled = item.isOnline,
                    )
                }
            }
        }

        if (showSettings) {
            Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
            SettingsPanel(state)
        }
    }
}

@Composable
private fun SettingsPanel(state: AdbToolState) {
    var adbPath by remember { mutableStateOf(TextFieldValue(state.settings.state.adbPath)) }
    var outputDir by remember { mutableStateOf(TextFieldValue(state.settings.state.outputDir)) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("adb 路径（留空自动检测）")
        TextField(
            value = adbPath,
            onValueChange = { adbPath = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("如 ~/Library/Android/sdk/platform-tools/adb") },
        )
        FieldLabel("输出目录（留空使用项目下 adb-output）")
        TextField(
            value = outputDir,
            onValueChange = { outputDir = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("如 ~/Downloads/adb-output") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DefaultButton(onClick = {
                state.settings.state.adbPath = adbPath.text.trim()
                state.settings.state.outputDir = outputDir.text.trim()
                state.onSettingsChanged()
            }) { Text("保存并重新检测") }
            OutlinedButton(onClick = { state.openOutputDirectory() }) { Text("打开输出目录") }
        }
        Text(
            "当前输出目录：${state.outputDirectory().absolutePath}",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = subtleStyle(),
        )
        Text(
            "当前 adb：${state.adbPath ?: "未检测到"}",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = subtleStyle(),
        )
    }
}

@Composable
fun ActionNav(
    categories: List<ActionCategory>,
    categoryId: String?,
    actionId: String?,
    favoriteIds: Set<String>,
    onSelect: (ActionCategory, AdbAction) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var expandedIds by remember {
        mutableStateOf(if (categoryId != null) setOf(categoryId) else emptySet())
    }
    val keyword = query.text.trim()
    val searching = keyword.isNotEmpty()

    val filtered = if (!searching) {
        categories
    } else {
        categories.mapNotNull { category ->
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
    }
    val favoriteActions = if (searching) {
        emptyList()
    } else {
        val index = categories.flatMap { category -> category.actions.map { it.id to (category to it) } }.toMap()
        favoriteIds.mapNotNull { index[it] }
    }

    Column(modifier = Modifier.width(NAV_WIDTH).fillMaxHeight()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            placeholder = { Text("搜索功能") },
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (favoriteActions.isNotEmpty()) {
                NavSectionLabel("收藏")
                favoriteActions.forEach { (category, action) ->
                    NavActionRow(
                        category = category,
                        action = action,
                        selected = category.id == categoryId && action.id == actionId,
                        favorite = true,
                        indent = 8.dp,
                        onSelect = onSelect,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            if (searching) {
                if (filtered.isEmpty()) {
                    Text(
                        text = "未找到匹配功能",
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        style = subtleStyle(),
                    )
                }
                filtered.forEach { category ->
                    NavSectionLabel(category.title)
                    category.actions.forEach { action ->
                        NavActionRow(
                            category = category,
                            action = action,
                            selected = category.id == categoryId && action.id == actionId,
                            favorite = action.id in favoriteIds,
                            indent = 18.dp,
                            onSelect = onSelect,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }
            } else {
                categories.forEach { category ->
                    val expanded = category.id in expandedIds || category.id == categoryId
                    NavRow(
                        onClick = {
                            expandedIds = if (expanded) expandedIds - category.id else expandedIds + category.id
                        },
                        padding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = if (expanded) "▾" else "▸",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
                        )
                        Text(
                            text = category.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    if (!expanded) return@forEach

                    category.actions.forEach { action ->
                        NavActionRow(
                            category = category,
                            action = action,
                            selected = category.id == categoryId && action.id == actionId,
                            favorite = action.id in favoriteIds,
                            indent = 22.dp,
                            onSelect = onSelect,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavSectionLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JewelTheme.globalColors.text.info,
        ),
    )
}

@Composable
private fun NavActionRow(
    category: ActionCategory,
    action: AdbAction,
    selected: Boolean,
    favorite: Boolean,
    indent: Dp,
    onSelect: (ActionCategory, AdbAction) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    NavRow(
        onClick = { onSelect(category, action) },
        selected = selected,
        padding = PaddingValues(start = indent, end = 4.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = action.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
        )
        Text(
            text = if (favorite) "★" else "☆",
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable { onToggleFavorite(action.id) }
                .padding(horizontal = 3.dp),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = if (favorite) JewelTheme.globalColors.text.warning else JewelTheme.globalColors.text.info,
            ),
        )
    }
}

@Composable
private fun NavRow(
    onClick: () -> Unit,
    padding: PaddingValues,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    selected -> navSelectionColor()
                    hovered -> navHoverColor()
                    else -> Color.Transparent
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun EmptySelectionHint() {
    Column(
        modifier = Modifier.padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "选择一个功能",
            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = "展开左侧分类，点击功能查看参数并执行",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.info,
            ),
        )
    }
}

@Composable
fun SplitDivider(modifier: Modifier = Modifier, onDrag: (Float) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .height(SPLIT_HANDLE_HEIGHT)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .hoverable(interaction)
            .draggable(
                orientation = GestureOrientation.Vertical,
                state = rememberDraggableState { delta -> onDrag(delta) },
                interactionSource = interaction,
            )
            .background(if (hovered) navHoverColor() else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun navSelectionColor(): Color = JewelTheme.globalColors.text.info.copy(alpha = 0.16f)

@Composable
private fun navHoverColor(): Color = JewelTheme.globalColors.text.info.copy(alpha = 0.07f)

@Composable
fun ActionEditor(
    state: AdbToolState,
    action: AdbAction,
    values: MutableMap<String, String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                action.title,
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
            )
            Text(action.summary, style = subtleStyle())
        }

        if (action.id == "process_kill_by_package") {
            ProcessKillPanel(state)
            return@Column
        }

        action.fields.filter { it.visibleWhen(values) }.forEach { field ->
            ActionFieldEditor(state, action, field, values)
        }

        val confirmRequired = action.fields.any { it.id == "confirm" } && values["confirm"] != "true"
        val deviceRequired = action.requiresDevice && state.selectedSerial == null
        val canRun = !state.busy && !confirmRequired && !deviceRequired

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultButton(onClick = { state.run(action, values.toMap()) }, enabled = canRun) {
                Text(if (state.busy) "执行中..." else "执行")
            }
            if (state.busy) {
                OutlinedButton(onClick = { state.stop() }) { Text("停止") }
            }
            if (deviceRequired) {
                Text("请先选择设备", style = subtleStyle())
            } else if (confirmRequired) {
                Text("请先勾选确认项", style = subtleStyle())
            } else if (state.busy && state.runningTitle != null) {
                Text("运行中：${state.runningTitle}", style = subtleStyle())
            }
        }
    }
}

@Composable
private fun ActionFieldEditor(
    state: AdbToolState,
    action: AdbAction,
    field: ActionField,
    values: MutableMap<String, String>,
) {
    when (field.kind) {
        FieldKind.TEXT -> {
            val initial = remember(action.id, field.id) { values[field.id] ?: field.defaultValue }
            var value by remember(action.id, field.id) { mutableStateOf(TextFieldValue(initial)) }
            val history = state.fieldHistory[field.id].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FieldLabel(field.label)
                TextField(
                    value = value,
                    onValueChange = {
                        value = it
                        values[field.id] = it.text
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    placeholder = { if (field.placeholder.isNotEmpty()) Text(field.placeholder) },
                )
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("最近：", style = subtleStyle())
                        history.forEach { past ->
                            OutlinedButton(
                                onClick = {
                                    value = TextFieldValue(past)
                                    values[field.id] = past
                                },
                                enabled = !state.busy,
                            ) {
                                Text(
                                    text = past,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                if (field.hint.isNotEmpty()) Text(field.hint, style = subtleStyle())
            }
        }
        FieldKind.FILE -> {
            val initial = remember(action.id, field.id) { values[field.id] ?: field.defaultValue }
            var value by remember(action.id, field.id) { mutableStateOf(TextFieldValue(initial)) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FieldLabel(field.label)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextField(
                        value = value,
                        onValueChange = {
                            value = it
                            values[field.id] = it.text
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                        placeholder = { if (field.placeholder.isNotEmpty()) Text(field.placeholder) },
                    )
                    OutlinedButton(
                        onClick = {
                            val chosen = FileChooser.chooseFile(
                                FileChooserDescriptorFactory.createSingleFileDescriptor("apk"),
                                state.project,
                                null,
                            )
                            if (chosen != null) {
                                value = TextFieldValue(chosen.path)
                                values[field.id] = chosen.path
                            }
                        },
                        enabled = !state.busy,
                    ) { Text("选择") }
                }
                if (field.hint.isNotEmpty()) Text(field.hint, style = subtleStyle())
            }
        }
        FieldKind.CHECKBOX -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CheckboxRow(
                    text = field.label,
                    checked = values[field.id] == "true",
                    onCheckedChange = { values[field.id] = it.toString() },
                    enabled = !state.busy,
                )
                if (field.hint.isNotEmpty()) Text(field.hint, style = subtleStyle())
            }
        }
        FieldKind.SELECT -> {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FieldLabel(field.label)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    field.options.forEach { option ->
                        val selected = (values[field.id] ?: field.defaultValue) == option.value
                        if (selected) {
                            DefaultButton(onClick = { values[field.id] = option.value }, enabled = !state.busy) {
                                Text(option.label, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(onClick = { values[field.id] = option.value }, enabled = !state.busy) {
                                Text(option.label, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (field.hint.isNotEmpty()) Text(field.hint, style = subtleStyle())
            }
        }
        FieldKind.INFO -> Text(field.label, style = subtleStyle())
    }
}

@Composable
fun ProcessKillPanel(state: AdbToolState) {
    var keyword by remember { mutableStateOf(TextFieldValue("")) }
    val processes = remember { mutableStateListOf<ProcessEntry>() }
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("包名（支持部分匹配）")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f),
                enabled = !state.busy,
                placeholder = { Text("如 genie") },
            )
            OutlinedButton(
                onClick = {
                    val query = keyword.text.trim()
                    state.runTask("查询进程") {
                        val list = state.queryProcesses(query)
                        processes.clear()
                        processes.addAll(list)
                        checked.clear()
                        list.forEach { checked[it.pid] = false }
                        if (list.isEmpty()) {
                            warning("未找到包含 '$query' 的进程")
                        } else {
                            success("共找到 ${list.size} 个进程")
                        }
                    }
                },
                enabled = !state.busy && keyword.text.isNotBlank(),
            ) { Text("查询") }
        }

        val selectedPids = checked.filterValues { it }.keys.toList()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DefaultButton(
                onClick = {
                    state.runTask("Kill 选中进程") {
                        killPids(selectedPids)
                        delay(1000)
                        val remaining = state.queryProcesses(keyword.text.trim())
                        processes.clear()
                        processes.addAll(remaining)
                        checked.clear()
                        remaining.forEach { checked[it.pid] = false }
                        if (remaining.isEmpty()) {
                            success("已无匹配进程")
                        } else {
                            info("当前剩余 ${remaining.size} 个进程")
                        }
                    }
                },
                enabled = !state.busy && selectedPids.isNotEmpty(),
            ) { Text("Kill 选中（${selectedPids.size}）") }
            OutlinedButton(
                onClick = {
                    processes.forEach { checked[it.pid] = true }
                },
                enabled = !state.busy && processes.isNotEmpty(),
            ) { Text("全选") }
            OutlinedButton(
                onClick = { processes.forEach { checked[it.pid] = false } },
                enabled = !state.busy && processes.isNotEmpty(),
            ) { Text("取消全选") }
        }

        if (processes.isEmpty()) {
            Text("输入包名后点击查询，结果会显示在这里", style = subtleStyle())
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                processes.take(MAX_PROCESS_ROWS).forEach { entry ->
                    CheckboxRow(
                        text = "PID=${entry.pid}  ${entry.name}",
                        checked = checked[entry.pid] == true,
                        onCheckedChange = { checked[entry.pid] = it },
                        enabled = !state.busy,
                    )
                }
                if (processes.size > MAX_PROCESS_ROWS) {
                    Text("仅显示前 $MAX_PROCESS_ROWS 个进程，请使用更精确的关键字", style = subtleStyle())
                }
            }
        }
    }
}

@Composable
fun ConsoleSection(state: AdbToolState, modifier: Modifier = Modifier) {
    var autoScroll by remember { mutableStateOf(true) }
    var onlyIssues by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val lines = if (onlyIssues) {
        state.output.filter { it.level == LogLevel.WARNING || it.level == LogLevel.ERROR }
    } else {
        state.output
    }

    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("输出", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            if (state.busy) {
                Text("运行中：${state.runningTitle.orEmpty()}", style = subtleStyle(), maxLines = 1)
                OutlinedButton(onClick = { state.stop() }) { Text("停止") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CheckboxRow(
                text = "自动滚动",
                checked = autoScroll,
                onCheckedChange = { autoScroll = it },
            )
            CheckboxRow(
                text = "只看问题",
                checked = onlyIssues,
                onCheckedChange = { onlyIssues = it },
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { state.copyLastCommand() }) { Text("复制命令") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { state.clearOutput() }) { Text("清空") }
            OutlinedButton(onClick = { state.copyOutput() }) { Text("复制") }
            OutlinedButton(onClick = { state.saveOutput() }) { Text("保存") }
        }
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = if (onlyIssues) {
                            "没有警告或错误输出"
                        } else {
                            "暂无输出，执行功能后命令输出会显示在这里"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = subtleStyle(),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        items(lines.size) { index ->
                            val line = lines[index]
                            if (line.level == LogLevel.DIVIDER || line.text.isEmpty()) {
                                Divider(
                                    orientation = Orientation.Horizontal,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    style = JewelTheme.consoleTextStyle.copy(
                                        color = logColor(line.level),
                                        fontWeight = if (line.level == LogLevel.TITLE) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun subtleStyle(): TextStyle = JewelTheme.defaultTextStyle.copy(
    fontSize = 11.sp,
    color = JewelTheme.globalColors.text.info,
)

@Composable
private fun logColor(level: LogLevel): Color = when (level) {
    LogLevel.COMMAND, LogLevel.INFO -> JewelTheme.globalColors.text.info
    LogLevel.SUCCESS -> if (JewelTheme.isDark) Color(0xFF6AAB73) else Color(0xFF1A7F37)
    LogLevel.WARNING -> JewelTheme.globalColors.text.warning
    LogLevel.ERROR -> JewelTheme.globalColors.text.error
    else -> JewelTheme.globalColors.text.normal
}

private const val MAX_PROCESS_ROWS = 100
private val NAV_WIDTH = 200.dp
internal val SPLIT_HANDLE_HEIGHT = 9.dp
