package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbParsers

val debugActions: List<AdbAction> = listOf(
    action(
        id = "content_read",
        title = "Content Provider 读取",
        summary = "执行 content read --uri <uri>。",
        fields = listOf(
            textField("uri", "Content URI", placeholder = "如 content://com.example.provider/items"),
        ),
    ) {
        val uri = requireText("uri", "URI")
        val result = shell("content read --uri ${AdbParsers.shellQuote(uri)}", echo = false)
        if (result.output().isBlank()) {
            warning("未返回数据")
        } else {
            success("读取结果：")
            result.lines.forEach { output(it) }
        }
    },
)
