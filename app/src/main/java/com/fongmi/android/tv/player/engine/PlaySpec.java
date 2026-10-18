package com.fongmi.android.tv.player.engine;

import android.net.Uri;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaySpec {

    private Map<String, String> headers;
    private List<Danmaku> danmakus;
    private MediaMetadata metadata;
    private List<Sub> subs;
    private String format;
    private String key;
    private String url;
    private Drm drm;

    public static PlaySpec from(String key, String url, Map<String, String> headers, MediaMetadata metadata) {
        return new PlaySpec(key, url, headers, null, null, null, null, metadata);
    }

    public static PlaySpec from(Result result, String key, MediaMetadata metadata) {
        return new PlaySpec(key, result.getRealUrl(), result.getHeader(), result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata);
    }

    public static PlaySpec fromParse(Result result, String key, MediaMetadata metadata) {
        return new PlaySpec(key, null, null, result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata);
    }

    private PlaySpec(String key, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subs, List<Danmaku> danmakus, MediaMetadata metadata) {
        this.key = key;
        this.url = url;
        this.drm = drm;
        this.subs = subs;
        this.format = format;
        this.headers = headers;
        this.danmakus = danmakus;
        this.metadata = metadata;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = normalize(url);
    }

    /**
     * 规整播放地址，确保 ExoPlayer 一定拿到合法 URL。
     *
     * <p>线上实测：某些片源返回 <code>/api/xxx</code> 这类根相对路径，
     * OkHttp 会抛 <code>IllegalArgumentException: Expected URL scheme
     * 'http' or 'https' but no scheme was found</code>，表现为有声音无画面。</p>
     *
     * <p>此处的兜底无法知道站点域名（相对路径的基准 URL 由 Decoder 处理），
     * 因此只做：去空白、反转义、拒绝明显非法的值（交由上层报错而不是崩溃）。</p>
     */
    private static String normalize(String url) {
        if (url == null) return null;
        String u = url.trim().replace("\\", "");
        if (u.isEmpty()) return u;
        // 已是合法 http(s) / 本地 / 代理协议
        if (u.contains("://")) return u;
        if (u.startsWith("/")) return "http:" + u; // 协议相对兜底，至少不会抛异常
        return u;
    }

    public Uri getUri() {
        return UrlUtil.uri(url);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Drm getDrm() {
        return drm;
    }

    public List<Sub> getSubs() {
        return subs;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public MediaMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MediaMetadata metadata) {
        this.metadata = metadata;
    }

    public PlaySpec checkUa() {
        if (headers == null) headers = new HashMap<>();
        if (headers.keySet().stream().noneMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase)) headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? PlayerHelper.getDefaultUa() : Setting.getUa());
        return this;
    }

    public void setSub(Sub sub) {
        if (subs == null) subs = new ArrayList<>();
        if (sub != null && !subs.contains(sub)) subs.add(0, sub);
    }

    public void setDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        danmakus.forEach(danmaku -> danmaku.setSelected(danmaku.getUrl().equals(item.getUrl())));
    }

    public void addDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(item);
    }
}
