# JKTV

**Android TV / 手机端的影音聚合客户端** —— 空壳播放器 + 可插拔片源框架。

> **JKTV 本身不内置、不提供任何内容来源。** 所有片源需用户自行配置。

[![version](https://img.shields.io/badge/version-JKTV--V1.0.0-c0392b)]()
[![license](https://img.shields.io/badge/license-GPL--3.0-blue)]()
[![platform](https://img.shields.io/badge/Android-7.0%2B%20%7C%20minSdk%2024-3ddc84)]()

---

## 这是什么

JKTV 是一个**播放器外壳**：它不含任何影视内容，只提供
- 一套标准化的片源抓取接口（Spider）
- 一个强大的播放内核
- 完整的电视遥控器交互

你把片源配置（JSON）导入后，JKTV 就能把各家站点聚合成一个统一的浏览/搜索/播放界面。

**没有配置 = 一个空的播放器。** 这是设计如此。

## 播放内核

| 内核 | 用途 | 说明 |
|---|---|---|
| **ExoPlayer (Media3 1.11.1)** | 硬解主引擎 | H.264/HEVC/AV1、HDR10+、DRM |
| **FFmpeg (libffmpeg.so)** | 软解兜底 | 完整 FFmpeg，硬解失败时回退 |
| ASS/SSA | 字幕 | Media3 内置解析，支持底部位置与字号调节 |

> **V1.0.0 功能边界**：不含 MPV 内核 / 弹幕渲染 / libass 高级字幕 / 多版本选择。
> 原因：上游把这几项封装为**不公开的预编译 AAR**，无法随源码分发。
> 完整说明见 [UPSTREAM.md](UPSTREAM.md)，架构已预留接口便于后续接入开源方案。

## 核心能力

- **片源框架**：实现 `Spider` 接口即可接入任意站点，支持 Java / JavaScript / Python
- **播放**：倍速、音轨切换、字幕（内嵌/外挂/搜索）、片头片尾跳过、画中画
- **浏览**：分类筛选、搜索、播放记录、收藏、无痕模式
- **直播**：M3U/TXT/JSON 播放列表、XMLTV 节目单、回看
- **互通**：DLNA 投放/接收、本地 HTTP 控制（手机遥控电视）
- **自动更新**：GitHub + 两个加速镜像 + jsDelivr 四通道，SHA-256 校验

## 安装

从 [Releases](https://github.com/joeyzhang99/JKTV/releases) 下载对应架构：

| 设备 | 文件 |
|---|---|
| 电视 / 盒子（32 位） | `JKTV-V1.0.0-leanback-armeabi-v7a.apk` |
| 电视 / 盒子（64 位） | `JKTV-V1.0.0-leanback-arm64-v8a.apk` |
| 手机 | `JKTV-V1.0.0-mobile-arm64-v8a.apk` |

> 注意：不同签名的 APK 不能覆盖安装。切换来源前请先卸载。

## 配置

首次打开是空界面。在「设置 → 配置」中导入你自己的片源 JSON。

配置格式见上游文档：[配置字典](https://fongmi.github.io/TV/config/)、[Spider 开发](https://fongmi.github.io/TV/spider/)

## 构建

**环境**：JDK 21+、Android SDK (compileSdk 37)、Python 3.10（Chaquopy）

```bash
export JAVA_HOME=~/java/current
export ANDROID_HOME=~/Android/sdk

# 生成签名密钥（首次）
keytool -genkeypair -v -keystore ~/keys/jktv.jks -alias jktv \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass <你的密码> -keypass <你的密码> -dname "CN=JKTV"

# local.properties
cat > local.properties <<PROP
sdk.dir=$HOME/Android/sdk
storeFile=$HOME/keys/jktv.jks
keyAlias=jktv
storePassword=<你的密码>
PROP

# 构建
./gradlew :app:assembleLeanbackRelease
```

或用 `./publish.sh` 一键完成构建+发版。

## 版本规范

见 [docs/VERSIONING.md](docs/VERSIONING.md)：`JKTV-V<MAJOR>.<MINOR>.<PATCH>`，versionCode = MAJOR×10000+MINOR×100+PATCH。

## 许可证

**GPL-3.0**。JKTV 基于 [FongMi/TV](https://github.com/FongMi/TV) 二次开发，完整许可证见 [LICENSE.md](LICENSE.md)。

依据 GPL-3.0：
- 保留上游版权声明与来源（见 [UPSTREAM.md](UPSTREAM.md)）
- 全部源代码随本仓库公开
- 以同等许可分发

## 免责声明

- 本项目仅供**技术学习与个人使用**
- 不提供、不托管、不分发任何影视内容
- 使用者的片源配置及其合法性由使用者自行负责
- 「JKTV」与上游 FongMi 项目无从属关系
