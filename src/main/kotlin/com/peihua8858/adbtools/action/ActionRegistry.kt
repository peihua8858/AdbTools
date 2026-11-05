package com.peihua8858.adbtools.action

object ActionRegistry {
    val categories: List<ActionCategory> = listOf(
        ActionCategory("env", "网络与环境", envActions),
        ActionCategory("device", "设备信息", deviceActions),
        ActionCategory("display", "显示与交互", displayActions),
        ActionCategory("app", "应用管理", appActions),
        ActionCategory("log", "日志", logActions),
        ActionCategory("debug", "调试", debugActions),
        ActionCategory("system", "系统设置", systemActions),
        ActionCategory("perf", "性能与内存", perfActions),
        ActionCategory("misc", "其他工具", miscActions),
    )

    fun category(id: String): ActionCategory = categories.firstOrNull { it.id == id } ?: categories.first()
}
