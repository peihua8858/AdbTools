package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers
import kotlinx.coroutines.delay

val perfActions: List<AdbAction> = listOf(
    action(
        id = "memory_gc",
        title = "GC 内存回收查看",
        summary = "向目标进程发送 SIGUSR1 触发 GC，然后输出 meminfo。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        val pids = shell("pidof ${AdbParsers.shellQuote(packageName)}", echo = false)
            .firstLine()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (pids.isEmpty()) throw AdbException("未找到包 $packageName 的进程")
        info("发送 GC 信号...")
        pids.forEach { pid ->
            shell("kill -10 $pid", echo = false)
            success("已向 PID=$pid 发送 GC 信号")
        }
        delay(2000)
        info("查看内存信息...")
        shell("dumpsys meminfo ${AdbParsers.shellQuote(packageName)}")
    },
    action(
        id = "memory_low",
        title = "模拟低内存",
        summary = "自动 root 后写入 /proc/sys/vm/drop_caches 触发内存回收。",
    ) {
        ensureRoot()
        info("模拟低内存环境...")
        shell("echo 1 > /proc/sys/vm/drop_caches")
        success("已触发内存回收")
    },
    action(
        id = "memory_info",
        title = "查看 app 内存信息",
        summary = "执行 dumpsys meminfo <package>。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        shell("dumpsys meminfo ${AdbParsers.shellQuote(packageName)}")
    },
    action(
        id = "memory_dumpheap",
        title = "获取 dumpheap",
        summary = "将堆快照 dump 到 /data/local/tmp 并拉取到本地输出目录。",
        fields = listOf(textField("package", "包名", placeholder = "如 com.example.app")),
    ) {
        val packageName = requireText("package", "包名")
        val remote = "/data/local/tmp/${packageName}_heap_${timestamp()}.hprof"
        info("正在获取 dumpheap...")
        val result = shell("am dumpheap ${AdbParsers.shellQuote(packageName)} $remote")
        if (result.exitCode != 0) throw AdbException("dumpheap 失败（exit=${result.exitCode}）")
        delay(3000)
        pull(remote)
    },
)
