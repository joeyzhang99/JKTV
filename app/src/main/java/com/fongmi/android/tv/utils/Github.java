package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * JKTV 更新源配置。
 *
 * GitHub 在国内（中国移动等）常被阻断，因此检查更新与下载 APK 都准备多条备用路径，
 * 按实测稳定性排序依次尝试。某一档失效只会多花一次连接超时，不影响后续档位。
 *
 * <h3>2026-10-18 中国移动实测（无 VPN，必须返回合法 JSON 而非 HTML 验证页）</h3>
 * <ul>
 *   <li>raw.githubusercontent.com 直连 —— <b>不通</b></li>
 *   <li>gh-proxy.com        —— ✅ 0.64s，返回正确 JSON</li>
 *   <li>ghproxy.net         —— ✅ 1.31s，但<b>有缓存</b>（曾返回上一版清单）</li>
 *   <li>ghproxy.cn          —— ❌ <b>返回 672 安全验证 HTML 页</b>（HTTP 200 但非 JSON，陷阱）</li>
 *   <li>cdn.jsdelivr.net    —— <b>不通</b>（DNS 解析失败）</li>
 *   <li>ghfast.top          —— <b>不通</b>（连接挂住不返回）</li>
 * </ul>
 *
 * <b>重要教训</b>：不能只看 HTTP 状态码。ghproxy.cn 返回 200 但内容是安全验证页，
 * 因此 {@link Updater} 在解析 JSON 失败后会继续尝试下一通道，而不是当作成功。
 */
public class Github {

    /** JKTV 仓库。 */
    public static final String REPO = "joeyzhang99/JKTV";

    /** 仓库内清单与 APK 的目录。 */
    public static final String RAW_BASE =
            "https://raw.githubusercontent.com/" + REPO + "/main/apk";

    /** GitHub Releases 资产前缀。 */
    public static final String RELEASES_BASE =
            "https://github.com/" + REPO + "/releases/download";

    /** 检查更新用的加速前缀，按实测可用性排序。 */
    public static final List<String> PREFIXES = List.of(
            "https://gh-proxy.com/",
            "https://ghproxy.net/"
    );

    /**
     * 下载 APK 用的加速前缀。
     *
     * <p>与 {@link #PREFIXES} 分开维护，因为实测各镜像在大文件下载上差异极大：</p>
     * <ul>
     *   <li><b>ghproxy.net</b> —— ✅ 唯一可靠：完整下载 39MB/71s，SHA-256 与清单一致</li>
     *   <li>ghproxy.cn    —— ❌ 大文件返回 672 安全验证 HTML 页（Range 小请求正常，诱骗性强）</li>
     *   <li>gh-proxy.com  —— ❌ 39MB 下载 400s 未完成（仅下到 10MB 即断）</li>
     * </ul>
     *
     * <p>2026-10-18 中国移动实测（releases/download，39MB APK）。</p>
     */
    public static final List<String> DOWNLOAD_PREFIXES = List.of(
            "https://ghproxy.net/"
    );

    /** 检查更新用的清单文件名（不含扩展名），如 leanback。 */
    private static String rawUrl(String fileName) {
        return RAW_BASE + "/" + fileName + ".json";
    }

    /** 单通道：GitHub raw 直链（VPN 下可用）。 */
    public static String getJson(String fileName) {
        return rawUrl(fileName);
    }

    /**
     * 构造 APK 下载地址。
     *
     * <p><b>不要硬编码 tag</b>：曾经因硬编码导致「检查更新读到新版本、下载却指向旧 tag」
     * 而静默 404。现在优先用清单里的 {@code url} 字段（发布时由脚本生成，
     * 必然与实际 Release 一致），缺失时才回退到按约定拼接。</p>
     */
    public static String getApk(String apkName, String tag) {
        String t = (tag == null || tag.isEmpty()) ? releaseTag() : tag;
        return RELEASES_BASE + "/" + t + "/" + apkName + ".apk";
    }

    /** 兜底 tag。仅在清单未提供 url 时使用。 */
    public static String releaseTag() {
        return "v1.2.0";
    }

    /**
     * 检查更新时依次尝试的全部清单地址。
     * 第一条是 GitHub 直链（VPN / 海外网络下可用），其后是三个国内加速镜像。
     */
    public static List<String> catalogUrls(String fileName) {
        String raw = rawUrl(fileName);
        List<String> urls = new ArrayList<>();
        urls.add(raw);
        for (String prefix : PREFIXES) urls.add(prefix + raw);
        return urls;
    }

    /**
 * 由 Release 资产名反推下载 URL。
 *
 * <p><b>JKTV 修复</b>：原实现把文件名拼成 {@code leanback-armeabi_v7a.apk}，
 * 而 Release 里的实际资产名是 {@code JKTV-V1.2.2-leanback-armeabi-v7a.apk}
 * （带版本号前缀），导致 404。这里由 tag 推出完整文件名。</p>
 */
public static List<String> downloadUrls(String flavorAbi, String tag) {
        String t = (tag == null || tag.isEmpty()) ? releaseTag() : tag;
        // 资产名规则：JKTV-{tag去掉v}-{flavorAbi}.apk
        String version = t.startsWith("v") ? t.substring(1) : t;
        String asset = "JKTV-" + version + "-" + flavorAbi + ".apk";
        String raw = RELEASES_BASE + "/" + t + "/" + asset;
        List<String> urls = new ArrayList<>();
        urls.add(raw);
        for (String prefix : DOWNLOAD_PREFIXES) urls.add(prefix + raw);
        return urls;
    }

    /**
     * 由清单里的完整 URL 构造多通道下载地址。
     *
     * <p>优先使用发布时写入清单的 {@code url}：它由发版脚本生成，必然与实际
     * Release 资产一致，从根本上避免"代码拼的名字与资产名不一致"导致 404。</p>
     */
    public static List<String> downloadUrlsFromManifest(String url, String flavorAbi, String tag) {
        if (url == null || url.trim().isEmpty()) return downloadUrls(flavorAbi, tag);
        String raw = url.trim();
        // 只接受可信来源，避免清单被篡改指向任意地址
        if (!raw.startsWith(RELEASES_BASE)) return downloadUrls(flavorAbi, tag);
        List<String> urls = new ArrayList<>();
        urls.add(raw);
        for (String prefix : DOWNLOAD_PREFIXES) urls.add(prefix + raw);
        return urls;
    }

    /** Releases 资产地址（备用下载路径）。 */
    public static String releaseAsset(String tag, String asset) {
        return RELEASES_BASE + "/" + tag + "/" + asset;
    }

    /** 检查更新时显示给用户的通道名。 */
    public static String channelLabel(String url) {
        for (String prefix : PREFIXES) {
            if (url.startsWith(prefix)) return prefix.substring(prefix.indexOf("://") + 3).replaceAll("/$", "");
        }
        if (url.startsWith(RELEASES_BASE)) return "GitHub Releases";
        return "GitHub";
    }

    /** 展示给用户的手动镜像站点列表。 */
    public static List<String> mirrorHosts() {
        List<String> hosts = new ArrayList<>();
        for (String prefix : PREFIXES) hosts.add(prefix.substring(prefix.indexOf("://") + 3).replaceAll("/$", ""));
        return hosts;
    }
}
