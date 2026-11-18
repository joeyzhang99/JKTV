# TiviMate 5.2.0 逆向分析报告

> 分析对象：`TiviMate-IPTV-5.2.0-universal-32bit.apk`
> SHA-256：`1d0759a894ff59a0d6779290c5dc86cf4808daecd84044f9840ed9ede0233c5d`
> 包名：`ar.tvplayer.tv` / versionCode 5208 / minSdk 23 / targetSdk 34
> 分析工具：REA + aapt2 + 静态字符串取证
> 分析日期：2026-11-18

## 一、结论摘要

| 项目 | 结果 |
|---|---|
| 代码可读性 | ❌ **不可读**，被 DexProtector 商业加固 |
| 架构可见性 | ✅ 类名/包结构未混淆，可完整还原模块划分 |
| 技术栈 | material3 / media3(ExoPlayer+FFmpeg) / Room / Leanback / Glide / OkHttp / Parse / Billing |
| 协议支持 | Stalker Portal、Xtream Codes、M3U |
| 核心能力 | EPG、Timeshift/Catchup、录制、提醒、PiP |

## 二、加固方式（决定性发现）

```
lib/*/libdexprotector.so     ← DexProtector 运行时
lib/*/libdpboot.so           ← 引导桩
assets/classes.dex.dat       ← 加密的真实 DEX（16 进制头 5b5c019e，非 dex\n035）
assets/dp.arm-v7.so.dat      ← 按 ABI 分发的解密组件
```

真实业务代码在 `classes.dex.dat` 中加密存放，安装后由 DexProtector 解密加载。
`classes.dex`（5.7MB）仅含引导代码与未混淆的符号引用。

**含义**：无法通过反编译获得可运行源码，任何"复制代码"的路线在技术上不成立。

## 三、技术栈（从符号引用提取）

- `androidx.media3.*` —— ExoPlayer 播放内核
- `androidx.media3.decoder.ffmpeg` —— FFmpeg 软解扩展（与 JKTV 同方案）
- MediaCodec —— 硬解（38 处引用）
- `androidx.room` —— 本地数据库
- `androidx.leanback` —— TV 框架
- `com.bumptech.glide` —— 图片/台标加载
- `okhttp3` + `PublicSuffixDatabase` —— 网络层
- `com.parse` —— 后端服务（ParseLoginActivity）
- `com.android.billingclient` —— 付费解锁

## 四、屏幕结构（AndroidManifest 提取）

### 主流程
| Activity | 职责 |
|---|---|
| `ui.MainActivity` | 主界面 |
| `player.ui.CustomPlayerView` / `CustomPlayerControlView` | 播放器与控件 |

### 播放列表管理
| Activity | 职责 |
|---|---|
| `playlists.wizard.PlaylistWizardActivity` | 添加播放列表向导 |
| `playlists.PlaylistNameActivity` | 重命名 |
| `playlists.DeletePlaylistActivity` | 删除 |

### 节目单（EPG）
| Activity | 职责 |
|---|---|
| `tvguide.ui.CopyChannelsActivity` | 复制频道 |
| `tvguide.ui.CreateChannelGroupActivity` | 创建频道分组 |
| `settings.ui.tvguide.TvGuideUrlActivity` | EPG 源地址 |
| `settings.ui.tvguide.TvGuideSourceNameActivity` | EPG 源命名 |
| `settings.ui.tvguide.DeleteTvGuideSourceActivity` | 删除 EPG 源 |

### 设置
| 模块 | Activity |
|---|---|
| 外观 | `appearance.LogosFolderActivity` |
| 常规 | `general.SelectBackupActivity` / `general.RestoreBackupActivity` |
| 分组 | `groupoptions.ResetPositionsActivity` |
| 其他 | `other.RecordingsFolderActivity` / `commons.ResetWatchTimeActivity` |

### 其他
| Activity | 职责 |
|---|---|
| `reminders.ui.ReminderPopupActivity` | 节目开播提醒 |
| `base.ui.UpdateAppActivity` | 应用自更新 |
| `base.ui.filepicker.FilePickerActivity` / `SmbActivity` | 本地/SMB 文件选择 |
| `base.ui.WebViewActivity` | 内嵌网页 |
| `unlock.ui.UnlockPremiumActivity` | 付费解锁 |
| `core.util.RestartProcessActivity` | 崩溃后重启进程 |
| `base.ScreenOnService` | 唯一 Service，保活屏幕常亮 |

## 五、功能强度（符号出现次数）

| 能力 | 引用数 | 说明 |
|---|---|---|
| catchup | 15 | 回看 |
| shift / timeshift | 10 / 1 | 时移 |
| recording | 10 | 录制 |
| archive | 4 | 频道归档回看 |
| xmltv | 1 | EPG 格式 |
| epgGranularity | 1 | EPG 时间粒度设置 |
| pictureinpicture | 3 | 画中画 |
| stalker | 23 | Stalker Portal 协议（占比最高） |

Room 实体：`RecordingForPush`、`RecordingRepeatRules` → 录制支持推送与重复规则。

## 六、对 JKTV 的差距分析

JKTV 已具备：
- ✅ ExoPlayer + FFmpeg 软解（与 TiviMate 同方案）
- ✅ Room 数据库
- ✅ 直播 + EPG 解析（`EpgParser` 全量解析并绑定到 `Channel.dataList`）
- ✅ `Channel` 已含 `catchup` 字段、`Group` 分组
- ✅ M3U / 多源（catvod spider）

JKTV 缺失（按价值排序）：

| 优先级 | 功能 | 状态 |
|---|---|---|
| P0 | 多频道时间轴节目单（TV Guide 网格） | ✅ **V1.2.16 已实现** |
| P1 | 回看/时移播放（catchup） | 待做（字段已有） |
| P1 | 录制 + 预约 | 待做 |
| P2 | 节目开播提醒 | 待做 |
| P2 | 多播放列表管理向导 | 待做 |
| P2 | 台标文件夹/自定义台标 | 待做 |
| P3 | 备份/恢复 | 待做 |
| P3 | Stalker Portal 协议 | 待做 |
| P3 | PiP | 待做 |

## 七、合规说明

TiviMate 为闭源商业软件，本项目**不复制其代码、界面、图标、文案或资源**。
本报告仅通过对安装包的静态观察，还原其**功能结构与技术选型**（思想层面），
据此在 JKTV（GPL-3.0）中用原创代码实现同类功能。

## 八、V1.2.16 交付内容

新增文件（全部原创）：

| 文件 | 职责 |
|---|---|
| `ui/custom/GuideTimeline.java` | 时间↔像素换算、当天节目抽取 |
| `ui/custom/TimeRulerView.java` | 顶部时间刻度尺（自绘，含当前时间红线） |
| `ui/custom/GuideRowView.java` | 单频道行：固定频道列 + 节目块（自绘） |
| `ui/adapter/GuideAdapter.java` | 行适配器，焦点/光标/按键回调 |
| `ui/activity/TvGuideActivity.java` | 节目单界面 |
| `res/layout/activity_tv_guide.xml` | 界面布局 |

交互：
- `▲▼` 切换频道
- `◀▶` 在当前频道的节目间移动（自动平移时间轴保证可见）
- `OK` 播放该频道
- 长按直播界面的节目条进入本界面

技术要点：所有行共享同一横向偏移指针（平移而非各自滚动），
因此刻度尺与节目块永远对齐，上下滚动不会错位。
