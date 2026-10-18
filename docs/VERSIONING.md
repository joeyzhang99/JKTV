# JKTV 版本命名规范

## 格式

```
JKTV-V<MAJOR>.<MINOR>.<PATCH>
```

三个 `X` 均为非负整数，含义与递增时机如下：

| 位 | 名称 | 含义 | 递增时机 | 示例 |
|---|---|---|---|---|
| **X₁** | MAJOR | 重大功能改变 | 架构级重构、播放内核换代、不兼容变更 | 换内核 → `V2.0.0` |
| **X₂** | MINOR | 小功能升级 | 新增功能、体验改进、兼容新格式 | 加弹幕 → `V1.1.0` |
| **X₃** | PATCH | 错误修正 | 纯缺陷修复，无新功能 | 修崩溃 → `V1.0.1` |

## versionCode 编码

```
versionCode = MAJOR × 10000 + MINOR × 100 + PATCH
```

| versionName | versionCode |
|---|---|
| `JKTV-V1.0.0` | 10000 |
| `JKTV-V1.0.1` | 10001 |
| `JKTV-V1.1.0` | 10100 |
| `JKTV-V2.0.0` | 20000 |

## 文件命名

```
JKTV-V1.1.0-leanback-armeabi-v7a.apk    # 32 位电视版
JKTV-V1.1.0-leanback-arm64-v8a.apk      # 64 位电视版（V8）
```

两个 ABI 共用同一 versionCode / versionName，分别上传到同一 Release。

## 发布流程

1. 改 `app/build.gradle` 的 `versionCode` / `versionName`
2. 运行 `./publish.sh`（构建 → 校验 → 打 tag → 推送 → 建 Release）
3. 更新 `version.json` 供 App 内自动更新

## 版本历史

| 版本 | 日期 | 类型 | 说明 |
|---|---|---|---|
| `V1.0.0` | 2026-10-18 | MAJOR | 首个版本：基于 FongMi TV 上游，ExoPlayer + FFmpeg 双内核 |
| `V1.1.0` | 2026-10-18 | MINOR | 新增双 ABI 构建（armv7 + V8/arm64-v8a）；重写自动更新为四通道 + SHA-256 校验 + 手动检查 |
| `V1.2.0` | 2026-10-18 | MINOR | 修复"有声音无画面"（集成 Media3 官方 FFmpeg 软解 + 修复 URL 缺 scheme）；换动漫风小猫图标；TV banner 重做 |
| `V1.1.1` | 2026-10-18 | PATCH | 修正更新通道顺序（按中国移动无 VPN 实测）；移除不通的 jsDelivr 与 ghfast.top；新增 ghproxy.cn |
