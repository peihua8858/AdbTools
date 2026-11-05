package com.peihua8858.adbtools.adb

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

class AdbSettingsSerializationTest : BasePlatformTestCase() {
    fun testFavoritesHistoryAndSplitSurviveSerialization() {
        val state = AdbSettings.SettingsState().apply {
            lastCategoryId = "app"
            lastActionId = "runtime"
            favoriteActionIds.addAll(listOf("logcat", "runtime"))
            fieldHistory["package"] = "com.example.one\ncom.example.two"
            consoleFraction = 0.62f
        }

        val restored = XmlSerializer.deserialize(
            XmlSerializer.serialize(state),
            AdbSettings.SettingsState::class.java,
        )

        assertEquals(state.lastCategoryId, restored.lastCategoryId)
        assertEquals(state.lastActionId, restored.lastActionId)
        assertEquals(state.favoriteActionIds, restored.favoriteActionIds)
        assertEquals(state.fieldHistory, restored.fieldHistory)
        assertEquals(0.62f, restored.consoleFraction, 0.0001f)
    }
}
