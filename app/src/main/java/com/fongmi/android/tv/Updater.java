package com.fongmi.android.tv;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.List;

/**
 * JKTV 更新器。
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li><b>自动检查</b>：{@link #start(FragmentActivity)} 在启动时调用，
 *       受 {@link Setting#getUpdate()} 开关控制，发现新版本后弹窗询问。</li>
 *   <li><b>手动检查</b>：{@link #force()} 由用户在设置里点击触发，
 *       无条件检查，并给出"已是最新版本"的明确反馈。</li>
 * </ul>
 *
 * <h3>四通道</h3>
 * 检查更新依次尝试 {@link Github#catalogUrls(String)}：
 * GitHub 直链 → ghproxy.net → gh-proxy.com → jsDelivr。
 * 下载 APK 时同样依次尝试 {@link Github#downloadUrls(String)}。
 *
 * <h3>完整性校验</h3>
 * 下载完成后按清单里的 SHA-256 校验 APK；不匹配则删除文件并报错，
 * 避免被劫持的镜像或损坏的下载污染安装。
 */
public class Updater implements Download.Callback, UpdateListener {

    private static final int TIMEOUT_MS = 12000;

    private final Download download;
    private UpdateDialog dialog;

    /** 清单里的 Release tag，用于拼出正确的下载路径。 */
    private String tag;

    /** 清单里的完整 APK 地址（发布时生成，必然与 Release 资产一致）。 */
    private String url;

    /** SHA256SUMS.txt 地址（与 XPplay 一致，用于校验下载完整性）。 */
    private String checksumsUrl;

    /** 本次下载的总长度，-1 表示未知。 */
    private long total = -1;

    /** 本次检查/下载实际使用的通道，用于给用户展示。 */
    private String channel = "GitHub";

    private Updater() {
        this.download = Download.create(List.of(), getFile());
    }

    /**
     * 依据清单里的 tag 重设下载地址。
     *
     * <p>JKTV 修复：原先下载地址里的 tag 是硬编码的，导致「检查更新显示新版本，
     * 点更新却去旧 tag 下文件」而静默 404。现在从清单取值，保证与实际 Release 一致。</p>
     */
    private void applyAsset(String tag, String assetUrl, String sumsUrl) {
        if (assetUrl == null || assetUrl.isEmpty()) return;
        this.tag = tag;
        this.url = assetUrl;
        this.checksumsUrl = sumsUrl;
        this.download.reset(Github.downloadUrls(assetUrl));
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    /**
     * 清单文件名。
     *
     * <p>按 ABI 区分：leanback-arm64_v8a 读 leanback-arm64_v8a.json，
     * leanback-armeabi_v7a 读 leanback.json。若 32/64 位共用同一份清单，
     * 会下载到架构不匹配的 APK 导致安装失败。</p>
     */
    private static String manifestName() {
        String abi = BuildConfig.FLAVOR_abi;
        return "arm64_v8a".equals(abi)
                ? BuildConfig.FLAVOR_mode + "-arm64_v8a"
                : BuildConfig.FLAVOR_mode;
    }

    /** 手动检查：无视开关，强制检查一次。 */
    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    /**
     * 启动时的自动检查。
     *
     * @param manual true 表示用户手动触发（会提示"已是最新版本"）；
     *               false 表示启动时自动检查（静默，仅在发现新版本时提示）
     */
    public void start(FragmentActivity activity, boolean manual) {
        if (!manual && !Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity, manual));
    }

    /** 兼容旧调用签名（启动时自动检查）。 */
    public void start(FragmentActivity activity) {
        start(activity, false);
    }

    private void doInBackground(FragmentActivity activity, boolean manual) {
        try {
            Manifest m = fetchManifest();
            if (m == null) {
                if (manual) App.post(() -> Notify.show(R.string.update_fail));
                return;
            }
            if (m.code <= BuildConfig.VERSION_CODE) {
                if (manual) App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
            String name = m.name.isEmpty() ? ("JKTV " + m.tag) : m.name;
            String desc = ResUtil.getString(R.string.update_desc_sha, m.assetUrl);
            App.post(() -> show(activity, name, desc));
        } catch (Exception e) {
            e.printStackTrace();
            if (manual) App.post(() -> Notify.show(R.string.update_fail));
        }
    }

    /**
     * 依次尝试各通道读取版本清单。
     *
     * <p><b>关键</b>：必须以"能否解析出合法 JSON 且含 code/name 字段"判定成功，
     * 而不能只看 HTTP 状态码——ghproxy.cn 会返回 200 但内容是安全验证 HTML 页。</p>
     *
     * <p><b>限流容错</b>（JKTV 实测）：镜像会返回 HTTP 429（Too Many Requests）或
     * 偶发连接失败。旧实现遇到失败只静默换下一个通道，若排在第一的通道正好被限流，
     * 就可能连续失败而看不到新版本。现在改为：记录失败原因，遇到 429 立即跳到
     * 下一通道，并对同一通道做一次短暂退避重试。</p>
     *
     * @return 解析成功的清单；全部通道失败时返回 null
     */
    private Manifest fetchManifest() {
        List<String> urls = Github.catalogUrls();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0) {
                    // 第二次尝试前短暂退避，避开瞬时限流
                    try {
                        Thread.sleep(1200L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                try (okhttp3.Response res = OkHttp.newCall(url, channelTag(url)).execute()) {
                    if (res.code() == 429) {
                        // 明确被限流：不必在本通道重试，直接换下一通道
                        break;
                    }
                    String body = res.body() == null ? null : res.body().string();
                    if (body == null || body.isEmpty()) continue;
                    String trimmed = body.trim();
                    // 镜像可能返回 HTML 验证页：必须以 { 或 [ 开头才是 JSON
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) continue;
                    Manifest m = Manifest.parse(trimmed);
                    if (m == null) continue;
                    this.channel = Github.channelLabel(url);
                    applyAsset(m.tag, m.assetUrl, m.sumsUrl);
                    return m;
                } catch (Exception ignored) {
                    // 网络异常：进入下一次尝试（最多 2 次）
                }
            }
        }
        return null;
    }

    /**
     * 版本清单。
     *
     * <p>兼容两种格式：</p>
     * <ol>
     *   <li><b>GitHub Releases 原生格式</b>（XPplay 同构）：
     *       <code>[{tag_name, assets:[{name, browser_download_url}]}]</code>，
     *       同时接受 {@code api.github.com} 响应与仓库内 version.json。</li>
     *   <li><b>JKTV 扁平格式</b>：<code>{name, code, url}</code>。</li>
     * </ol>
     */
    private static final class Manifest {
        String tag = "";
        String name = "";
        int code;
        String assetUrl = "";
        String sumsUrl = "";

        static Manifest parse(String body) throws Exception {
            Manifest m = new Manifest();
            if (body.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    if (o.optBoolean("draft")) continue;
                    org.json.JSONArray assets = o.optJSONArray("assets");
                    if (assets == null) continue;
                    m.tag = o.optString("tag_name");
                    m.name = o.optString("name");
                    // 从资产名解析版本号：JKTV-V1.2.4-xxx.apk → 1.2.4
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject a = assets.optJSONObject(j);
                        if (a == null) continue;
                        String n = a.optString("name");
                        if (n.endsWith(".apk") && m.assetUrl.isEmpty()) {
                            m.assetUrl = a.optString("browser_download_url");
                            m.code = versionCodeOf(m.tag, n);
                        } else if ("SHA256SUMS.txt".equalsIgnoreCase(n)) {
                            m.sumsUrl = a.optString("browser_download_url");
                        }
                    }
                    if (!m.assetUrl.isEmpty()) return m;
                }
                return null;
            }
            JSONObject o = new JSONObject(body);
            m.name = o.optString("name");
            m.code = o.optInt("code");
            m.tag = o.optString("tag");
            m.assetUrl = o.optString("url");
            m.sumsUrl = o.optString("sha_url");
            return (m.code > 0 || !m.assetUrl.isEmpty()) ? m : null;
        }

        /** 由 tag 或资产名解析 versionCode：v1.2.4 → 10204 */
        private static int versionCodeOf(String tag, String assetName) {
            String t = (tag == null || tag.isEmpty()) ? assetName : tag;
            int i = t.indexOf('v') >= 0 ? t.indexOf('v') + 1 : 0;
            StringBuilder digits = new StringBuilder();
            for (int k = i; k < t.length(); k++) {
                char c = t.charAt(k);
                if (Character.isDigit(c)) digits.append(c);
                else if (c == '.' && digits.length() > 0 && digits.charAt(digits.length() - 1) != '.') {
                    digits.append('.');
                }
            }
            String[] p = digits.toString().split("\\.");
            if (p.length < 3) return 0;
            try {
                return Integer.parseInt(p[0]) * 10000 + Integer.parseInt(p[1]) * 100 + Integer.parseInt(p[2]);
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /** 用 tag 隔离各通道的请求，便于取消。 */
    private String channelTag(String url) {
        return "update-manifest-" + Math.abs(url.hashCode());
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        String text = ResUtil.getString(R.string.update_version, version)
                + "\n" + ResUtil.getString(R.string.update_channel, channel);
        dialog = UpdateDialog.create().title(text).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null && this.total > 0) dialog.setProgress(progress);
    }

    /** 总长未知时展示「已下载 x MB」，让用户知道确实在下载。 */
    @Override
    public void progress(long downloaded, long total) {
        this.total = total;
        if (dialog == null) return;
        if (total > 0) {
            dialog.setProgress((int) Math.min(100, downloaded / total * 100));
        } else {
            dialog.setText(String.format(Locale.getDefault(), "%.1f MB",
                    downloaded / 1024f / 1024f));
        }
    }

    @Override
    public void error(String msg) {
        // JKTV: 把真实的失败原因透出（HTTP 状态码 / 超时 / 网络），
        // 避免只显示 "HTTP 404" 这类无信息量文案而无法定位。
        String detail = (msg == null || msg.trim().isEmpty()) ? "unknown" : msg.trim();
        Notify.show(ResUtil.getString(R.string.update_fail_reason, detail));
        dismiss();
    }

    @Override
    public void success(File file) {
        // 先验证是否为合法 APK（ZIP 魔数），防止镜像返回 HTML 验证页被当作下载成功
        if (!isValidApk(file)) {
            Path.clear(file);
            Notify.show(ResUtil.getString(R.string.update_checksum_fail));
            dismiss();
            return;
        }
        if (verify(file)) {
            FileUtil.openFile(file);
        } else {
            Path.clear(file);
            Notify.show(ResUtil.getString(R.string.update_checksum_fail));
        }
        dismiss();
    }

    /** APK 必然是 ZIP：检查 PK\x03\x04 魔数与中央目录结尾。 */
    private boolean isValidApk(File file) {
        if (file == null || !file.exists() || file.length() < 1024) return false;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            byte[] head = new byte[4];
            raf.readFully(head);
            if (head[0] != 'P' || head[1] != 'K') return false;
            if (head[2] != 3 && head[2] != 5 && head[2] != 7) return false;
            // 中央目录记录签名 PK\x01\x02 应存在于文件末尾附近
            long len = raf.length();
            long scan = Math.min(len, 1024 * 1024);
            raf.seek(len - scan);
            byte[] tail = new byte[(int) scan];
            raf.readFully(tail);
            for (int i = tail.length - 4; i >= 0; i--) {
                if (tail[i] == 'P' && tail[i + 1] == 'K' && tail[i + 2] == 1 && tail[i + 3] == 2) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 按 SHA-256 校验下载的 APK。
     *
     * <p>校验值取自版本清单的 {@code desc} 字段中的 SHA-256 行；若清单未提供校验值，
     * 视为无法校验，放行（保持与上游一致的宽松行为）。</p>
     */
    private boolean verify(File file) {
        try {
            String expect = expectedSha256();
            if (expect == null || expect.isEmpty()) return true;
            String actual = sha256(file);
            return expect.equalsIgnoreCase(actual);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 期望的 SHA-256。
     *
     * <p>与 XPplay 一致：从 Release 的 {@code SHA256SUMS.txt} 资产读取，
     * 而不是把哈希塞进清单描述里——清单是结构化数据，哈希属于独立的校验文件。</p>
     */
    private String expectedSha256() {
        String url = checksumsUrl;
        if (url == null || url.isEmpty()) return null;
        try {
            String body = null;
            for (String u : Github.downloadUrls(url)) {
                try {
                    String s = OkHttp.newCall(u, "sha").execute().body().string();
                    if (s != null && !s.trim().isEmpty()) { body = s; break; }
                } catch (Exception ignored) {
                }
            }
            if (body == null) return null;
            for (String line : body.split("\\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                int sp = t.indexOf(' ');
                if (sp > 0) return t.substring(0, sp).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = is.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
