package com.peihua8858.adbtools.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRegistryTest {
    @Test
    fun `all 28 script features are covered by unique actions`() {
        val allActions = ActionRegistry.categories.flatMap { it.actions }

        assertEquals(9, ActionRegistry.categories.size)
        assertTrue(allActions.size >= 28)
        assertEquals(allActions.size, allActions.map { it.id }.distinct().size)
        allActions.forEach { action ->
            assertTrue(action.title.isNotBlank())
            assertTrue(action.summary.isNotBlank())
        }
    }

    @Test
    fun `actions are grouped by what they operate on`() {
        fun categoryOf(actionId: String) = ActionRegistry.categories
            .first { category -> category.actions.any { it.id == actionId } }
            .id

        assertEquals("app", categoryOf("top_activity"))
        assertEquals("app", categoryOf("runtime"))
        assertEquals("display", categoryOf("overdraw"))
        assertEquals("system", categoryOf("settings_get"))
        assertEquals("system", categoryOf("settings_put"))
        assertEquals("system", categoryOf("getprop"))
        assertEquals("device", categoryOf("audio_info"))
        assertEquals("log", categoryOf("logcat"))
        assertEquals("log", categoryOf("anr_export"))
        assertEquals("debug", categoryOf("content_read"))
    }

    @Test
    fun `install uninstall and device commands live in expected categories`() {
        fun categoryOf(actionId: String) = ActionRegistry.categories
            .first { category -> category.actions.any { it.id == actionId } }
            .id

        assertEquals("app", categoryOf("app_install"))
        assertEquals("app", categoryOf("app_uninstall"))
        assertEquals("app", categoryOf("app_clear_data"))
        assertEquals("device", categoryOf("device_reboot"))
        assertEquals("device", categoryOf("adb_restart"))

        val install = ActionRegistry.categories
            .flatMap { it.actions }
            .first { it.id == "app_install" }
        assertEquals(
            FieldKind.FILE,
            install.fields.first { it.id == "apk" }.kind,
        )
    }

    @Test
    fun `select fields have a valid default option`() {
        ActionRegistry.categories
            .flatMap { it.actions }
            .flatMap { it.fields }
            .filter { it.kind == FieldKind.SELECT }
            .forEach { field ->
                assertTrue("${field.id} 没有选项", field.options.isNotEmpty())
                assertNotNull(
                    "${field.id} 的默认值不在选项中",
                    field.options.firstOrNull { it.value == field.defaultValue },
                )
            }
    }

    @Test
    fun `every field id is unique inside its action`() {
        ActionRegistry.categories
            .flatMap { it.actions }
            .forEach { action ->
                val ids = action.fields.map { it.id }
                assertEquals("${action.id} 存在重复字段", ids.size, ids.distinct().size)
            }
    }
}
