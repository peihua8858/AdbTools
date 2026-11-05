package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers
import kotlinx.coroutines.delay

val miscActions: List<AdbAction> = listOf(
    action(
        id = "voice_command",
        title = "发送语音命令",
        summary = "通过 waft deeplink 向设备发送一条语音文本。",
        fields = listOf(textField("voice_text", "语音文本", placeholder = "请输入要发送的文本")),
    ) {
        val voiceText = requireText("voice_text", "语音文本")
        val uri = "waft://com.alibaba.genie.waft.agcs/test?input=$voiceText"
        val result = adb("shell", "am", "start", "-d", uri)
        if (result.exitCode == 0) success("语音命令已发送：$voiceText") else error("语音命令发送失败")
    },
    action(
        id = "deeplink",
        title = "打开 Deeplink",
        summary = "执行 am start -W -a VIEW -d <uri>。",
        fields = listOf(textField("uri", "Deeplink URI", placeholder = "如 genie://com.android.settings/bt?open=true")),
    ) {
        val uri = requireText("uri", "Deeplink URI")
        val result = adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", uri)
        if (result.exitCode == 0) success("Deeplink 已打开：$uri") else error("打开 Deeplink 失败")
    },
    action(
        id = "screen_capture",
        title = "录屏 / 截屏",
        summary = "截图或录屏，并保存到本地输出目录。",
        fields = listOf(
            selectField(
                id = "capture_mode",
                label = "操作",
                options = listOf(
                    option("screenshot", "截屏并保存到本地"),
                    option("record", "录屏"),
                ),
            ),
            textField(
                id = "duration",
                label = "录屏时长（秒）",
                defaultValue = "30",
                visibleWhen = { it["capture_mode"] == "record" },
            ),
            textField(
                id = "size",
                label = "分辨率",
                placeholder = "如 1280x720，留空使用默认",
                visibleWhen = { it["capture_mode"] == "record" },
            ),
            textField(
                id = "bitrate",
                label = "码率",
                placeholder = "如 4000000，留空使用默认",
                visibleWhen = { it["capture_mode"] == "record" },
            ),
            checkField(
                id = "pull",
                label = "完成后拉取到本地",
                defaultValue = true,
                visibleWhen = { it["capture_mode"] == "record" },
            ),
        ),
        interruptible = true,
    ) {
        when (text("capture_mode")) {
            "screenshot" -> {
                val remote = remoteTimestamped("screenshot", "png")
                info("正在截屏...")
                val result = shell("screencap -p $remote")
                if (result.exitCode != 0) throw AdbException("截屏失败")
                pull(remote)
            }
            "record" -> {
                val duration = text("duration").ifBlank { "30" }
                if (!AdbParsers.isValidPositiveInt(duration)) throw AdbException("录屏时长必须是正整数")
                val size = text("size")
                if (size.isNotEmpty() && !AdbParsers.isValidSize(size)) {
                    throw AdbException("分辨率格式不正确，应如 1280x720")
                }
                val bitrate = text("bitrate")
                if (bitrate.isNotEmpty() && !bitrate.all(Char::isDigit)) {
                    throw AdbException("码率必须是数字，如 4000000")
                }
                val remote = remoteTimestamped("screenrecord", "mp4")
                val command = buildString {
                    append("screenrecord --time-limit $duration")
                    if (size.isNotEmpty()) append(" --size $size")
                    if (bitrate.isNotEmpty()) append(" --bit-rate $bitrate")
                    append(" $remote")
                }
                warning("开始录屏（${duration}秒）... 可点击“停止”提前结束")
                val result = shell(command)
                if (result.exitCode != 0) throw AdbException("录屏失败（exit=${result.exitCode}）")
                success("录屏完成：$remote")
                if (bool("pull")) pull(remote)
            }
            else -> throw AdbException("无效的录屏/截屏操作")
        }
    },
    action(
        id = "exit_factory",
        title = "退出工厂模式",
        summary = "自动 root 后调用 SmartAppService.exitFactory，可选择性重启设备。",
        fields = listOf(checkField("reboot", "执行后重启设备")),
    ) {
        ensureRoot()
        info("正在执行退出工厂模式...")
        val result = shell(
            "dumpsys activity service com.alibaba.ailabs.genie.smartapp/.service.SmartAppService exitFactory",
        )
        if (result.exitCode != 0) warning("命令退出码为 ${result.exitCode}")
        delay(2000)
        if (bool("reboot")) {
            adb("reboot")
            success("重启命令已发送")
        } else {
            success("退出工厂模式命令已执行")
        }
    },
    action(
        id = "close_emulator",
        title = "关闭模拟器",
        summary = "检测所有 emulator-* 设备并逐个执行 adb emu kill。",
        requiresDevice = false,
    ) {
        val result = state.execute(
            args = listOf("devices"),
            serial = null,
            echoOutput = false,
            showCommand = false,
        )
        val emulators = AdbParsers.parseDevices(result.output()).filter { it.isEmulator }
        if (emulators.isEmpty()) {
            warning("未检测到运行中的模拟器")
            return@action
        }
        emulators.forEach { emulator ->
            info("正在关闭模拟器：${emulator.serial} ...")
            val killResult = state.execute(
                args = listOf("emu", "kill"),
                serial = emulator.serial,
                echoOutput = true,
            )
            if (killResult.exitCode == 0) {
                success("模拟器 ${emulator.serial} 已关闭")
            } else {
                error("关闭模拟器 ${emulator.serial} 失败")
            }
        }
        delay(500)
        state.refreshDevices()
    },
)
