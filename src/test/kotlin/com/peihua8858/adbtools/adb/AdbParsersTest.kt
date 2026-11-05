package com.peihua8858.adbtools.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbParsersTest {
    @Test
    fun `parseDevices skips headers and daemon messages`() {
        val output = """
            * daemon not running; starting now at tcp:5037
            * daemon started successfully
            List of devices attached
            emulator-5554	device
            192.168.1.8:5555	device product:demo model:Demo_Device device:demo
            0123456789	unauthorized usb:1-1
            deadbeef	offline
        """.trimIndent()

        val devices = AdbParsers.parseDevices(output)

        assertEquals(4, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
        assertTrue(devices[0].isOnline)
        assertTrue(devices[0].isEmulator)
        assertEquals("device", devices[1].state)
        assertEquals("unauthorized", devices[2].state)
        assertFalse(devices[2].isOnline)
        assertEquals("offline", devices[3].state)
    }

    @Test
    fun `parsePsPidName reads modern ps output`() {
        val output = """
            PID NAME
            1234 com.example.app
            5678 com.example.app:remote
            not-a-pid broken
        """.trimIndent()

        val processes = AdbParsers.parsePsPidName(output)

        assertEquals(2, processes.size)
        assertEquals(ProcessEntry("1234", "com.example.app"), processes[0])
        assertEquals(ProcessEntry("5678", "com.example.app:remote"), processes[1])
    }

    @Test
    fun `parsePsLegacy reads last column as process name`() {
        val output = """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            root 1234 1 1000000 20000 SyS_epoll_0 0 S com.example.app
            u0_a10 4321 1 900000 10000 SyS_epoll_0 0 S com.example.app:push
        """.trimIndent()

        val processes = AdbParsers.parsePsLegacy(output)

        assertEquals(2, processes.size)
        assertEquals(ProcessEntry("1234", "com.example.app"), processes[0])
        assertEquals(ProcessEntry("4321", "com.example.app:push"), processes[1])
    }

    @Test
    fun `findFirstMacAddress only accepts mac format`() {
        val text = "current MAC address: 12:34:56:AB:CD:EF, no other value"

        assertEquals("12:34:56:AB:CD:EF", AdbParsers.findFirstMacAddress(text))
        assertEquals(emptyList<String>(), AdbParsers.findMacAddresses("no mac here"))
    }

    @Test
    fun `envName maps known values`() {
        assertEquals("线上环境", AdbParsers.envName("0"))
        assertEquals("预发环境1", AdbParsers.envName("1"))
        assertEquals("预发环境2", AdbParsers.envName("2"))
        assertTrue(AdbParsers.envName("9").contains("未知"))
    }

    @Test
    fun `validators reject malformed values`() {
        assertTrue(AdbParsers.isValidSize("1920x1080"))
        assertFalse(AdbParsers.isValidSize("1920*1080"))
        assertTrue(AdbParsers.isValidDensity("320"))
        assertFalse(AdbParsers.isValidDensity("320dpi"))
        assertTrue(AdbParsers.isValidPositiveInt("10000"))
        assertFalse(AdbParsers.isValidPositiveInt("0"))
    }

    @Test
    fun `formatCommand quotes arguments containing spaces`() {
        val command = AdbParsers.formatCommand(listOf("adb", "shell", "getprop", "ro.product.model"))
        assertEquals("adb shell getprop ro.product.model", command)

        val quoted = AdbParsers.formatCommand(listOf("adb", "shell", "am start -d x"))
        assertEquals("adb shell \"am start -d x\"", quoted)
    }
}
