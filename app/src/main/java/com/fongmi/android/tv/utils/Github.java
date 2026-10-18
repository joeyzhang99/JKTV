package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * JKTV 更新源配置。
 *
 * GitHub 在国内（中国移动等）常被阻断，因此检查更新与下载 APK 都准备多条备用路径，
 * 按实测稳定性排序依次尝试。某一档失效只会多花一次连接超时，不影响后续档位。
 *
 * <h3>2026-10-18 中国移动实测（无 VPN）</h3>
 * <ul>
 *   <li>raw.githubusercontent.com 直连 —— <b>不通</b></li>
 *   <li>ghproxy.cn          —— 0.79s ✅ 最快</li>
 *   <li>gh-proxy.com        —— 0.64s ✅</li>
 *   <li>ghproxy.net         —— 1.31s ✅</li>
 *   <li>cdn.jsdelivr.net    —— <b>不通</b>（DNS 解析失败）</li>
 *   <li>ghfast.top          —— <b>不通</b>（连接挂住不返回）</li>
 * </ul>
 *
 * 因此通道顺序为：两个最快的国内镜像在前，GitHub 直链保留给 VPN/海外网络，
 * 不通的 ghfast.top 与 jsDelivr 已移除（留着只会让用户白等超时）。
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

    /** 加速前缀，按尝试顺序（实测越快越靠前）。 */
    public static final List<String> PREFIXES = List.of(
            "https://ghproxy.cn/",
            "https://ghproxy.net/",
            "https://gh-proxy.com/"
    );

    /**
     * 下载 APK 用的加速前缀。
     *
     * <p>与 {@link #PREFIXES} 不同：各镜像对 GitHub Releases 资产的支持不一致
     * （gh-proxy.com 对 releases/download 返回 404），因此单独维护一份经验证可用的列表。</p>
     *
     * <p>2026-10-18 中国移动实测（releases/download）：ghproxy.cn ✅ / ghproxy.net ✅ / gh-proxy.com ❌</p>
     */
    public static final List<String> DOWNLOAD_PREFIXES = List.of(
            "https://ghproxy.cn/",
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

    /** APK 单通道地址（GitHub Releases 直链）。 */
    public static String getApk(String apkName) {
        return RELEASES_BASE + "/" + releaseTag() + "/" + apkName + ".apk";
    }

    /** 当前发布 tag，与 version.json 保持一致。 */
    public static String releaseTag() {
        return "v1.1.1";
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
     * 同一个 APK 下载地址的全部可用镜像，原始地址排第一。
     * 下载失败时由调用方依次重试。
     */
    public static List<String> downloadUrls(String apkName) {
        String raw = getApk(apkName);
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
