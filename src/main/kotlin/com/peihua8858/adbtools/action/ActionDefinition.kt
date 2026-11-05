package com.peihua8858.adbtools.action

import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers
import com.peihua8858.adbtools.adb.CommandResult
import com.peihua8858.adbtools.adb.LogLevel
import com.peihua8858.adbtools.adb.ProcessEntry
import com.peihua8858.adbtools.ui.AdbToolState
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class FieldKind {
    TEXT,
    CHECKBOX,
    SELECT,
    FILE,
    INFO,
}

data class SelectOption(val value: String, val label: String)

data class ActionField(
    val id: String,
    val label: String,
    val kind: FieldKind,
    val defaultValue: String = "",
    val placeholder: String = "",
    val options: List<SelectOption> = emptyList(),
    val hint: String = "",
    val visibleWhen: (Map<String, String>) -> Boolean = { true },
)

class AdbAction(
    val id: String,
    val title: String,
    val summary: String,
    val fields: List<ActionField> = emptyList(),
    val interruptible: Boolean = false,
    val requiresDevice: Boolean = true,
    val block: suspend ActionContext.() -> Unit,
)

data class ActionCategory(
    val id: String,
    val title: String,
    val actions: List<AdbAction>,
)

fun action(
    id: String,
    title: String,
    summary: String,
    fields: List<ActionField> = emptyList(),
    interruptible: Boolean = false,
    requiresDevice: Boolean = true,
    block: suspend ActionContext.() -> Unit,
): AdbAction = AdbAction(id, title, summary, fields, interruptible, requiresDevice, block)

fun textField(
    id: String,
    label: String,
    defaultValue: String = "",
    placeholder: String = "",
    hint: String = "",
    visibleWhen: (Map<String, String>) -> Boolean = { true },
): ActionField = ActionField(id, label, FieldKind.TEXT, defaultValue, placeholder, hint = hint, visibleWhen = visibleWhen)

fun checkField(
    id: String,
    label: String,
    defaultValue: Boolean = false,
    hint: String = "",
    visibleWhen: (Map<String, String>) -> Boolean = { true },
): ActionField = ActionField(
    id = id,
    label = label,
    kind = FieldKind.CHECKBOX,
    defaultValue = defaultValue.toString(),
    hint = hint,
    visibleWhen = visibleWhen,
)

fun selectField(
    id: String,
    label: String,
    options: List<SelectOption>,
    defaultValue: String = options.first().value,
    hint: String = "",
    visibleWhen: (Map<String, String>) -> Boolean = { true },
): ActionField = ActionField(
    id = id,
    label = label,
    kind = FieldKind.SELECT,
    defaultValue = defaultValue,
    options = options,
    hint = hint,
    visibleWhen = visibleWhen,
)

fun fileField(
    id: String,
    label: String,
    placeholder: String = "",
    hint: String = "",
    visibleWhen: (Map<String, String>) -> Boolean = { true },
): ActionField = ActionField(
    id = id,
    label = label,
    kind = FieldKind.FILE,
    placeholder = placeholder,
    hint = hint,
    visibleWhen = visibleWhen,
)

fun option(value: String, label: String) = SelectOption(value, label)

class ActionContext(
    val project: Project,
    val state: AdbToolState,
    private val values: Map<String, String>,
) {
    val serial: String get() = state.requireSerial()
    val outputDir: File get() = state.outputDirectory()

    fun text(id: String): String = values[id]?.trim().orEmpty()

    fun bool(id: String): Boolean = values[id] == "true"

    fun int(id: String, default: Int): Int = text(id).toIntOrNull() ?: default

    fun requireText(id: String, label: String): String {
        val value = text(id)
        if (value.isEmpty()) throw AdbException("${label}不能为空")
        return value
    }

    fun info(text: String) = state.log(text, LogLevel.INFO)
    fun success(text: String) = state.log(text, LogLevel.SUCCESS)
    fun warning(text: String) = state.log(text, LogLevel.WARNING)
    fun error(text: String) = state.log(text, LogLevel.ERROR)
    fun output(text: String) = state.log(text, LogLevel.OUTPUT)
    fun divider() = state.log("", LogLevel.DIVIDER)

    suspend fun adb(
        vararg args: String,
        echo: Boolean = true,
        showCommand: Boolean = true,
        lineFilter: ((String) -> Boolean)? = null,
    ): CommandResult = state.execute(
        args = args.toList(),
        serial = serial,
        echoOutput = echo,
        showCommand = showCommand,
        lineFilter = lineFilter,
    )

    suspend fun adbServer(
        vararg args: String,
        echo: Boolean = true,
        showCommand: Boolean = true,
    ): CommandResult = state.execute(
        args = args.toList(),
        serial = null,
        echoOutput = echo,
        showCommand = showCommand,
    )

    suspend fun shell(
        command: String,
        echo: Boolean = true,
        lineFilter: ((String) -> Boolean)? = null,
    ): CommandResult = adb("shell", command, echo = echo, lineFilter = lineFilter)

    suspend fun ensureRoot() {
        info("正在检查设备 root 状态...")
        val currentUser = shell("whoami", echo = false).firstLine()
        if (currentUser == "root") {
            success("设备已处于 root 状态")
            return
        }
        warning("设备未处于 root 状态（当前用户：${currentUser.ifBlank { "未知" }}），执行 adb root...")
        adb("root")
        adb("wait-for-device")
        delay(1200)
        val afterRoot = shell("whoami", echo = false).firstLine()
        if (afterRoot != "root") {
            throw AdbException("adb root 失败，设备可能不支持 root（当前用户：${afterRoot.ifBlank { "未知" }}）")
        }
        success("设备已切换至 root 状态")
    }

    suspend fun ensureRemount() {
        info("正在检查 /system 分区写入权限...")
        val testFile = "/system/.adb_tools_test_${System.currentTimeMillis()}"
        val writable = shell("touch $testFile", echo = false).exitCode == 0
        if (!writable) {
            warning("/system 分区不可写入，执行 adb remount...")
            adb("remount")
            delay(1000)
            val retry = shell("touch $testFile", echo = false).exitCode == 0
            if (!retry) {
                throw AdbException(
                    "adb remount 后 /system 仍不可写，请检查设备是否已禁用 verity（dm-verity）。" +
                        "可尝试手动执行 adb disable-verity 后重启设备再试。",
                )
            }
            success("/system 分区已成功 remount 为可写")
        } else {
            success("/system 分区已可写入")
        }
        shell("rm -f $testFile", echo = false)
    }

    suspend fun setpropVerified(key: String, value: String) {
        info("正在设置 $key=$value ...")
        shell("setprop ${AdbParsers.shellQuote(key)} ${AdbParsers.shellQuote(value)}")
        delay(1000)
        val actual = shell("getprop ${AdbParsers.shellQuote(key)}", echo = false).firstLine()
        if (actual != value) {
            throw AdbException("$key 设置验证失败，期望值=$value，实际值=${actual.ifBlank { "空" }}")
        }
        success("$key 已成功设置为 $value（验证通过）")
    }

    suspend fun queryProcesses(keyword: String): List<ProcessEntry> =
        state.queryProcesses(keyword, serial)

    suspend fun pull(remotePath: String, targetDir: File = outputDir): File {
        val result = adb("pull", remotePath, targetDir.absolutePath)
        if (result.exitCode != 0) {
            throw AdbException("pull 失败：${remotePath} -> ${targetDir.absolutePath}")
        }
        val local = File(targetDir, remotePath.substringAfterLast('/'))
        success("已保存到：${local.absolutePath}")
        return local
    }

    suspend fun killPids(pids: List<String>): Boolean {
        var allOk = true
        for (pid in pids) {
            val result = shell("kill $pid", echo = false)
            if (result.exitCode == 0) {
                success("已发送 kill 信号给进程 PID=$pid")
            } else {
                error("kill 进程 PID=$pid 失败")
                allOk = false
            }
        }
        return allOk
    }

    fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    fun remoteTimestamped(prefix: String, extension: String): String =
        "/sdcard/${prefix}_${timestamp()}.$extension"

    fun localLogFile(prefix: String): File = File(outputDir, "${prefix}_${timestamp()}.log")
}
