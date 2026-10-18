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

    /** 本次检查/下载实际使用的通道，用于给用户展示。 */
    private String channel = "GitHub";

    private Updater() {
        this.download = Download.create(Github.downloadUrls(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi), getFile());
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    /** APK 单通道地址（GitHub 直链），保留给需要单地址的调用方。 */
    private String getApk() {
        return Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
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
            JSONObject object = fetchManifest();
            if (object == null) {
                if (manual) App.post(() -> Notify.show(R.string.update_fail));
                return;
            }
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");
            if (code <= BuildConfig.VERSION_CODE) {
                if (manual) App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
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
     * @return 解析成功的清单；全部通道失败时返回 null
     */
    private JSONObject fetchManifest() {
        List<String> urls = Github.catalogUrls(manifestName());
        for (String url : urls) {
            try {
                String body = OkHttp.newCall(url, channelTag(url)).execute().body().string();
                if (body == null || body.isEmpty()) continue;
                // 镜像可能返回 HTML 验证页：trim 后必须以 { 开头才是 JSON
                String trimmed = body.trim();
                if (!trimmed.startsWith("{")) continue;
                JSONObject object = new JSONObject(trimmed);
                if (object.has("code") && object.has("name")) {
                    this.channel = Github.channelLabel(url);
                    return object;
                }
            } catch (Exception ignored) {
                // 该通道失败，尝试下一个
            }
        }
        return null;
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
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        if (verify(file)) {
            FileUtil.openFile(file);
        } else {
            Path.clear(file);
            Notify.show(ResUtil.getString(R.string.update_checksum_fail));
        }
        dismiss();
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

    private String expectedSha256() {
        try {
            JSONObject object = fetchManifest();
            if (object == null) return null;
            String desc = object.optString("desc");
            int idx = desc.indexOf("SHA-256:");
            if (idx < 0) return null;
            String value = desc.substring(idx + 8).trim();
            int end = value.indexOf(' ');
            return end > 0 ? value.substring(0, end) : value;
        } catch (Exception e) {
            return null;
        }
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
