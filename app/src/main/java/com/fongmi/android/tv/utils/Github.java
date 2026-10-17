package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * JKTV 更新源配置。
 *
 * GitHub 在国内（中国移动等）常被阻断，因此检查更新与下载 APK 都准备多条备用路径，
 * 按"相对稳定"排序依次尝试。某一档失效只会多花一次连接超时，不影响后续档位。
 *
 * 通道顺序（与 XPplay 的实测结论一致）：
 *   1. GitHub raw / releases 直链   —— VPN 或海外网络下最快
 *   2. ghproxy.net                  —— 上海移动实测稳定 1–2 秒
 *   3. gh-proxy.com                 —— 正常但波动（0.5–9 秒）
 *   4. cdn.jsdelivr.net             —— 兜底；分支引用有 12 小时缓存，排最后
 *
 * 已移除的通道：ghfast.top（连接挂住不返回，会让用户白等一个超时）。
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

    /** 加速前缀，按尝试顺序。 */
    public static final List<String> PREFIXES = List.of(
            "https://ghproxy.net/",
            "https://gh-proxy.com/"
    );

    /** 检查更新用的清单文件名（不含扩展名），如 leanback。 */
    private static String rawUrl(String fileName) {
        return RAW_BASE + "/" + fileName + ".json";
    }

    /** 单通道：GitHub raw 直链（VPN 下可用）。 */
    public static String getJson(String fileName) {
        return rawUrl(fileName);
    }

    /** APK 单通道地址（GitHub raw 直链）。 */
    public static String getApk(String apkName) {
        return RAW_BASE + "/" + apkName + ".apk";
    }

    /**
     * 检查更新时依次尝试的全部清单地址。
     * 第一条是 GitHub 直链，其后是加速前缀代理的同一个地址，最后是 jsDelivr 兜底。
     */
    public static List<String> catalogUrls(String fileName) {
        String raw = rawUrl(fileName);
        List<String> urls = new ArrayList<>();
        urls.add(raw);
        for (String prefix : PREFIXES) urls.add(prefix + raw);
        // jsDelivr：分支引用有 12 小时缓存，刚发版时可能仍是旧版本，只作最后兜底
        urls.add("https://cdn.jsdelivr.net/gh/" + REPO + "@main/apk/" + fileName + ".json");
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
        for (String prefix : PREFIXES) urls.add(prefix + raw);
        return urls;
    }

    /** Releases 资产地址（备用下载路径）。 */
    public static String releaseAsset(String tag, String asset) {
        return RELEASES_BASE + "/" + tag + "/" + asset;
    }

    /** 检查更新时显示给用户的通道名。 */
    public static String channelLabel(String url) {
        if (url.contains("jsdelivr")) return "jsDelivr";
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
        hosts.add("cdn.jsdelivr.net");
        return hosts;
    }
}
