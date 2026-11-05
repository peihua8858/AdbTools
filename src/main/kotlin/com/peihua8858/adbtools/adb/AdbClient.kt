package com.peihua8858.adbtools.adb

import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

class AdbClient(private val project: Project, private val settings: AdbSettings) {
    @Volatile
    private var cachedAdb: String? = null

    fun invalidate() {
        cachedAdb = null
    }

    fun resolveAdb(): String {
        cachedAdb?.let { return it }
        val failures = mutableListOf<String>()
        for (candidate in candidatePaths()) {
            val path = normalize(candidate) ?: continue
            if (testAdb(path)) {
                cachedAdb = path
                return path
            }
            failures += path
        }
        throw AdbException(
            "未找到可用的 adb 命令。" +
                "请在设置中指定 adb 路径，或配置 ANDROID_HOME / ANDROID_SDK_ROOT。" +
                if (failures.isEmpty()) "" else "（已尝试：${failures.joinToString()}）",
        )
    }

    fun listDevices(): List<AdbDevice> {
        val result = runQuiet(listOf("devices"))
        if (result.exitCode != 0) {
            throw AdbException("adb devices 执行失败：${result.output().ifBlank { "exit=${result.exitCode}" }}")
        }
        return AdbParsers.parseDevices(result.output())
    }

    fun queryModel(serial: String): String? {
        return try {
            runQuiet(listOf("shell", "getprop", "ro.product.model"), serial = serial, timeoutSeconds = 8)
                .firstLine()
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun runQuiet(
        args: List<String>,
        serial: String? = null,
        timeoutSeconds: Long = 20,
    ): CommandResult {
        val command = buildList {
            add(resolveAdb())
            if (serial != null) {
                add("-s")
                add(serial)
            }
            addAll(args)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val watchdog = Thread {
            try {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: InterruptedException) {
                // watchdog cancelled
            }
        }.apply {
            isDaemon = true
            start()
        }
        val lines = mutableListOf<String>()
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    lines += line
                }
            }
            val exit = if (process.waitFor(5, TimeUnit.SECONDS)) process.exitValue() else -1
            return CommandResult(exit, lines)
        } finally {
            watchdog.interrupt()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    fun outputDirectory(): File {
        val configured = settings.state.outputDir.trim()
        val dir = if (configured.isNotEmpty()) {
            File(expandHome(configured))
        } else {
            File(project.basePath ?: System.getProperty("user.home"), "adb-output")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun adbCandidates(): List<String> = candidatePaths().mapNotNull(::normalize)

    private fun candidatePaths(): List<String> {
        val home = System.getProperty("user.home")
        val names = if (isWindows()) listOf("adb.exe") else listOf("adb")
        val explicit = settings.state.adbPath.trim()
        return buildList {
            if (explicit.isNotEmpty()) add(explicit)
            System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let {
                names.forEach { name -> add(File(it, "platform-tools/$name").path) }
            }
            System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let {
                names.forEach { name -> add(File(it, "platform-tools/$name").path) }
            }
            readSdkDirFromLocalProperties()?.let {
                names.forEach { name -> add(File(it, "platform-tools/$name").path) }
            }
            names.forEach { name ->
                add("$home/Library/Android/sdk/platform-tools/$name")
                add("$home/Android/Sdk/platform-tools/$name")
                add("/opt/homebrew/bin/$name")
                add("/usr/local/bin/$name")
                add(name)
            }
        }.distinct()
    }

    private fun normalize(candidate: String): String? {
        val expanded = expandHome(candidate.trim())
        if (expanded.isEmpty()) return null
        val file = File(expanded)
        if (file.isDirectory) {
            val names = if (isWindows()) listOf("adb.exe") else listOf("adb")
            names.forEach { name ->
                File(file, name).takeIf { it.isFile }?.let { return it.absolutePath }
                File(file, "platform-tools/$name").takeIf { it.isFile }?.let { return it.absolutePath }
            }
            return null
        }
        if (file.isFile) return file.absolutePath
        return if (!expanded.contains(File.separator)) expanded else null
    }

    private fun testAdb(path: String): Boolean {
        return try {
            val process = ProcessBuilder(path, "version").redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(6, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun readSdkDirFromLocalProperties(): String? {
        val base = project.basePath ?: return null
        val file = File(base, "local.properties")
        if (!file.isFile) return null
        return try {
            file.readLines()
                .firstOrNull { it.trim().startsWith("sdk.dir=") }
                ?.substringAfter("sdk.dir=")
                ?.trim()
                ?.replace("\\:", ":")
                ?.replace("\\\\", "\\")
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun expandHome(path: String): String {
        val home = System.getProperty("user.home")
        return when {
            path == "~" -> home
            path.startsWith("~/") -> File(home, path.removePrefix("~/")).path
            else -> path
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}
