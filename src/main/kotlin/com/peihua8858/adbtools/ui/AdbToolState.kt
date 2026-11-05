package com.peihua8858.adbtools.ui

import com.peihua8858.adbtools.action.ActionContext
import com.peihua8858.adbtools.action.ActionField
import com.peihua8858.adbtools.action.AdbAction
import com.peihua8858.adbtools.action.FieldKind
import com.peihua8858.adbtools.adb.AdbClient
import com.peihua8858.adbtools.adb.AdbDevice
import com.peihua8858.adbtools.adb.AdbException
import com.peihua8858.adbtools.adb.AdbParsers
import com.peihua8858.adbtools.adb.AdbSettings
import com.peihua8858.adbtools.adb.CommandResult
import com.peihua8858.adbtools.adb.LogLevel
import com.peihua8858.adbtools.adb.LogLine
import com.peihua8858.adbtools.adb.ProcessEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AdbToolState(
    val project: Project,
    val settings: AdbSettings,
) {
    private val client = AdbClient(project, settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val deviceList = mutableListOf<AdbDevice>()
    private val outputLines = mutableListOf<LogLine>()
    private val history = mutableMapOf<String, List<String>>().apply {
        settings.state.fieldHistory.forEach { (fieldId, raw) ->
            val items = raw.split('\n').filter { it.isNotBlank() }
            if (items.isNotEmpty()) put(fieldId, items)
        }
    }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val notifyQueued = AtomicBoolean(false)

    @Volatile
    var selectedSerial: String? = settings.state.lastSerial.ifBlank { null }
        private set

    @Volatile
    var adbPath: String? = null
        private set

    @Volatile
    var adbError: String? = null
        private set

    @Volatile
    var busy: Boolean = false
        private set

    @Volatile
    var runningTitle: String? = null
        private set

    private var activeJob: Job? = null

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var disposed = false

    init {
        refreshDevices()
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun devices(): List<AdbDevice> = synchronized(deviceList) { deviceList.toList() }

    fun outputSnapshot(): List<LogLine> = synchronized(outputLines) { outputLines.toList() }

    fun fieldHistory(fieldId: String): List<String> = history[fieldId].orEmpty()

    fun selectedDevice(): AdbDevice? = devices().firstOrNull { it.serial == selectedSerial }

    fun outputDirectory(): File = client.outputDirectory()

    fun selectDevice(serial: String) {
        selectedSerial = serial
        settings.state.lastSerial = serial
        notifyChanged()
    }

    fun rememberSelection(categoryId: String, actionId: String) {
        settings.state.lastCategoryId = categoryId
        settings.state.lastActionId = actionId
    }

    fun toggleFavorite(actionId: String) {
        val favorites = settings.state.favoriteActionIds
        if (!favorites.remove(actionId)) favorites.add(actionId)
        notifyChanged()
    }

    fun favoriteIds(): Set<String> = settings.state.favoriteActionIds.toSet()

    fun refreshDevices() {
        scope.launch {
            try {
                val adb = client.resolveAdb()
                adbPath = adb
                adbError = null
                val raw = client.listDevices()
                val withModels = raw.map { device ->
                    if (device.isOnline) device.copy(model = client.queryModel(device.serial)) else device
                }
                synchronized(deviceList) {
                    deviceList.clear()
                    deviceList.addAll(withModels)
                }
                val current = selectedSerial
                val stillOnline = withModels.any { it.serial == current && it.isOnline }
                if (!stillOnline) {
                    selectedSerial = withModels.firstOrNull { it.isOnline }?.serial
                }
                settings.state.lastSerial = selectedSerial.orEmpty()
                if (withModels.isEmpty()) {
                    log("adb 已就绪，但未检测到已连接设备", LogLevel.WARNING)
                }
                notifyChanged()
            } catch (t: Throwable) {
                adbPath = null
                adbError = t.message ?: t.javaClass.simpleName
                synchronized(deviceList) { deviceList.clear() }
                log(t.message ?: "ADB 初始化失败", LogLevel.ERROR)
                notifyChanged()
            }
        }
    }

    fun onSettingsChanged() {
        client.invalidate()
        refreshDevices()
    }

    fun requireSerial(): String {
        val serial = selectedSerial ?: throw AdbException("请先连接设备，并在顶部选择一台设备")
        val device = devices().firstOrNull { it.serial == serial }
        if (device != null && !device.isOnline) {
            throw AdbException("设备 $serial 当前状态为 ${device.state}，无法执行命令")
        }
        return serial
    }

    fun defaultFor(field: ActionField): String = when (field.id) {
        "package" -> settings.state.lastPackage.ifBlank { field.defaultValue }
        "debugKey" -> settings.state.lastDebugKey.ifBlank { field.defaultValue }
        "uri", "deeplink" -> settings.state.lastDeeplink.ifBlank { field.defaultValue }
        "keyword" -> settings.state.lastKeyword.ifBlank { field.defaultValue }
        else -> field.defaultValue
    }

    fun run(action: AdbAction, values: Map<String, String>) {
        if (action.requiresDevice) {
            try {
                requireSerial()
            } catch (e: AdbException) {
                log(e.message.orEmpty(), LogLevel.ERROR)
                return
            }
        }
        persistHints(values)
        recordFieldHistory(action, values)
        runTask(action.title, values) { action.block(this) }
    }

    fun runTask(
        title: String,
        values: Map<String, String> = emptyMap(),
        block: suspend ActionContext.() -> Unit,
    ) {
        if (busy) {
            log("已有任务正在执行，请先等待或点击停止", LogLevel.WARNING)
            return
        }
        busy = true
        runningTitle = title
        log("", LogLevel.DIVIDER)
        log("开始执行：$title", LogLevel.TITLE)
        notifyChanged()
        val start = System.currentTimeMillis()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                ActionContext(project, this@AdbToolState, values).block()
                log("完成：$title（${elapsed(start)}）", LogLevel.SUCCESS)
            } catch (_: CancellationException) {
                log("任务已停止：$title", LogLevel.WARNING)
            } catch (t: Throwable) {
                log(t.message ?: t.javaClass.simpleName, LogLevel.ERROR)
            } finally {
                busy = false
                runningTitle = null
                activeJob = null
                notifyChanged()
            }
        }
        activeJob = job
        job.start()
    }

    fun stop() {
        activeProcess?.destroy()
        activeJob?.cancel()
        log("已请求停止当前任务", LogLevel.WARNING)
    }

    suspend fun execute(
        args: List<String>,
        serial: String? = selectedSerial,
        echoOutput: Boolean = true,
        showCommand: Boolean = true,
        lineFilter: ((String) -> Boolean)? = null,
    ): CommandResult = withContext(Dispatchers.IO) {
        val adb = try {
            client.resolveAdb()
        } catch (t: Throwable) {
            adbError = t.message
            notifyChanged()
            throw t
        }
        adbPath = adb
        adbError = null
        val command = buildList {
            add(adb)
            if (serial != null) {
                add("-s")
                add(serial)
            }
            addAll(args)
        }
        if (showCommand) log(AdbParsers.formatCommand(command), LogLevel.COMMAND)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        activeProcess = process
        val lines = mutableListOf<String>()
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    lines += line
                    if (echoOutput && (lineFilter == null || lineFilter(line))) {
                        log(line, LogLevel.OUTPUT)
                    }
                }
            }
            val exit = process.waitFor(10, TimeUnit.SECONDS)
            CommandResult(if (exit) process.exitValue() else -1, lines)
        } finally {
            activeProcess = null
            if (process.isAlive) process.destroyForcibly()
        }
    }

    suspend fun queryProcesses(keyword: String, serial: String = requireSerial()): List<ProcessEntry> {
        val modern = execute(
            args = listOf("shell", "ps -A -o PID,NAME"),
            serial = serial,
            echoOutput = false,
            showCommand = false,
        )
        var entries = AdbParsers.parsePsPidName(modern.output())
        if (entries.isEmpty()) {
            val legacy = execute(
                args = listOf("shell", "ps"),
                serial = serial,
                echoOutput = false,
                showCommand = false,
            )
            entries = AdbParsers.parsePsLegacy(legacy.output())
        }
        return if (keyword.isBlank()) entries else entries.filter { it.name.contains(keyword) }
    }

    fun log(text: String, level: LogLevel = LogLevel.NORMAL) {
        if (disposed) return
        val chunks = if (text.isEmpty()) listOf("") else text.split('\n')
        synchronized(outputLines) {
            if (outputLines.size + chunks.size > MAX_OUTPUT_LINES) {
                val removeCount = outputLines.size + chunks.size - MAX_OUTPUT_LINES + 500
                repeat(removeCount.coerceAtMost(outputLines.size)) { outputLines.removeAt(0) }
            }
            chunks.forEach { chunk -> outputLines.add(LogLine(chunk.trimEnd(), level)) }
        }
        notifyChanged()
    }

    fun clearOutput() {
        synchronized(outputLines) { outputLines.clear() }
        notifyChanged()
    }

    fun outputText(): String = outputSnapshot().joinToString("\n") { it.text }

    fun copyOutput() {
        CopyPasteManager.getInstance().setContents(StringSelection(outputText()))
    }

    fun copyLastCommand() {
        val command = outputSnapshot().lastOrNull { it.level == LogLevel.COMMAND }?.text
        if (command.isNullOrBlank()) {
            log("暂无可复制的命令，请先执行一次功能", LogLevel.WARNING)
            return
        }
        CopyPasteManager.getInstance().setContents(StringSelection(command))
        log("已复制命令：$command", LogLevel.INFO)
    }

    fun saveOutput(): File? {
        return try {
            val file = File(outputDirectory(), "adb-console-${timestamp()}.log")
            file.writeText(outputText())
            log("已保存控制台日志：${file.absolutePath}", LogLevel.SUCCESS)
            file
        } catch (t: Throwable) {
            log("保存日志失败：${t.message}", LogLevel.ERROR)
            null
        }
    }

    fun openOutputDirectory() {
        val dir = outputDirectory()
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir)
            } else {
                log("当前环境不支持打开目录：${dir.absolutePath}", LogLevel.WARNING)
            }
        } catch (t: Throwable) {
            log("打开目录失败：${t.message}（${dir.absolutePath}）", LogLevel.ERROR)
        }
    }

    fun dispose() {
        disposed = true
        activeProcess?.destroy()
        scope.cancel()
        listeners.clear()
    }

    private fun notifyChanged() {
        if (disposed) return
        if (!notifyQueued.compareAndSet(false, true)) return
        val app = ApplicationManager.getApplication()
        if (app == null) {
            notifyQueued.set(false)
            return
        }
        app.invokeLater {
            notifyQueued.set(false)
            if (!disposed) listeners.forEach { it() }
        }
    }

    private fun persistHints(values: Map<String, String>) {
        values["package"]?.trim()?.takeIf { it.isNotEmpty() }?.let { settings.state.lastPackage = it }
        values["debugKey"]?.trim()?.takeIf { it.isNotEmpty() }?.let { settings.state.lastDebugKey = it }
        values["uri"]?.trim()?.takeIf { it.isNotEmpty() }?.let { settings.state.lastDeeplink = it }
        values["deeplink"]?.trim()?.takeIf { it.isNotEmpty() }?.let { settings.state.lastDeeplink = it }
        values["keyword"]?.trim()?.takeIf { it.isNotEmpty() }?.let { settings.state.lastKeyword = it }
        selectedSerial?.let { settings.state.lastSerial = it }
    }

    private fun recordFieldHistory(action: AdbAction, values: Map<String, String>) {
        action.fields
            .filter { it.kind == FieldKind.TEXT && it.visibleWhen(values) }
            .forEach { field ->
                val value = values[field.id]?.trim().orEmpty()
                if (value.isEmpty() || value == field.defaultValue) return@forEach
                val updated = (listOf(value) + history[field.id].orEmpty().filter { it != value })
                    .take(MAX_FIELD_HISTORY)
                history[field.id] = updated
                settings.state.fieldHistory[field.id] = updated.joinToString("\n")
            }
    }

    private fun elapsed(start: Long): String {
        val seconds = (System.currentTimeMillis() - start) / 1000.0
        return String.format("%.1fs", seconds)
    }

    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    companion object {
        private const val MAX_OUTPUT_LINES = 6000
        private const val MAX_FIELD_HISTORY = 6
    }
}
