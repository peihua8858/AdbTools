package com.peihua8858.adbtools.adb

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "AdbToolsSettings", storages = [Storage("adb-tools.xml")])
class AdbSettings : PersistentStateComponent<AdbSettings.SettingsState> {
    data class SettingsState(
        var adbPath: String = "",
        var outputDir: String = "",
        var lastPackage: String = "",
        var lastDebugKey: String = "",
        var lastSerial: String = "",
        var lastDeeplink: String = "",
        var lastKeyword: String = "",
        var lastCategoryId: String = "",
        var lastActionId: String = "",
        var favoriteActionIds: MutableList<String> = mutableListOf(),
        var fieldHistory: MutableMap<String, String> = mutableMapOf(),
        var consoleFraction: Float = 0.5f,
    )

    private var myState = SettingsState()

    override fun getState(): SettingsState = myState

    override fun loadState(state: SettingsState) {
        myState = state
    }
}
