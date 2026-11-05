package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers
import kotlinx.coroutines.delay

val envActions: List<AdbAction> = listOf(
    action(
        id = "switch_env",
        title = "切换网络环境",
        summary = "设置 persist.sys.genie.env（0 线上 / 1 预发1 / 2 预发2），自动执行 adb root 与 remount。",
        fields = listOf(
            selectField(
                id = "env",
                label = "目标环境",
                options = listOf(
                    option("0", "线上环境"),
                    option("1", "预发环境1"),
                    option("2", "预发环境2"),
                ),
            ),
            checkField("reboot", "设置完成后重启设备"),
        ),
    ) {
        ensureRoot()
        ensureRemount()
        val key = "persist.sys.genie.env"
        val current = shell("getprop $key", echo = false).firstLine()
        info("当前网络环境：${current.ifBlank { "未设置" }}（${AdbParsers.envName(current)}）")
        val env = requireText("env", "环境编号")
        setpropVerified(key, env)
        success("环境切换成功：$key = $env（${AdbParsers.envName(env)}）")
        if (bool("reboot")) {
            info("正在执行 adb reboot...")
            adb("reboot")
            success("重启命令已发送，请等待设备重新启动")
        } else {
            warning("已跳过重启，部分功能可能需要重启后才能生效")
        }
    },
    action(
        id = "query_env",
        title = "查询网络环境",
        summary = "读取 persist.sys.genie.env 的当前值。",
    ) {
        val key = "persist.sys.genie.env"
        val value = shell("getprop $key", echo = false).firstLine()
        if (value.isBlank()) {
            warning("未获取到环境属性值（$key 可能未设置）")
        } else {
            success("当前网络环境：$key = $value（${AdbParsers.envName(value)}）")
        }
    },
    action(
        id = "ut_realtime",
        title = "设置埋点UT实时上报",
        summary = "开启 persist.ut.realtime.enable、写入 ut.realtime.debugkey，并重启匹配进程验证。",
        fields = listOf(
            textField("debugKey", "debugkey", placeholder = "请输入 debugkey 值"),
            textField("package", "进程包名", placeholder = "如 com.alibaba.ailabs.genie.smartapp"),
            checkField("restart", "设置完成后重启匹配进程", defaultValue = true),
        ),
    ) {
        ensureRoot()
        ensureRemount()
        setpropVerified("persist.ut.realtime.enable", "1")
        val debugKey = requireText("debugKey", "debugkey")
        setpropVerified("ut.realtime.debugkey", debugKey)
        if (!bool("restart")) {
            warning("已跳过进程重启，UT 实时上报可能需要重启进程后生效")
            return@action
        }
        val packageName = requireText("package", "进程包名")
        val oldProcesses = queryProcesses(packageName)
        if (oldProcesses.isEmpty()) {
            warning("未找到与 '$packageName' 匹配的进程，跳过重启")
            return@action
        }
        info("找到 ${oldProcesses.size} 个匹配进程：")
        oldProcesses.forEach { output("  PID=${it.pid}  ${it.name}") }
        val oldPids = oldProcesses.map { it.pid }
        if (!killPids(oldPids)) {
            throw AdbException("部分进程 kill 失败，请检查后重试")
        }
        info("等待进程重新启动...")
        delay(3000)
        var newProcesses = queryProcesses(packageName)
        if (newProcesses.isEmpty()) {
            info("进程尚未出现，继续等待...")
            delay(3000)
            newProcesses = queryProcesses(packageName)
        }
        if (newProcesses.isEmpty()) {
            throw AdbException("进程 '$packageName' 未能重新启动，请手动检查")
        }
        val newPids = newProcesses.map { it.pid }
        val stillAlive = oldPids.filter { it in newPids }
        if (stillAlive.isNotEmpty()) {
            error("以下 PID 未改变，可能未成功重启：${stillAlive.joinToString()}")
            return@action
        }
        success("埋点UT实时上报配置完成")
        success("- persist.ut.realtime.enable = 1")
        success("- ut.realtime.debugkey = $debugKey")
        success("- 进程 $packageName 已重启")
        success("  旧 PID: ${oldPids.joinToString()}")
        success("  新 PID: ${newPids.joinToString()}")
    },
)
