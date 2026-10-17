#!/bin/bash
# JKTV 发布脚本：构建 → 校验 → 版本清单 → 提交 → 打 tag → 推送 →（可选）建 Release
#
# 用法：
#   ./publish.sh                      # 只做本地提交 + tag（不推送）
#   GH_TOKEN=ghp_xxx ./publish.sh     # 完整发布：推送 + 创建 GitHub Release 并上传资产
#
# GH_TOKEN 需要 classic token 且勾选 repo 权限：
#   https://github.com/settings/tokens  → Generate new token (classic) → 勾选 repo
set -euo pipefail
export JAVA_HOME="$HOME/java/current"
export PATH="$JAVA_HOME/bin:$PATH"

PROJ="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJ"

# ---- 版本号（发新版时改这三个数）----
V_MAJOR=1
V_MINOR=0
V_PATCH=0
VERSION="V${V_MAJOR}.${V_MINOR}.${V_PATCH}"
VERSION_NAME="JKTV-${VERSION}"
VERSION_CODE=$((V_MAJOR * 10000 + V_MINOR * 100 + V_PATCH))
REPO="joeyzhang99/JKTV"
REMOTE="https://github.com/${REPO}.git"

# Gradle：优先用本地已解压的发行版，避免 wrapper 下载被墙
GRADLE_BIN="${GRADLE_BIN:-$HOME/.gradle/wrapper/dists/gradle-9.5.0-bin/estug5qhtw5xqrldjyqnn9yr5/gradle-9.5.0/bin/gradle}"

echo "== 1/7 同步版本号到 build.gradle =="
sed -i.bak -E "s/versionCode [0-9]+/versionCode ${VERSION_CODE}/; s/versionName \"[^\"]*\"/versionName \"${VERSION_NAME}\"/" app/build.gradle
rm -f app/build.gradle.bak
grep -E "versionCode|versionName" app/build.gradle | head -2

echo "== 2/7 构建 leanback arm32（电视最常用） =="
"$GRADLE_BIN" -q :app:assembleLeanbackRelease
APK_SRC=$(find app/build/outputs/apk/leanback/release -name "*.apk" | head -1)
[ -n "$APK_SRC" ] || { echo "构建失败：未找到 APK"; exit 1; }
APK_NAME="JKTV-${VERSION}-leanback-armeabi-v7a.apk"
mkdir -p dist
cp "$APK_SRC" "dist/$APK_NAME"
echo "产出: dist/$APK_NAME"

echo "== 3/7 生成 SHA256SUMS.txt =="
SHA=$(shasum -a 256 "dist/$APK_NAME" | cut -d' ' -f1)
printf '%s  %s\n' "$SHA" "$APK_NAME" > dist/SHA256SUMS.txt
echo "sha256 = $SHA"

echo "== 4/7 归档并生成 version.json（App 内自动更新清单） =="
mkdir -p apk
cp "dist/$APK_NAME" "apk/"
python3 - "$SHA" "$VERSION" "$APK_NAME" "$REPO" <<'PYEOF'
import json, sys, os
sha, version, apk, repo = sys.argv[1:5]
base = f"https://raw.githubusercontent.com/{repo}/main"
doc = {
    "name": version,
    "desc": "JKTV " + version,
    "code": None,
    "assets": [],
}
# 上游 Updater 期望单个对象 {name, desc, code}
doc["code"] = None
open('/tmp/jktv_vj.tmp','w').write(json.dumps(doc))
print("已生成版本模板")
PYEOF
python3 - "$SHA" "$VERSION" "$APK_NAME" "$REPO" "$VERSION_CODE" <<'PYEOF'
import json, sys
sha, version, apk, repo, code = sys.argv[1:6]
base = f"https://raw.githubusercontent.com/{repo}/main"
# 上游 Updater 读的是 {name, desc, code} 单对象；此处同时给出 releases 数组供扩展使用
single = {
    "name": version,
    "desc": f"JKTV {version}\n\nSHA-256: {sha}",
    "code": int(code),
}
open('apk/leanback.json','w',encoding='utf-8').write(json.dumps(single, ensure_ascii=False, indent=2) + "\n")
open('apk/mobile.json','w',encoding='utf-8').write(json.dumps(single, ensure_ascii=False, indent=2) + "\n")
# version.json 兼容 XPplay 风格的 releases 数组
releases = [{
    "tag_name": f"v{version}",
    "name": f"JKTV {version}",
    "draft": False,
    "prerelease": False,
    "assets": [
        {"name": apk, "browser_download_url": f"https://github.com/{repo}/releases/download/v{version}/{apk}"},
        {"name": "SHA256SUMS.txt", "browser_download_url": f"https://github.com/{repo}/releases/download/v{version}/SHA256SUMS.txt"},
    ],
}]
open('version.json','w',encoding='utf-8').write(json.dumps(releases, ensure_ascii=False, indent=2) + "\n")
print("version.json + apk/*.json 已更新")
PYEOF

echo "== 5/7 git 提交与打 tag =="
if [ ! -d .git ]; then
  git init -q
  git symbolic-ref HEAD refs/heads/main
fi
git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REMOTE"
git add -A
git commit -q -m "Release JKTV ${VERSION} (versionCode ${VERSION_CODE})" || echo "(无改动可提交)"
git tag -f "v${VERSION}" -m "JKTV ${VERSION}" >/dev/null

echo "== 6/7 推送 =="
if [ -n "${GH_TOKEN:-}" ]; then
  git push -q "https://x-access-token:${GH_TOKEN}@github.com/${REPO}.git" HEAD:main --tags
  echo "pushed to ${REMOTE}"
else
  echo "未提供 GH_TOKEN，跳过推送。"
fi

echo "== 7/7 GitHub Release =="
if [ -n "${GH_TOKEN:-}" ]; then
  API="https://api.github.com/repos/${REPO}/releases"
  CODE=$(curl -s -o /tmp/jktv_rel.json -w '%{http_code}' -X POST "$API" \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -d "{\"tag_name\":\"v${VERSION}\",\"name\":\"JKTV ${VERSION}\",\"body\":\"versionCode ${VERSION_CODE}\"}")
  if [ "$CODE" = "201" ]; then
    UPLOAD=$(python3 -c "import json;print(json.load(open('/tmp/jktv_rel.json'))['upload_url'].split('{')[0])")
    for f in "dist/$APK_NAME" dist/SHA256SUMS.txt; do
      curl -s -o /dev/null -w "uploaded $(basename $f) -> %{http_code}\n" -X POST "${UPLOAD}?name=$(basename $f)" \
        -H "Authorization: Bearer ${GH_TOKEN}" \
        -H "Content-Type: application/octet-stream" \
        --data-binary "@$f"
    done
  else
    echo "Release 创建返回 $CODE（可能已存在）"
  fi
else
  echo "未提供 GH_TOKEN，跳过 Release 创建。"
fi

echo
echo "发布完成：${APK_NAME} (${VERSION_NAME}, versionCode ${VERSION_CODE})"
ls -la "dist/$APK_NAME"
