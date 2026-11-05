package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers
import java.io.File

val appActions: List<AdbAction> = listOf(
    action(
        id = "app_install",
        title = "安装应用",
        summary = "选择本地 APK 安装到当前设备，默认覆盖安装并允许测试包。",
        fields = listOf(
            fileField(
                "apk",
                "APK 文件",
                placeholder = "选择或输入 APK 路径",
                hint = "也可以直接把 .apk 文件拖到输入框",
            ),
            checkField("replace", "覆盖安装（-r）", defaultValue = true),
            checkField("allowTest", "允许测试包（-t）", defaultValue = true),
            checkField("downgrade", "允许降级（-d）"),
        ),
    ) {
        val path = requireText("apk", "APK 文件")
        val file = File(path)
        if (!file.isFile) throw AdbException("APK 文件不存在：$path")
        if (!file.name.endsWith(".apk", ignoreCase = true)) throw AdbException("请选择 .apk 文件")
        val flags = buildList {
            if (bool("replace")) add("-r")
            if (bool("allowTest")) add("-t")
            if (bool("downgrade")) add("-d")
        }
        info("开始安装 ${file.name} ...")
        val result = adb("install", *flags.toTypedArray(), file.absolutePath)
        val message = result.output().trim()
        if (result.exitCode == 0 && message.contains("Success", ignoreCase = true)) {
            success("安装成功：${file.name}")
        } else {
            error("安装失败：${message.ifBlank { "exit=${result.exitCode}" }}")
        }
    },
    action(
        id = "app_uninstall",
        title = "卸载应用",
        summary = "卸载指定包，可选择保留数据（-k）；不保留时应用数据会一并删除。",
        fields = listOf(
            textField("package", "包名", placeholder = "如 com.example.app"),
            checkField("keepData", "保留数据（-k）"),
            checkField("confirm", "我确认要卸载该应用"),
        ),
    ) {
        val packageName = requireText("package", "包名")
        if (!bool("confirm")) throw AdbException("请先勾选确认项")
        val args = buildList {
            add("uninstall")
            if (bool("keepData")) add("-k")
            add(packageName)
        }
        val result = adb(*args.toTypedArray())
        val message = result.output().trim()
        if (message.contains("Success", ignoreCase = true)) {
            success("已卸载 $packageName")
        } else {
            error("卸载失败：${message.ifBlank { "exit=${result.exitCode}" }}")
        }
    },
    action(
        id = "app_version",
        title = "查看 app 版本号",
        summary = "读取指定包名的 versionCode / versionName。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        val result = shell("dumpsys package ${AdbParsers.shellQuote(packageName)}", echo = false)
        val lines = result.lines.filter { it.contains("versionCode") || it.contains("versionName") }
        if (lines.isEmpty()) {
            error("未找到包 $packageName 的版本信息")
        } else {
            success("包 $packageName 版本信息：")
            lines.forEach { output(it.trim()) }
        }
    },
    action(
        id = "app_launch",
        title = "启动 app",
        summary = "通过 monkey 启动指定包的 Launcher Activity。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        val result = shell("monkey -p ${AdbParsers.shellQuote(packageName)} -c android.intent.category.LAUNCHER 1")
        if (result.output().contains("No activities found")) {
            warning("未找到 $packageName 的 Launcher Activity，启动失败")
        } else if (result.exitCode == 0) {
            success("已尝试启动 $packageName")
        } else {
            error("启动 $packageName 失败（exit=${result.exitCode}）")
        }
    },
    action(
        id = "app_start_activity",
        title = "打开指定 Activity",
        summary = "执行 am start -n <component>。",
        fields = listOf(
            textField(
                id = "component",
                label = "Activity component",
                placeholder = "如 com.example.app/.MainActivity",
            ),
        ),
    ) {
        val component = requireText("component", "Activity component")
        val result = shell("am start -n ${AdbParsers.shellQuote(component)}")
        if (result.exitCode == 0) success("Activity 已打开：$component") else error("打开 Activity 失败")
    },
    action(
        id = "app_boot_broadcast",
        title = "发送开机广播",
        summary = "广播 android.intent.action.BOOT_COMPLETED。",
    ) {
        shell("am broadcast -a android.intent.action.BOOT_COMPLETED")
        success("开机广播已发送")
    },
    action(
        id = "app_start_service",
        title = "启动 Service",
        summary = "执行 am startservice -n <component>。",
        fields = listOf(
            textField(
                id = "component",
                label = "Service component",
                placeholder = "如 com.example.app/.MyService",
            ),
        ),
    ) {
        val component = requireText("component", "Service component")
        val result = shell("am startservice -n ${AdbParsers.shellQuote(component)}")
        if (result.exitCode == 0) success("Service 已启动：$component") else error("启动 Service 失败")
    },
    action(
        id = "app_clear_data",
        title = "清除应用数据（缓存+用户数据）",
        summary = "执行 pm clear，同时清除该应用的缓存和用户数据，操作不可恢复。",
        fields = listOf(
            textField("package", "包名", placeholder = "如 com.example.app"),
            checkField("confirm", "我确认要清除该应用的全部数据"),
        ),
    ) {
        val packageName = requireText("package", "包名")
        if (!bool("confirm")) throw AdbException("请先勾选确认项")
        val result = shell("pm clear ${AdbParsers.shellQuote(packageName)}")
        val message = result.output().trim()
        if (message.contains("Success", ignoreCase = true) || result.exitCode == 0) {
            success("已清除 $packageName 的缓存与用户数据")
        } else {
            error("清除 $packageName 数据失败：${message.ifBlank { "exit=${result.exitCode}" }}")
        }
    },
    action(
        id = "app_path",
        title = "查看安装路径",
        summary = "执行 pm path 查看 APK 安装位置。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        val result = shell("pm path ${AdbParsers.shellQuote(packageName)}", echo = false)
        if (result.output().isBlank()) {
            error("未找到包 $packageName")
        } else {
            success("$packageName 安装路径：")
            result.lines.filter { it.isNotBlank() }.forEach { output(it.trim()) }
        }
    },
    action(
        id = "top_activity",
        title = "查看顶层 Activity",
        summary = "从 dumpsys activity activities 中提取 ResumedActivity。",
    ) {
        val result = shell("dumpsys activity activities", echo = false)
        val matches = result.lines.filter {
            it.contains("ResumedActivity") || it.contains("mResumedActivity")
        }
        if (matches.isEmpty()) {
            warning("未找到 ResumedActivity 信息")
        } else {
            success("当前顶层 Activity：")
            matches.forEach { output(it.trim()) }
        }
    },
    action(
        id = "runtime",
        title = "查看应用运行时长",
        summary = "按关键字匹配进程，通过 ps 的 ETIME 字段查看各进程已运行时长。",
        fields = listOf(
            textField("keyword", "包名关键字", placeholder = "如 genie"),
        ),
    ) {
        val keyword = requireText("keyword", "关键字")
        val result = shell("ps -A -o CMD,ETIME", echo = false)
        val matches = result.lines.filter { it.contains(keyword) }
        if (matches.isEmpty()) {
            warning("未找到匹配 '$keyword' 的进程")
        } else {
            success("运行时长信息：")
            matches.forEach { output(it.trim()) }
        }
    },
    action(
        id = "process_list",
        title = "查看进程",
        summary = "按关键字过滤当前设备进程（关键字留空显示全部）。",
        fields = listOf(textField("keyword", "查询关键字", placeholder = "留空显示全部")),
    ) {
        val keyword = text("keyword")
        val processes = queryProcesses(keyword)
        if (processes.isEmpty()) {
            warning("未找到匹配 '${keyword.ifBlank { "全部" }}' 的进程")
        } else {
            success("匹配进程列表：")
            processes.forEach { output("  PID=${it.pid}  ${it.name}") }
        }
    },
    action(
        id = "process_kill_pid",
        title = "Kill 进程（PID）",
        summary = "向指定 PID 发送 kill 信号。",
        fields = listOf(textField("pid", "进程 PID", placeholder = "如 12345")),
    ) {
        val pid = requireText("pid", "PID")
        if (!pid.all(Char::isDigit)) throw AdbException("PID 必须是数字")
        val result = shell("kill $pid")
        if (result.exitCode == 0) success("已发送 kill 信号给 PID=$pid") else error("kill 失败")
    },
    action(
        id = "process_force_stop",
        title = "强制停止 app",
        summary = "执行 am force-stop 停止指定包。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        val result = shell("am force-stop ${AdbParsers.shellQuote(packageName)}")
        if (result.exitCode == 0) success("已强制停止 $packageName") else error("强制停止失败")
    },
    action(
        id = "process_kill_by_package",
        title = "按包名 Kill 进程",
        summary = "输入包名查询匹配进程，勾选后批量 kill，并校验进程状态。",
    ) {
        warning("请使用下方进程选择面板查询并选择要 kill 的进程")
    },
    action(
        id = "find_package",
        title = "查找包名",
        summary = "在 dumpsys package packages 中按关键字搜索已安装包。",
        fields = listOf(textField("keyword", "包名关键字", placeholder = "如 genie")),
    ) {
        val keyword = requireText("keyword", "关键字")
        val result = shell("dumpsys package packages", echo = false)
        val matches = result.lines.filter { it.contains("Package [") && it.contains(keyword) }
        if (matches.isEmpty()) {
            warning("未找到匹配 '$keyword' 的包名")
        } else {
            success("匹配的包名列表：")
            matches.forEach { output(it.trim()) }
        }
    },
    action(
        id = "monkey",
        title = "Monkey 测试",
        summary = "以固定 seed + throttle 运行 monkey 压力测试，自动忽略 crash/timeout。",
        fields = listOf(
            textField("package", "目标包名", placeholder = "如 com.example.app"),
            textField("events", "事件数", defaultValue = "10000"),
            textField("throttle", "事件间隔（毫秒）", defaultValue = "500"),
            textField("seed", "seed 值", defaultValue = "12345"),
        ),
        interruptible = true,
    ) {
        val packageName = requireText("package", "包名")
        val events = text("events").ifBlank { "10000" }
        val throttle = text("throttle").ifBlank { "500" }
        val seed = text("seed").ifBlank { "12345" }
        if (!AdbParsers.isValidPositiveInt(events)) throw AdbException("事件数必须是正整数")
        if (!AdbParsers.isValidPositiveInt(throttle)) throw AdbException("事件间隔必须是正整数")
        if (!seed.all(Char::isDigit)) throw AdbException("seed 必须是数字")
        info("开始 Monkey 测试：包=$packageName, 事件数=$events, 间隔=${throttle}ms, seed=$seed")
        val result = shell(
            "monkey -p ${AdbParsers.shellQuote(packageName)} -s $seed --throttle $throttle " +
                "--ignore-crashes --ignore-timeouts -v $events",
        )
        if (result.exitCode == 0) success("Monkey 测试已完成") else error("Monkey 测试结束（exit=${result.exitCode}）")
    },
)
