# AdbTools

把 `adb_tools.sh` 的全部功能搬进 IDE 工具窗口的 IntelliJ Platform 插件。界面使用 Compose + Jewel，
左侧为分类 / 功能菜单，右侧为参数表单与实时命令输出控制台。

## 功能

工具窗口按 9 个分类组织：

| 分类 | 功能 |
| --- | --- |
| 网络与环境 | 切换网络环境、查询网络环境、设置埋点 UT 实时上报 |
| 设备信息 | 查询 UUID、MAC 地址、蓝牙地址、系统音量、重启设备、重启 adb 服务 |
| 显示与交互 | 横竖屏切换、分辨率 / DPI 修改与预设、过度绘制开关、模拟电源键 |
| 应用管理 | 安装 / 卸载应用、版本号、启动 app / Activity / Service、开机广播、清除应用数据（缓存 + 用户数据）、安装路径、顶层 Activity、应用运行时长、进程查看 / kill / 强停、包名查找、Monkey |
| 日志 | logcat 过滤、ANR 导出 |
| 调试 | ContentProvider 读取 |
| 系统设置 | settings 读写、getprop / setprop |
| 性能与内存 | GC 触发、低内存模拟、meminfo、dumpheap |
| 其他工具 | 语音命令、Deeplink、截图 / 录屏、退出工厂模式、关闭模拟器 |

脚本中的自动提权逻辑保留：需要时自动执行 `adb root`、`adb remount`，`setprop` 后回读校验，
UT 实时上报完成后会校验进程是否真正重启。

## 界面

- 顶部设备区提供「刷新 / 设备 / 设置」，下方可直接「重启设备」「重启 adb」
- 左侧分类可折叠，默认全部收起；支持按功能名 / 说明关键字搜索
- 功能可收藏，收藏项固定在导航顶部
- 自动记忆上次选择的分类与功能、输入框历史值、控制台高度
- 参数区与控制台之间可拖动分隔条调整高度
- 控制台支持自动滚动、只看问题（警告 / 错误），并可复制最后执行的 adb 命令

## 环境要求

- IntelliJ IDEA 2025.2+ / Android Studio 2025.2+（`sinceBuild = 252`）
- 2024.3 及更早版本内置的 Jewel 缺少 `addComposeTab` 等 API，插件无法显示界面
- JDK 21
- Android SDK platform-tools（adb）

## 安装与入口

```bash
./gradlew buildPlugin   # 产物：build/distributions/AdbTools-1.0-SNAPSHOT.zip
```

在目标 IDE 中 `Settings | Plugins | ⚙ | Install Plugin from Disk...` 选择上面的 zip，然后**重启 IDE**。

打开工具的三种方式：

1. 菜单 `Tools | ADB 工具箱`
2. 菜单 `View | Tool Windows | ADB 工具箱`
3. 右侧边栏的 `ADB 工具箱` 竖排按钮

## 构建与运行

```bash
./gradlew runIde       # 启动带插件的 IDE
./gradlew test         # 运行解析、注册表与工具窗口冒烟测试
./gradlew buildPlugin  # 打包 zip 到 build/distributions
```

## adb 定位顺序

1. 工具窗口「设置」中手动指定的 adb 路径
2. `ANDROID_HOME` / `ANDROID_SDK_ROOT`
3. 当前项目 `local.properties` 的 `sdk.dir`
4. 常见的 macOS / Linux SDK 安装路径及 `PATH`

## 输出目录

截图、录屏、ANR、heap dump 和控制台日志默认保存到项目根目录的 `adb-output/`，
可在「设置」中改为任意目录。

## 注意

- 「清除 app 数据」不可恢复，执行前必须勾选确认。
- `adb root` / `adb remount` 在 user 版本设备上可能失败，插件会输出 `disable-verity` 提示。
