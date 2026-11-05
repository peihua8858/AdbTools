package com.peihua8858.adbtools.adb

data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String? = null,
) {
    val isOnline: Boolean get() = state == "device"
    val isEmulator: Boolean get() = serial.startsWith("emulator-")

    val displayName: String
        get() = buildString {
            append(serial)
            if (!model.isNullOrBlank()) append(" (").append(model).append(")")
            if (!isOnline) append(" [").append(state).append("]")
        }
}

data class ProcessEntry(val pid: String, val name: String)

data class CommandResult(val exitCode: Int, val lines: List<String>) {
    fun output(): String = lines.joinToString("\n")
    fun firstLine(): String = lines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

enum class LogLevel {
    NORMAL,
    COMMAND,
    OUTPUT,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    TITLE,
    DIVIDER,
}

data class LogLine(
    val text: String,
    val level: LogLevel,
    val timestamp: Long = System.currentTimeMillis(),
)

class AdbException(message: String) : Exception(message)

object AdbParsers {
    private val DEVICE_STATES = setOf(
        "device", "offline", "unauthorized", "bootloader", "recovery", "sideload", "connecting",
    )
    private val MAC_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

    fun parseDevices(output: String): List<AdbDevice> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("List of devices") }
            .filterNot { it.startsWith("*") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val state = parts[1]
                if (state !in DEVICE_STATES && !state.equals("no", ignoreCase = true)) return@mapNotNull null
                AdbDevice(serial = parts[0], state = state)
            }
            .toList()
    }

    fun parsePsPidName(output: String): List<ProcessEntry> {
        val result = mutableListOf<ProcessEntry>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@forEach
            if (!parts[0].all(Char::isDigit)) return@forEach
            result += ProcessEntry(pid = parts[0], name = parts[1].trim())
        }
        return result
    }

    fun parsePsLegacy(output: String): List<ProcessEntry> {
        val result = mutableListOf<ProcessEntry>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 3) return@forEach
            val pid = parts[1]
            if (!pid.all(Char::isDigit)) return@forEach
            val name = parts.last()
            if (name.equals("NAME", ignoreCase = true)) return@forEach
            result += ProcessEntry(pid = pid, name = name)
        }
        return result
    }

    fun findMacAddresses(text: String): List<String> = MAC_REGEX.findAll(text).map { it.value }.toList()

    fun findFirstMacAddress(text: String): String? = MAC_REGEX.find(text)?.value

    fun envName(value: String): String = when (value) {
        "0" -> "线上环境"
        "1" -> "预发环境1"
        "2" -> "预发环境2"
        else -> "未知（${value.ifBlank { "空" }}）"
    }

    fun isValidSize(value: String): Boolean = Regex("\\d{2,5}x\\d{2,5}").matches(value.trim())

    fun isValidDensity(value: String): Boolean = Regex("\\d{2,4}").matches(value.trim())

    fun isValidPositiveInt(value: String): Boolean = value.trim().toIntOrNull()?.let { it > 0 } == true

    fun formatCommand(args: List<String>): String = args.joinToString(" ") { arg ->
        if (arg.isBlank() || arg.any { it.isWhitespace() } || arg.contains('"')) {
            "\"" + arg.replace("\"", "\\\"") + "\""
        } else {
            arg
        }
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
