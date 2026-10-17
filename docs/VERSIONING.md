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
JKTV-V1.0.0-leanback-armeabi-v7a.apk
JKTV-V1.0.0-leanback-arm64-v8a.apk
JKTV-V1.0.0-mobile-arm64-v8a.apk
```

## 发布流程

1. 改 `app/build.gradle` 的 `versionCode` / `versionName`
2. 运行 `./publish.sh`（构建 → 校验 → 打 tag → 推送 → 建 Release）
3. 更新 `version.json` 供 App 内自动更新

## 版本历史

| 版本 | 日期 | 类型 | 说明 |
|---|---|---|---|
| `V1.0.0` | 2026-10-18 | MAJOR | 首个版本：基于 FongMi TV 上游，ExoPlayer + FFmpeg 双内核 |
