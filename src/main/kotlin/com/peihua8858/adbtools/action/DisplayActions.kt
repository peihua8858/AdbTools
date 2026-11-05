package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers

val displayActions: List<AdbAction> = listOf(
    action(
        id = "rotation",
        title = "横竖屏切换",
        summary = "通过 settings put system user_rotation 切换屏幕方向。",
        fields = listOf(
            selectField(
                id = "rotation",
                label = "屏幕方向",
                options = listOf(
                    option("0", "竖屏（Portrait）"),
                    option("1", "横屏（Landscape）"),
                ),
            ),
        ),
    ) {
        val value = requireText("rotation", "屏幕方向")
        val result = shell("settings put system user_rotation $value")
        if (result.exitCode != 0) throw AdbException("切换失败")
        success("已切换为${if (value == "0") "竖屏" else "横屏"}模式")
    },
    action(
        id = "resolution",
        title = "屏幕分辨率修改",
        summary = "修改 wm density / wm size，支持恢复默认和常用预设。",
        fields = listOf(
            selectField(
                id = "resolution_op",
                label = "操作",
                options = listOf(
                    option("density", "修改 density（DPI）"),
                    option("size", "修改 size（分辨率）"),
                    option("reset", "重置为默认值"),
                    option("preset_1920", "预设 1920x1080 / density 160"),
                    option("preset_2160", "预设 2160x3840 / density 480"),
                ),
            ),
            textField(
                id = "resolution_value",
                label = "数值",
                placeholder = "density 如 320；size 如 1920x1080",
                visibleWhen = { it["resolution_op"] == "density" || it["resolution_op"] == "size" },
            ),
        ),
    ) {
        when (val op = text("resolution_op")) {
            "density" -> {
                val value = requireText("resolution_value", "density 值")
                if (!AdbParsers.isValidDensity(value)) throw AdbException("density 格式不正确，应为数字，如 160、320、480")
                shell("wm density $value")
                success("density 已设置为 $value")
            }
            "size" -> {
                val value = requireText("resolution_value", "size 值")
                if (!AdbParsers.isValidSize(value)) throw AdbException("size 格式不正确，应如 1920x1080")
                shell("wm size $value")
                success("size 已设置为 $value")
            }
            "reset" -> {
                shell("wm size reset")
                shell("wm density reset")
                success("分辨率和 density 已重置为默认值")
            }
            "preset_1920" -> {
                shell("wm size 1920x1080")
                shell("wm density 160")
                success("已设置为 1920x1080 / density 160")
            }
            "preset_2160" -> {
                shell("wm size 2160x3840")
                shell("wm density 480")
                success("已设置为 2160x3840 / density 480")
            }
            else -> throw AdbException("无效的分辨率操作：$op")
        }
    },
    action(
        id = "overdraw",
        title = "过度绘制开关",
        summary = "设置 debug.hwui.overdraw 开启/关闭过度绘制检测。",
        fields = listOf(
            selectField(
                id = "overdraw",
                label = "状态",
                options = listOf(
                    option("on", "开启过度绘制检测"),
                    option("off", "关闭过度绘制检测"),
                ),
            ),
        ),
    ) {
        val value = if (text("overdraw") == "on") "show" else "false"
        shell("setprop debug.hwui.overdraw $value")
        success("过度绘制检测已${if (value == "show") "开启" else "关闭"}")
    },
    action(
        id = "power_key",
        title = "模拟电源键",
        summary = "发送 KEYCODE_POWER（keyevent 26）。",
    ) {
        val result = shell("input keyevent 26")
        if (result.exitCode == 0) success("已模拟电源键按下") else error("模拟电源键失败")
    },
)
