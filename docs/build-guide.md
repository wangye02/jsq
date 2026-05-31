# 在 Termux PRoot Ubuntu 下构建 Android APK 全记录

> 环境：Android 手机 → Termux → PRoot Distro Ubuntu 25.10 (aarch64)  
> 成果：成功编译 MultiTimer release APK (2.0 MB)

---

## 一、环境概览

```
硬件: Android 手机 (aarch64/ARM64)
宿主: Termux
容器: PRoot Distro Ubuntu 25.10 (Questing Quokka)
内核: Linux 6.17.0-PRoot-Distro aarch64
磁盘: ~224G (可用 ~35G)
```

**核心矛盾**：CPU 是 ARM64，但 Android SDK 的 build-tools 只提供 x86-64 二进制。  
**解决思路**：用 `box64` 模拟运行 x86-64 的 aapt2。

---

## 二、部署步骤

### 1. 安装 JDK 17

```bash
apt-get update
apt-get install -y openjdk-17-jdk-headless
```

验证：
```bash
java -version
# openjdk version "17.0.19" ...
```

### 2. 下载 Android SDK 命令行工具

```bash
mkdir -p /opt/android-sdk/cmdline-tools
cd /opt/android-sdk/cmdline-tools

# 下载 (约 147MB)
curl -L -o cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

unzip -q cmdline-tools.zip
mv cmdline-tools latest   # ⚠️ 目录名必须是 "latest"
```

### 3. 安装 SDK 组件

```bash
export ANDROID_SDK_ROOT=/opt/android-sdk

yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager \
  --sdk_root=/opt/android-sdk \
  "build-tools;34.0.0" \
  "platforms;android-34"
```

### 4. 创建 local.properties

在项目根目录创建 `local.properties`：
```properties
sdk.dir=/opt/android-sdk
```

### 5. 生成 Gradle Wrapper

安装系统 Gradle（仅用于生成 wrapper，之后用 wrapper 自带的版本）：
```bash
apt-get install -y gradle   # 版本 4.4.1 就够用

cd 你的项目目录
gradle wrapper --gradle-version 8.7
```

---

## 三、踩坑与解决

### 坑 1：网络不通 — Gradle 官方源下载超时

**现象**：`gradlew` 首次运行从 `services.gradle.org` 下载 Gradle 超时

**解决**：`gradle/wrapper/gradle-wrapper.properties` 改用腾讯云镜像：

```properties
# 原来
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip

# 改为
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip
```

同时在 `settings.gradle.kts` 中添加 Maven 镜像：

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
    }
}
```

### 坑 2：aarch64 无法运行 x86-64 的 build-tools ⚠️ 核心难点

**现象**：
```
> AAPT2 aapt2-8.2.2-10154469-linux Daemon #0: Daemon startup failed
```

**根因**：Google 只为 Linux 提供 x86-64 的 build-tools，`aapt2`/`aapt` 等原生
工具在 aarch64 上无法执行。

```bash
$ file /opt/android-sdk/build-tools/34.0.0/aapt2
ELF 64-bit LSB pie executable, x86-64, ...
```

**解决方案 — 安装 box64 模拟器**：

```bash
apt-get install -y box64
```

然后将 build-tools 中的二进制包装为通过 box64 运行：

```bash
cd /opt/android-sdk/build-tools/34.0.0

# 包装 aapt2
mv aapt2 aapt2.real
cat > aapt2 << 'SCRIPT'
#!/bin/sh
exec box64 /opt/android-sdk/build-tools/34.0.0/aapt2.real "$@"
SCRIPT
chmod +x aapt2

# 同样处理 aapt
mv aapt aapt.real
cat > aapt << 'SCRIPT'
#!/bin/sh
exec box64 /opt/android-sdk/build-tools/34.0.0/aapt.real "$@"
SCRIPT
chmod +x aapt
```

### 坑 3：AGP 用自己的 aapt2，不经过 build-tools

Gradle 缓存中还有一份 aapt2（AGP 从 Maven 下载的），也需要包装：

```bash
# 找到并包装所有 Gradle 缓存中的 aapt2
find /root/.gradle/caches/transforms-4 -name "aapt2" -type f ! -name "*.real" \
  | while read f; do
    if file "$f" | grep -q "ELF"; then
        dir=$(dirname "$f")
        mv "$f" "$f.real"
        cat > "$f" << 'SCRIPT'
#!/bin/sh
exec box64 "$(dirname "$0")/aapt2.real" "$@"
SCRIPT
        chmod +x "$f"
    fi
done
```

然后在 `gradle.properties` 中强制 AGP 使用 build-tools 的 aapt2：

```properties
android.aapt2FromMavenOverride=/opt/android-sdk/build-tools/34.0.0/aapt2
```

> ⚠️ 每次 `clean` 后 Gradle 可能重新解压 aapt2，如果再次出现 daemon 启动失败，
> 重新执行上面的 `find` 包装命令。

### 坑 4：Gradle JVM 内存不足

**现象**：Release 构建时 R8/Lint 报 `java.lang.OutOfMemoryError: Metaspace`

**解决**：增大 `gradle.properties` 中的 JVM 参数：

```properties
org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m
```

### 坑 5：Release APK 未签名无法安装

**现象**：`解析软件包时出现问题。(33)`

**解决**：生成签名密钥并在 `build.gradle.kts` 中配置：

```bash
keytool -genkey -v \
  -keystore app/debug.keystore \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass android -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"
```

```kotlin
// app/build.gradle.kts
signingConfigs {
    create("release") {
        storeFile = file("debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ...
    }
}
```

### 坑 6：R8 混淆导致 Gson TypeToken 崩溃

**现象**：Release 包中 `new TypeToken<List<Preset>>(){}.getType()` 抛出：
```
IllegalStateException: TypeToken must be created with a type argument
```

**解决**：不用 Gson，改用 Android 内置的 `org.json`（零依赖，不依赖泛型反射）：

```java
// 保存
JSONArray arr = new JSONArray();
for (Preset p : presets) {
    JSONObject obj = new JSONObject();
    obj.put("l", p.label);
    obj.put("s", p.seconds);
    arr.put(obj);
}
prefs.edit().putString(KEY, arr.toString()).apply();

// 加载
JSONArray arr = new JSONArray(json);
for (int i = 0; i < arr.length(); i++) {
    JSONObject obj = arr.getJSONObject(i);
    presets.add(new Preset(obj.getString("l"), obj.getLong("s")));
}
```

### 坑 7：Android 14+ 全局广播不可靠

**现象**：Service 发 `sendBroadcast()`，Activity 收不到

**解决**：不用广播刷新 UI，改用 `Handler` 定时轮询 Service 状态：

```java
// 每 500ms 主动拉取 Service 数据
pollRunnable = new Runnable() {
    @Override
    public void run() {
        refreshUI();
        handler.postDelayed(this, 500);
    }
};
```

### 坑 8：前台 Service 要用 startForegroundService

Android 8+ 从后台启动 Service 受限。从 Activity 发指令给 Service 时统一用：

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(intent);
} else {
    context.startService(intent);
}
```

---

## 四、当前环境使用方法

### 目录结构

```
/mnt/termux_home/jsq/          ← MultiTimer 计时器项目
├── app/
│   ├── build.gradle.kts
│   ├── debug.keystore         ← 签名密钥（已提交 Git）
│   └── src/main/java/com/example/multitimer/
│       ├── MainActivity.java
│       ├── TimerService.java
│       ├── TimerAdapter.java
│       └── ...
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── local.properties           ← sdk.dir=/opt/android-sdk
├── gradlew                    ← Gradle Wrapper
├── MultiTimer-debug-1.0.apk   ← 构建产物
└── MultiTimer-release-1.0.apk ← 构建产物

/opt/android-sdk/              ← Android SDK
├── build-tools/34.0.0/        ← aapt2 已包装为 box64
├── platforms/android-34/
└── cmdline-tools/latest/
```

### 日常构建命令

```bash
cd /mnt/termux_home/jsq

# 设置 SDK 路径
export ANDROID_HOME=/opt/android-sdk

# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本
./gradlew assembleRelease

# 同时编译两个版本
./gradlew assembleDebug assembleRelease

# 清理后重新编译
./gradlew clean assembleRelease

# APK 输出位置
# Debug: app/build/outputs/apk/debug/MultiTimer-debug-1.0.apk
# Release: app/build/outputs/apk/release/MultiTimer-release-1.0.apk
```

### 如果 aapt2 daemon 启动失败

重新包装 Gradle 缓存中的 aapt2：

```bash
find /root/.gradle/caches/transforms-4 -name "aapt2" -type f ! -name "*.real" \
  | while read f; do
    if file "$f" | grep -q "ELF"; then
        dir=$(dirname "$f")
        mv "$f" "$f.real"
        cat > "$f" << 'SCRIPT'
#!/bin/sh
exec box64 "$(dirname "$0")/aapt2.real" "$@"
SCRIPT
        chmod +x "$f"
    fi
done
```

### Git 推送

```bash
cd /mnt/termux_home/jsq
git add -A
git commit -m "描述"
git push origin master
```

### 在新电脑上构建

1. 安装 JDK 17+
2. 安装 Android SDK (API 34, build-tools 34.0.0)
3. 如果是 x86-64 系统，跳过 box64 相关步骤，移除 `android.aapt2FromMavenOverride`
4. `./gradlew assembleRelease`

---

## 五、关键文件速查

| 文件 | 作用 |
|------|------|
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 下载地址（已改腾讯云镜像） |
| `settings.gradle.kts` | Maven 仓库镜像配置 |
| `gradle.properties` | JVM 内存 + aapt2 覆盖路径 |
| `local.properties` | SDK 路径 |
| `app/build.gradle.kts` | 签名配置 + 依赖 |
| `app/debug.keystore` | 签名密钥 |

---

## 六、新项目复现清单

如果从头在另一台 aarch64 设备上搭环境：

1. ✅ `apt install openjdk-17-jdk-headless box64 gradle`
2. ✅ 下载 Android commandlinetools，解压到 `/opt/android-sdk/cmdline-tools/latest/`
3. ✅ `sdkmanager "build-tools;34.0.0" "platforms;android-34"`
4. ✅ 包装 aapt2/aapt 为 box64 脚本
5. ✅ `gradle wrapper --gradle-version 8.7`，改镜像地址
6. ✅ `settings.gradle.kts` 加腾讯云 Maven 镜像
7. ✅ `gradle.properties` 加 `android.aapt2FromMavenOverride`
8. ✅ 生成 keystore，配置 signingConfigs
9. ✅ 不用 Gson，用 `org.json`
10. ✅ UI 刷新用 Handler 轮询，不用全局广播
