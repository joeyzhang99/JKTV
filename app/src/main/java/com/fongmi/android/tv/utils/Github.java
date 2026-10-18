package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * JKTV 更新源配置（与 XPplay 的 UpdateMirrors 保持一致的结构与策略）。
 *
 * <p>XPplay 的方案：{@code api.github.com} 优先，打不开时改读仓库内的
 * {@code version.json}（经加速前缀代理，实时无缓存），jsDelivr 兜底；
 * 下载 APK 时对同一 GitHub 地址依次套用加速前缀重试。</p>
 *
 * <h3>JKTV 的差异</h3>
 * <ul>
 *   <li>清单放在仓库根目录的 {@code version.json}（与 XPplay 一致），
 *       而非 {@code apk/leanback.json}；原因是根目录更短、更好排查。</li>
 *   <li>清单为 GitHub Releases 同构的数组格式（含 tag_name 与 assets），
 *       因此直接复用资产的 {@code browser_download_url}，
 *       不再由代码拼文件名——这从根上避免过「拼名与资产名不一致 → 404」。</li>
 * </ul>
 *
 * <h3>通道顺序（2026-10-18 中国移动、关闭 VPN 实测）</h3>
 * <ul>
 *   <li>{@code api.github.com} —— 国内不通</li>
 *   <li>{@code ghproxy.net} —— 0.5–1.3 秒，稳定</li>
 *   <li>{@code gh-proxy.com} —— 0.6 秒，波动</li>
 *   <li>{@code cdn.jsdelivr.net} —— DNS 不通（此前实测）</li>
 * </ul>
 * 失效的通道已移除：留着只会让用户白等一次超时。
 */
public class Github {

    public static final String REPO = "joeyzhang99/JKTV";
    public static final String BRANCH = "main";

    /** 仓库内的版本清单（与 GitHub Releases 同构的数组）。 */
    private static final String RAW_CATALOG =
            "https://raw.githubusercontent.com/" + REPO + "/" + BRANCH + "/version.json";

    /** jsDelivr 副本；分支引用有 12 小时缓存，只作最后兜底。 */
    private static final String FALLBACK_CATALOG =
            "https://cdn.jsdelivr.net/gh/" + REPO + "@" + BRANCH + "/version.json";

    /** GitHub Releases API（与 XPplay 一致，放最前，海外/VPN 下最快）。 */
    private static final String RELEASES_API =
            "https://api.github.com/repos/" + REPO + "/releases/latest";

    /** Release 资产直链前缀。 */
    private static final String RELEASES_BASE =
            "https://github.com/" + REPO + "/releases/download";

    /** 加速前缀，按尝试顺序。 */
    public static final List<String> PREFIXES = List.of(
            "https://ghproxy.net/",
            "https://gh-proxy.com/"
    );

    /**
     * 检查更新时依次尝试的地址。
     *
     * <p>顺序与 XPplay 完全一致：GitHub API → 加速前缀代理的 raw → jsDelivr 兜底。</p>
     */
    public static List<String> catalogUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(RELEASES_API);
        urls.add(PREFIXES.get(0) + RAW_CATALOG);
        urls.add(PREFIXES.get(1) + RAW_CATALOG);
        urls.add(FALLBACK_CATALOG);
        return urls;
    }

    /**
     * APK 下载地址的全部可用镜像，原始地址排第一。
     *
     * @param githubUrl 清单里的 browser_download_url（真实资产地址）
     */
    public static List<String> downloadUrls(String githubUrl) {
        List<String> urls = new ArrayList<>();
        urls.add(githubUrl);
        for (String prefix : PREFIXES) urls.add(prefix + githubUrl);
        return urls;
    }

    /** 检查更新时显示给用户的通道名。 */
    public static String channelLabel(String url) {
        if (url.startsWith(RELEASES_API)) return "GitHub";
        if (url.contains("jsdelivr")) return "jsDelivr";
        for (String prefix : PREFIXES) {
            if (url.startsWith(prefix)) return prefix.substring(prefix.indexOf("://") + 3).replaceAll("/$", "");
        }
        int i = url.indexOf("://");
        if (i < 0) return url;
        String rest = url.substring(i + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /** 展示给用户的手动镜像站点。 */
    public static List<String> mirrorHosts() {
        List<String> hosts = new ArrayList<>();
        for (String prefix : PREFIXES) hosts.add(prefix.substring(prefix.indexOf("://") + 3).replaceAll("/$", ""));
        return hosts;
    }

    /** 兼容旧调用：清单文件名（新版已统一为 version.json）。 */
    public static List<String> catalogUrls(String fileName) {
        return catalogUrls();
    }
}