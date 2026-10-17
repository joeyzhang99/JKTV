# JKTV 播放内核选型与参考项目

> 本文记录 JKTV 在"视频播放能力"上的选型依据，以及调研过的 GitHub 开源项目。
> 排序按 Star 数（2026-10-18 实测）。

## 一、结论先行

JKTV V1.1.0 的播放内核：

| 层 | 组件 | 许可 | 说明 |
|---|---|---|---|
| 硬解主引擎 | **ExoPlayer / Media3 1.11.1** | Apache-2.0 | H.264/HEVC/AV1、HDR10+、DRM |
| 软解兜底 | **FFmpeg（libffmpeg.so）** | LGPL/GPL | 硬解失败时回退 |
| 字幕 | Media3 内置 SSA/ASS 解析 | Apache-2.0 | 支持底部位置、字号调节 |

**没有采用 MPV / libVLC / ijkplayer 的原因见下文"为什么不用"**。

## 二、调研过的项目（按 Star）

### 播放内核类（真正可集成的库）

| # | 项目 | Star | 许可 | 语言 | 评估 |
|---|---|---|---|---|---|
| 1 | [google/ExoPlayer](https://github.com/google/ExoPlayer) | 21.9k | Apache-2.0 | Java | ✅ **已采用**。Google 官方，Android 端事实标准 |
| 2 | [androidx/media](https://github.com/androidx/media) | 3.0k | Apache-2.0 | Java | ✅ **已采用**。即 Media3，ExoPlayer 的继任者 |
| 3 | [CarGuo/GSYVideoPlayer](https://github.com/CarGuo/GSYVideoPlayer) | 21.5k | Apache-2.0 | Java | ⚠️ 功能全但偏重 UI 层，且长期未发版；对 TV 场景过重 |
| 4 | [bilibili/ijkplayer](https://github.com/bilibili/ijkplayer) | 33.2k | GPL-2.0 | C | ❌ **GPL-2.0 与 JKTV 的 GPL-3.0 不兼容**；且基于 FFmpeg n3.4（2016 年），太旧 |
| 5 | [videolan/vlc-android](https://github.com/videolan/vlc-android) | 4.1k | GPL-2.0 | Java | ❌ **同样是 GPL-2.0**；体积 +20MB，需 NDK 全量编译 |
| 6 | [mpv-android/mpv-android](https://github.com/mpv-android/mpv-android) | 3.6k | **MIT** | Kotlin | ⚠️ **许可最宽松**，是后续补齐 MPV 内核的首选 |
| 7 | [marlboro-advance/mpvEx](https://github.com/marlboro-advance/mpvEx) | 2.7k | Apache-2.0 | Kotlin | ⚠️ 备选；活跃度低于 mpv-android |
| 8 | [abdallahmehiz/mpvKt](https://github.com/abdallahmehiz/mpvKt) | 1.3k | Apache-2.0 | Kotlin | ⚠️ 备选 |
| 9 | [anilbeesetti/nextplayer](https://github.com/anilbeesetti/nextplayer) | 4.6k | GPL-3.0 | Kotlin | ⚠️ **许可兼容（GPL-3.0）**，但其 `nextlib` 扩展库未公开分发 |
| 10 | [Doikki/DKVideoPlayer](https://github.com/Doikki/DKVideoPlayer) | 5.3k | Apache-2.0 | Java | ⚠️ 手机端为主，无 TV 遥控器交互 |

### 工具/下载类（非播放内核，仅记录）

| 项目 | Star | 许可 | 说明 |
|---|---|---|---|
| [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp) | 198.8k | Unlicense | 命令行下载器，非播放器 |
| [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) | 152.4k | Apache-2.0 | 投屏工具，非播放器 |
| [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit) | 5.9k | LGPL-3.0 | FFmpeg 预编译包，**JKTV 已内置 libffmpeg.so** |
| [xufuji456/FFmpegAndroid](https://github.com/xufuji456/FFmpegAndroid) | 5.9k | none | FFmpeg 转码/裁剪，非播放内核 |

## 三、为什么不用 MPV / VLC / ijkplayer

### 1. 许可证冲突（决定性因素）

JKTV 的上游 FongMi/TV 是 **GPL-3.0**，衍生作品必须以同许可分发：

| 内核 | 许可 | 与 GPL-3.0 兼容？ |
|---|---|---|
| ExoPlayer / Media3 | Apache-2.0 | ✅ 兼容 |
| FFmpeg | LGPL-3.0 / GPL | ✅ 兼容（动态链接） |
| mpv-android | MIT | ✅ 兼容 |
| **ijkplayer** | **GPL-2.0** | ❌ **不兼容**（FSF 明确 GPL-2.0 only 与 GPL-3.0 冲突） |
| **vlc-android** | **GPL-2.0** | ❌ **不兼容** |

ijkplayer 的 33.2k Star 最高，vlc-android 的容错性最好，但**都不能合法集成**。

### 2. 目标设备约束

用户的电视是 **TCL 55Q9K，ARMv7 32 位**：
- ijkplayer 官方只发布到 FFmpeg n3.4（2016），且社区 armv7 构建稀缺
- vlc-android 需 NDK 全量编译，APK +20MB
- mpv-android 的 armv7 构建需要自行交叉编译 libmpv

### 3. 功能重复

ExoPlayer + FFmpeg 已覆盖：
- 硬解（MediaCodec）：H.264 / HEVC / AV1
- 软解兜底（FFmpeg JNI）：几乎所有容器
- HDR / DRM / 字幕 / 倍速 / 音轨切换

再加一个内核对实际体验提升有限，却显著增加包体与维护成本。

## 四、后续演进路线

若确需 MPV 内核（更强的软解兼容性、ASS 高级渲染），推荐路径：

1. **集成 [mpv-android](https://github.com/mpv-android/mpv-android)**（MIT，最宽松）
   - 需自行交叉编译 `libmpv.so` for `armeabi-v7a` 与 `arm64-v8a`
   - 通过 `PlayerView` 的 `setPlayer` 接入，或按上游的 `PlayerEngine` 接口新增一个实现
2. **弹幕渲染**：接入 [DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster)（Apache-2.0）
   - JKTV 已重建 `DanmakuConfig`（27 字段）与 `DanmakuController` 接口，可直接对接

以上均不影响上层 `PlayerEngine` 抽象，改造成本可控。

## 五、架构说明

JKTV 保留了上游的引擎抽象层，便于后续扩展：

```
PlayerEngine (接口)
├── ExoPlayerEngine   ← 当前唯一实现
└── (预留) MpvPlayerEngine  ← 后续可接入
```

切换内核只需在 `PlayerEngineFactory` 注册新实现，上层 `PlayerManager` 无感知。
