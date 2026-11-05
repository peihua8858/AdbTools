package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbParsers
import kotlinx.coroutines.delay

val deviceActions: List<AdbAction> = listOf(
    action(
        id = "device_uuid",
        title = "查询设备 UUID",
        summary = "读取系统属性中所有包含 uuid 的项。",
    ) {
        val matches = shell("getprop", echo = false).lines.filter { it.contains("uuid", ignoreCase = true) }
        if (matches.isEmpty()) {
            warning("未找到 UUID 相关属性")
        } else {
            success("设备 UUID 信息：")
            matches.forEach { output(it.trim()) }
        }
    },
    action(
        id = "device_mac",
        title = "查询 MAC 地址",
        summary = "遍历 /sys/class/net 网卡地址，并补充 dumpsys wifi 中的 WiFi MAC。",
    ) {
        var found = false
        val interfaces = shell("ls /sys/class/net", echo = false)
            .lines
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "lo" }
        interfaces.forEach { name ->
            val mac = shell("cat /sys/class/net/$name/address", echo = false).firstLine()
            if (mac.isNotEmpty() && mac != "00:00:00:00:00:00") {
                output("  $name: $mac")
                found = true
            }
        }
        val wifiMac = AdbParsers.findFirstMacAddress(shell("dumpsys wifi", echo = false).output())
        if (wifiMac != null) {
            output("  WiFi(dumpsys): $wifiMac")
            found = true
        }
        if (!found) {
            warning("未获取到 MAC 地址（设备网卡未启用或系统已随机化 MAC）")
        }
    },
    action(
        id = "device_bt",
        title = "查询蓝牙地址",
        summary = "依次从 secure settings、dumpsys、系统属性和蓝牙配置文件读取蓝牙 MAC。",
    ) {
        var mac: String? = null
        val setting = shell("settings get secure bluetooth_address", echo = false).firstLine()
        if (setting.isNotBlank() && setting != "null") mac = setting
        if (mac == null) {
            mac = AdbParsers.findFirstMacAddress(shell("dumpsys bluetooth_manager", echo = false).output())
        }
        if (mac == null) {
            val props = listOf(
                "ro.boot.btmacaddr",
                "persist.bluetooth.bdaddr",
                "ro.vendor.bt.bdaddr",
                "persist.vendor.bluetooth.bdaddr",
            )
            for (prop in props) {
                val value = shell("getprop $prop", echo = false).firstLine()
                if (value.isNotBlank()) {
                    mac = value
                    break
                }
            }
        }
        if (mac == null) {
            mac = AdbParsers.findFirstMacAddress(
                shell("cat /data/misc/bluetooth/bt_config.conf", echo = false).output(),
            )
        }
        if (mac != null) {
            success("蓝牙 MAC 地址：$mac")
        } else {
            warning("未获取到蓝牙地址（蓝牙可能未开启，或设备限制了该信息）")
        }
    },
    action(
        id = "audio_info",
        title = "查看系统音量",
        summary = "从 dumpsys audio 中提取 STREAM_ 相关音量信息。",
    ) {
        val lines = shell("dumpsys audio", echo = false).lines
        val index = lines.indices.filter { lines[it].contains("STREAM_") }
        if (index.isEmpty()) {
            warning("未找到 STREAM_ 音量信息")
        } else {
            index.forEach { start ->
                val end = (start + 5).coerceAtMost(lines.lastIndex)
                (start..end).forEach { output(lines[it].trimEnd()) }
                output("")
            }
        }
    },
    action(
        id = "device_reboot",
        title = "重启设备",
        summary = "执行 adb reboot，可选择重启到系统、Recovery 或 Bootloader。",
        fields = listOf(
            selectField(
                id = "mode",
                label = "重启模式",
                options = listOf(
                    option("", "系统"),
                    option("recovery", "Recovery"),
                    option("bootloader", "Bootloader"),
                ),
            ),
        ),
    ) {
        val mode = text("mode")
        val target = when (mode) {
            "recovery" -> "Recovery"
            "bootloader" -> "Bootloader"
            else -> "系统"
        }
        info("正在重启设备到 $target ...")
        val args = buildList {
            add("reboot")
            if (mode.isNotBlank()) add(mode)
        }
        adb(*args.toTypedArray(), echo = false)
        warning("设备将断开连接，重启完成后需等待其重新上线")
        delay(1500)
        state.refreshDevices()
    },
    action(
        id = "adb_restart",
        title = "重启 adb 服务",
        summary = "执行 adb kill-server 与 start-server，然后刷新设备列表。",
        requiresDevice = false,
    ) {
        info("正在停止 adb 服务（kill-server）...")
        val stop = adbServer("kill-server")
        if (stop.exitCode != 0) warning("kill-server 返回 exit=${stop.exitCode}")
        delay(500)
        info("正在启动 adb 服务（start-server）...")
        val start = adbServer("start-server")
        if (start.exitCode != 0) {
            error("start-server 失败（exit=${start.exitCode}）")
        } else {
            success("adb 服务已重启")
            state.refreshDevices()
        }
    },
)
