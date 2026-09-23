# 智能操控机械臂 — 构建须知

## 症状 → 处置

**Symptom：`gradlew` 开始下载新的 Gradle 发行包（或卡在 Downloading）。**
处置：本机所有可用的 Gradle 都在 `D:\Gradle\gradle-*`。**直接用安装目录里的
`gradle.bat`，不要用 `gradlew`**：

```powershell
& "D:\Gradle\gradle-9.4.1\bin\gradle.bat" :hands:assembleDebug
```

wrapper 缓存已离线种好（`D:\Gradle\.gradle\wrapper\dists\gradle-9.4.1-bin\`，
含 `.ok` 标记），因此 `gradlew` 在 9.4.1 上也不会下载；但换版本仍会。

**Symptom：`Minimum supported Gradle version is X. Current version is Y.`**
处置：AGP 与 Gradle 必须配对。本机 Gradle 最高 9.4.1，所以：

| AGP | 要求的最低 Gradle |
|---|---|
| 9.2.0 / 9.2.1 | **9.4.1** ← 当前使用 |
| 9.3.x | 9.5.0 |
| 9.4.x | 9.6.0 |

升级 AGP 前先确认 `D:\Gradle` 里有对应的 Gradle。

**Symptom：`case R.id.xxx:` 报 “需要常量表达式 / constant expression required”。**
处置：AGP 8+ 默认 `android.nonFinalResIds=true`，`R.id.*` 不再是编译期常量。
把 `switch` 改成 `if / else if`。

**Symptom：动画抽风，或 inflate 时闪退，且换个系统版本才复现。**
处置：不要在 `res/` 里引用框架私有资源（`@*android:...`）。框架资源 ID 在不同
API 等级间不稳定，编译时定下的 ID 到别的版本上会指向别的资源甚至不存在。
插值器等一律在本地 `res/interpolator` 定义后用 `@interpolator/...` 引用。

**Symptom：`androidx.core:*` 报 “requires ... compile against version 37 or later”。**
处置：`androidx.core` 1.19.0 起要 compileSdk 37。本机平台到 36 为止，
故锁定 `androidx.core:core:1.18.0`（1.18.0 要 36，正好）。

## 不要做的事

- **不要本地编译 MediaPipe。** 它编译极慢（WSL 上曾近 10 小时）。
  只用官方预编译产物：`com.google.mediapipe:tasks-core` / `tasks-vision`
  与 `src/main/assets/hand_landmarker.task`，不要引入需要自行编译的源码依赖。
- 不要给 MediaPipe 传旋转角来"帮它对齐"。`ImageProxy.toBitmap()` 不套用
  `ImageInfo.getRotationDegrees()`，而 MediaPipe 返回的关键点坐标**始终在送进去的
  那张图**的坐标系里；若原图未旋正，关键点就在横向传感器帧上，与 PreviewView 旋正
  后的画面差 90°，叠加层会整个横过来。做法是**自己先把位图旋正再送推理**，输入图与
  预览画面坐标系一致，关键点可直接按同一套比例绘制。
