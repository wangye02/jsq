# ⏱️ MultiTimer 多路计时器

同时运行多个独立倒计时的 Android 应用，专为小米澎湃 HyperOS 优化，2MB 极简包体。

## ✨ 功能

- **多路倒计时** — 同时添加并运行多个计时器，各自独立开始/暂停/重置/删除
- **收藏预设** — 内置常用时长，每次添加自动收藏，一键复用
- **独立铃声** — 每个计时器可单独选择系统铃声
- **独立音量** — 每路计时器滑动调节铃声音量
- **后台保活** — Foreground Service + WakeLock，切后台不中断
- **循环响铃** — 到时循环播放 2 分钟，通知栏一键停止
- **小米适配** — 首次启动引导关闭电池优化 / 开启自启动
- **极简资源** — 纯原生 View，Release 包仅 **2.0 MB**，运行内存 ~30MB

## 📦 下载

在 [Releases](https://github.com/wangye02/jsq/releases) 下载最新 APK，或直接安装项目根目录下的：

- `MultiTimer-release-1.0.apk` — 已签名优化版（推荐）
- `MultiTimer-debug-1.0.apk` — 调试版

## 🛠️ 构建

```bash
# 需要 JDK 17 + Android SDK (API 34)
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
# APK 输出: app/build/outputs/apk/release/MultiTimer-release-1.0.apk
```

依赖：
- `androidx.appcompat:appcompat` — 基础兼容
- `androidx.recyclerview:recyclerview` — 列表
- `com.google.android.material:material` — Material Design 组件

## 🏗️ 架构

```
app/src/main/java/com/example/multitimer/
├── MainActivity.java     主界面 (Handler 轮询刷新)
├── TimerService.java     后台核心 (Foreground Service)
├── TimerData.java        计时器数据模型 (Parcelable)
├── TimerAdapter.java     RecyclerView 适配器
├── TimerReceiver.java    AlarmManager 闹钟接收
├── BootReceiver.java     开机自启 (小米适配)
└── PresetManager.java    收藏预设 (SharedPreferences + org.json)
```

## 📱 兼容性

- Android 7.0 (API 24) 及以上
- 小米 HyperOS / MIUI 特殊适配
- 理论上兼容所有 Android 设备

## 📄 License

MIT
