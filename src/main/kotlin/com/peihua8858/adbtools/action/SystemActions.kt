package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbParsers

private val settingsNamespaces = listOf(
    option("secure", "secure"),
    option("system", "system"),
    option("global", "global"),
)

val systemActions: List<AdbAction> = listOf(
    action(
        id = "settings_get",
        title = "获取 settings 值",
        summary = "读取 secure / system / global 命名空间下的配置项。",
        fields = listOf(
            selectField("namespace", "命名空间", settingsNamespaces),
            textField("key", "key", placeholder = "如 user_rotation"),
        ),
    ) {
        val namespace = requireText("namespace", "命名空间")
        val key = requireText("key", "key")
        val value = shell(
            "settings get ${AdbParsers.shellQuote(namespace)} ${AdbParsers.shellQuote(key)}",
            echo = false,
        ).firstLine()
        success("$namespace/$key = ${value.ifBlank { "空" }}")
    },
    action(
        id = "settings_put",
        title = "设置 settings 值",
        summary = "写入 secure / system / global 命名空间下的配置项。",
        fields = listOf(
            selectField("namespace", "命名空间", settingsNamespaces),
            textField("key", "key", placeholder = "如 user_rotation"),
            textField("value", "value", placeholder = "要写入的值"),
        ),
    ) {
        val namespace = requireText("namespace", "命名空间")
        val key = requireText("key", "key")
        val value = requireText("value", "value")
        shell(
            "settings put ${AdbParsers.shellQuote(namespace)} " +
                "${AdbParsers.shellQuote(key)} ${AdbParsers.shellQuote(value)}",
        )
        success("已设置 $namespace/$key = $value")
    },
    action(
        id = "getprop",
        title = "查看系统属性",
        summary = "属性名留空时查看全部 getprop。",
        fields = listOf(textField("prop", "属性名", placeholder = "留空查看所有属性")),
    ) {
        val prop = text("prop")
        if (prop.isEmpty()) {
            val result = shell("getprop", echo = false)
            if (result.lines.isEmpty()) {
                warning("未返回任何系统属性")
            } else {
                result.lines.forEach { output(it) }
            }
        } else {
            val value = shell("getprop ${AdbParsers.shellQuote(prop)}", echo = false).firstLine()
            success("$prop = ${value.ifBlank { "空" }}")
        }
    },
    action(
        id = "setprop",
        title = "设置系统属性",
        summary = "自动 root 后执行 setprop，并回读校验。",
        fields = listOf(
            textField("prop", "属性名", placeholder = "如 persist.sys.genie.env"),
            textField("value", "属性值", placeholder = "要写入的值"),
        ),
    ) {
        ensureRoot()
        val prop = requireText("prop", "属性名")
        val value = requireText("value", "属性值")
        setpropVerified(prop, value)
    },
)
