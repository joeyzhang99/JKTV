# JKTV 上游来源与改动说明

## 上游

| 项目 | 地址 | 许可证 | 用途 |
|---|---|---|---|
| **FongMi / TV（影視TV）** | https://github.com/FongMi/TV | **GPL-3.0** | JKTV 的全部应用骨架、播放器、Spider 框架 |

**基线提交**：`696dc992abebeec439fba2d6ea7fa62ec86caf18`（"Fix youtube"，上游 5.5.2）
克隆时间：2026-10-18

> 选择该基线的原因：上游自 5.6.x 起把 **MPV 内核、弹幕渲染、libass 字幕、MediaTitle 多版本选择** 封装为
> 不公开的预编译 AAR（`lib-*.aar`，被 `.gitignore` 排除，所有 fork 均不提供）。
> 该基线是最后一个不依赖任何私有二进制的完整可构建版本。

## JKTV 相对上游的改动

### 品牌
| 项 | 上游 | JKTV |
|---|---|---|
| applicationId | `com.fongmi.android.tv` | `com.jktv.android.tv` |
| 应用名 | 影視 / TV | **JKTV** |
| versionName | `5.5.2` | `JKTV-V1.0.0` |
| versionCode | 552 | 10000 |

### 依赖修复（为可独立构建）
| 问题 | 处理 |
|---|---|
| `libs.media` 指向 `androidx.media`（旧库） | 改为 **`androidx.media3` 1.11.1**，并补齐 12 个子模块 |
| `nextlib` 的 `NextRenderersFactory`（未公开） | 改用 **Media3 官方 `DefaultRenderersFactory`**，硬解 MediaCodec + 软解 libffmpeg.so |
| `androidx.media3.common.MediaTitle`（私有） | **移除多版本/多章节选择功能**（TitleDialog/TitleAdapter），V1.0.0 不提供 |
| `androidx.media3.ui.danmaku`（私有 AAR） | 重建 `DanmakuConfig`（完整 27 字段）+ `DanmakuController` 接口；**渲染未接入** |
| `PlayerView.setRender(int)`（私有） | 移除渲染模式切换 |
| `SubtitleView.setBottomPosition/addPosition`（私有） | 改用官方 **`setBottomPaddingFraction`** |
| `Player.COMMAND_*_OFFSET` 与音视频偏移（私有） | 改为本地暂存字段，接口保留 |
| `PlayerManager` 缺少 null 守卫 | 补齐 3 处，弹幕未启用时安全跳过 |

### 更新机制
- 重写 `utils/Github.java`：GitHub raw + `ghproxy.net` + `gh-proxy.com` + jsDelivr **四通道**
- 复用上游 `Updater`（读取 `{name,desc,code}` JSON，比对 versionCode）
- 新增 `publish.sh`：构建 → SHA-256 → version.json → tag → 推送 → Release

## GPL-3.0 合规

依据 GPL-3.0 第 5 条，JKTV 作为衍生作品：
- ✅ 保留原始版权声明与上游来源（本文件）
- ✅ 以 **GPL-3.0** 同样许可分发，完整许可证见 `LICENSE.md`
- ✅ **全部源代码随仓库公开**
- ✅ 不内置、不提供任何内容源（与上游一致）

## 已知功能缺口（V1.0.0）

| 功能 | 状态 | 原因 |
|---|---|---|
| MPV 播放内核 | ❌ 未含 | 上游私有 AAR |
| 弹幕渲染 | ⚠️ 配置项保留，渲染未接入 | 上游私有 AAR |
| libass 高级字幕（样式/字体） | ⚠️ 降级为 Media3 内置 ASS 解析 | 上游私有 AAR |
| 多版本/多章节选择 | ❌ 未含 | 上游私有扩展 |
| 渲染模式切换 | ❌ 未含 | 上游私有扩展 |
| 音视频时间轴偏移 | ⚠️ 仅本地暂存 | 上游私有扩展 |

> 以上缺口均可在后续版本通过集成开源方案补齐（如 `mpv-android` MIT、`DanmakuFlameMaster` Apache-2.0），
> 架构已预留接口。
