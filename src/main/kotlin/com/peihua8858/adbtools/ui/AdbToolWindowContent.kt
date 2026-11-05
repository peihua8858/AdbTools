package com.peihua8858.adbtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peihua8858.adbtools.action.ActionRegistry
import com.peihua8858.adbtools.adb.AdbSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
fun AdbToolWindowContent(project: Project) {
    val settings = remember {
        ApplicationManager.getApplication().getService(AdbSettings::class.java)
            ?: error("AdbSettings 服务未注册")
    }
    val state = remember(project) { AdbToolState(project, settings) }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }

    val categories = ActionRegistry.categories
    val initialCategory = remember {
        categories.firstOrNull { it.id == settings.state.lastCategoryId }
    }
    var categoryId by remember { mutableStateOf(initialCategory?.id) }
    var actionId by remember {
        mutableStateOf(initialCategory?.actions?.firstOrNull { it.id == settings.state.lastActionId }?.id)
    }
    var favoriteIds by remember { mutableStateOf(settings.state.favoriteActionIds.toSet()) }
    val category = categories.firstOrNull { it.id == categoryId }
    val action = category?.actions?.firstOrNull { it.id == actionId }

    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviceSection(state)
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionNav(
                categories = categories,
                categoryId = category?.id,
                actionId = action?.id,
                favoriteIds = favoriteIds,
                onSelect = { selectedCategory, selectedAction ->
                    categoryId = selectedCategory.id
                    actionId = selectedAction.id
                    state.rememberSelection(selectedCategory.id, selectedAction.id)
                },
                onToggleFavorite = { actionToToggle ->
                    state.toggleFavorite(actionToToggle)
                    favoriteIds = settings.state.favoriteActionIds.toSet()
                },
            )
            Divider(orientation = Orientation.Vertical, modifier = Modifier.fillMaxHeight())

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val usableHeight = (maxHeight - SPLIT_HANDLE_HEIGHT).coerceAtLeast(0.dp)
                var consoleFraction by remember {
                    mutableStateOf(settings.state.consoleFraction.coerceIn(MIN_CONSOLE_FRACTION, MAX_CONSOLE_FRACTION))
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .height(usableHeight * (1f - consoleFraction))
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (action != null) {
                            val values = remember(action.id) {
                                mutableStateMapOf<String, String>().apply {
                                    action.fields.forEach { field -> put(field.id, state.defaultFor(field)) }
                                }
                            }
                            ActionEditor(state, action, values)
                        } else {
                            EmptySelectionHint()
                        }
                    }
                    SplitDivider(onDrag = { delta ->
                        if (usableHeight.value > 0f) {
                            consoleFraction = (consoleFraction + delta / usableHeight.value)
                                .coerceIn(MIN_CONSOLE_FRACTION, MAX_CONSOLE_FRACTION)
                            settings.state.consoleFraction = consoleFraction
                        }
                    })
                    ConsoleSection(state, Modifier.height(usableHeight * consoleFraction).fillMaxWidth())
                }
            }
        }
    }
}

private const val MIN_CONSOLE_FRACTION = 0.25f
private const val MAX_CONSOLE_FRACTION = 0.8f
