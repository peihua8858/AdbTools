package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import java.io.File

val logActions: List<AdbAction> = listOf(
    action(
        id = "logcat",
        title = "日志筛选",
        summary = "实时 logcat，支持关键字、多关键词正则或全部缓冲区；使用“停止”按钮结束。",
        fields = listOf(
            selectField(
                id = "log_mode",
                label = "模式",
                options = listOf(
                    option("keyword", "关键字过滤"),
                    option("multi", "多关键词（正则）"),
                    option("all", "所有缓冲区"),
                ),
            ),
            textField(
                id = "log_filter",
                label = "过滤条件",
                placeholder = "关键字，或用 | 分隔的正则，如 Error|Exception|ANR",
                visibleWhen = { it["log_mode"] != "all" },
            ),
        ),
        interruptible = true,
    ) {
        warning("日志输出中，可点击控制台的“停止”按钮结束")
        when (val mode = text("log_mode")) {
            "all" -> shell("logcat -b all")
            "keyword" -> {
                val keyword = requireText("log_filter", "过滤关键字")
                shell("logcat", lineFilter = { it.contains(keyword) })
            }
            "multi" -> {
                val pattern = requireText("log_filter", "过滤条件")
                val regex = try {
                    Regex(pattern)
                } catch (e: Exception) {
                    throw AdbException("无效的正则表达式：${e.message}")
                }
                shell("logcat", lineFilter = { regex.containsMatchIn(it) })
            }
            else -> throw AdbException("未知的日志模式：$mode")
        }
    },
    action(
        id = "anr_export",
        title = "ANR 日志导出",
        summary = "将 /data/anr/ 导出到本地输出目录；失败时自动 root 后重试。",
    ) {
        val target = File(outputDir, "anr_logs_${timestamp()}")
        target.mkdirs()
        info("正在导出 /data/anr/ 目录...")
        var result = adb("pull", "/data/anr/", target.absolutePath)
        if (result.exitCode != 0) {
            error("导出失败，可能需要 root 权限，尝试 adb root 后重试")
            ensureRoot()
            result = adb("pull", "/data/anr/", target.absolutePath)
            if (result.exitCode != 0) throw AdbException("ANR 日志导出失败")
        }
        success("ANR 日志已导出到：${target.absolutePath}")
    },
)
