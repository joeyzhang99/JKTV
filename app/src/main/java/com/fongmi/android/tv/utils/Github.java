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
 *   <li>{@code api.github.com} —— 国内不通；但<b>经镜像代理时是实时的</b>，故作首选</li>
 *   <li>{@code gh-proxy.com} —— 代理 API 可用；下载 7.2s/MB，明显快于 ghproxy.net</li>
 *   <li>{@code ghproxy.net} —— 代理 API 可用；下载 24.3s/MB</li>
 *   <li>{@code cdn.jsdelivr.net} —— DNS 在本机不通；电视可解析，保留兜底</li>
 * </ul>
 * 已移除的通道：{@code ghfast.top}（连接挂住不返回）、{@code ghproxy.cn}
 * （返回 200 但内容是安全验证 HTML 页）。
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

    /**
     * 加速前缀（仅作兜底）。
     *
     * <p>2026-10-20 实机对照发现：原版 FongMi 升级成功走的是<b>完全直连</b>
     * （api.github.com 查版本 + github.com/release CDN 下 APK，无任何镜像），
     * 实测国内移动网络下 API 0.4s 可用、CDN 下载 79–106 KB/s。
     * 因此 JKTV 也改为<b>直连优先</b>，镜像仅在前者失败时兜底。</p>
     */
    public static final List<String> PREFIXES = List.of(
            "https://gh-proxy.com/",
            "https://ghproxy.net/"
    );

    /** 通道索引：跟随系统（按内置顺序自动尝试）。 */
    public static final int CH_AUTO = -1;
    /** 通道索引：GitHub API 直连。 */
    public static final int CH_API = 0;
    /** 通道索引：gh-proxy.com 代理 API。 */
    public static final int CH_GH_PROXY_COM = 1;
    /** 通道索引：ghproxy.net 代理 API。 */
    public static final int CH_GHPROXY_NET = 2;
    /** 通道索引：gh-proxy.com 代理 raw。 */
    public static final int CH_GH_PROXY_COM_RAW = 3;
    /** 通道索引：ghproxy.net 代理 raw。 */
    public static final int CH_GHPROXY_NET_RAW = 4;
    /** 通道索引：jsDelivr。 */
    public static final int CH_JSD_ELIVR = 5;
    /** 通道索引：GitHub raw 直连（VPN/海外）。 */
    public static final int CH_RAW_DIRECT = 6;

    /** 通道显示名（与上面索引一一对应，UI 选择用）。 */
    public static final List<String> CHANNEL_NAMES = List.of(
            "GitHub API（直连）",
            "gh-proxy.com（API）",
            "ghproxy.net（API）",
            "gh-proxy.com（清单）",
            "ghproxy.net（清单）",
            "jsDelivr（CDN）",
            "GitHub raw（直连）"
    );

    /** 全部已知通道对应的清单 URL，索引与 CHANNEL_NAMES 一致。 */
    public static List<String> allChannelUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(RELEASES_API);                                   // 0
        urls.add(PREFIXES.get(0) + RELEASES_API);                 // 1
        urls.add(PREFIXES.get(1) + RELEASES_API);                 // 2
        urls.add(PREFIXES.get(0) + RAW_CATALOG);                  // 3
        urls.add(PREFIXES.get(1) + RAW_CATALOG);                  // 4
        urls.add(FALLBACK_CATALOG);                               // 5
        urls.add(RAW_CATALOG);                                    // 6
        return urls;
    }

    /**
     * 检查更新用地址序列。
     *
     * @param channel 用户选择的通道索引；CH_AUTO 表示跟随系统内置顺序
     */
    public static List<String> catalogUrls(int channel) {
        List<String> all = allChannelUrls();
        List<String> urls = new ArrayList<>();
        if (channel >= 0 && channel < all.size()) {
            // 用户指定通道优先，其后按内置顺序兜底（该通道失效时自动回退）
            urls.add(all.get(channel));
            for (String u : all) if (!urls.contains(u)) urls.add(u);
        } else {
            // 内置顺序（照 FongMi：直连优先）
            // 0 API直连 → 6 raw直连 → 1/2 代理API → 3/4 代理raw → 5 jsDelivr
            urls.add(all.get(0));
            urls.add(all.get(6));
            urls.add(all.get(1));
            urls.add(all.get(2));
            urls.add(all.get(3));
            urls.add(all.get(4));
            urls.add(all.get(5));
        }
        return urls;
    }

    /**
     * 检查更新时依次尝试的地址。
     *
     * <p>顺序与 XPplay 完全一致：GitHub API → 加速前缀代理的 raw → jsDelivr 兜底。</p>
     */
    /**
     * 检查更新时依次尝试的地址。
     *
     * <h3>为什么以 API 为主、raw 为辅</h3>
     * <p>2026-10-18 实测发现：国内加速镜像对 {@code raw.githubusercontent.com} 有
     * <b>长达数小时的缓存</b>（GitHub 源头已是 v1.2.4，两个镜像仍返回 v1.2.3），
     * 而代理 {@code api.github.com} 时返回的是<b>实时</b>结果。
     * 因此通道以 API 为主，raw 仅作兜底——它虽然新鲜度不可靠，但胜在路径短。</p>
     */
    public static List<String> catalogUrls() {
        return catalogUrls(CH_AUTO);
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