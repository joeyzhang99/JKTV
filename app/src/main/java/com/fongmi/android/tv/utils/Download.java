package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.Future;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 带多镜像重试与断点续传的下载器。
 *
 * <p>GitHub 在国内常被阻断，因此下载时按 {@link #urls} 依次尝试：
 * 原始地址排第一，其后是各加速前缀代理的同一个地址。</p>
 *
 * <p><b>断点续传</b>：国内镜像速度实测仅 45–100 KB/s，64MB APK 需要十分钟以上，
 * 中途超时或网络抖动在所难免。已下载的部分保留在临时文件里，下次尝试从断点继续，
 * 而不是从头再来。服务端不支持 Range 时自动退回全量下载。</p>
 */
public class Download {

    /** 下载用 UA：GitHub CDN 对空 UA 请求会挂起。 */
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0.0.0 Mobile Safari/537.36";

    /** 断点续传用的临时文件后缀。 */
    private static final String PART = ".part";

    private final File file;
    private List<String> urls;
    private Callback callback;
    private Future<?> future;

    /** 本次下载实际使用的地址，取消时按它隔离请求。 */
    private String currentUrl;

    public static Download create(String url, File file) {
        return new Download(List.of(url), file);
    }

    public static Download create(List<String> urls, File file) {
        return new Download(urls, file);
    }

    public Download(List<String> urls, File file) {
        this.urls = urls;
        this.file = file;
    }

    /** 切换下载地址（更新时依据清单里的 tag 重建）。 */
    public Download reset(List<String> urls) {
        this.urls = urls;
        return this;
    }

    /** 设置取消标签（与上游 API 兼容）。 */
    public Download tag(String tag) {
        return this;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callback = callback;
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        if (future != null) future.cancel(true);
        if (currentUrl != null) OkHttp.cancel(currentUrl);
        future = null;
        return this;
    }

    private void doInBackground() {
        Exception last = null;
        for (String url : urls) {
            if (Thread.interrupted()) return;
            this.currentUrl = url;
            try {
                if (downloadOnce(url)) {
                    if (callback != null) App.post(() -> callback.success(file));
                    return;
                }
                last = new IOException("HTTP " + currentCode + " via " + hostOf(url));
            } catch (Exception e) {
                last = e;
            }
        }
        // 全部通道失败：保留断点文件，下次重试从断点继续（清掉会让用户从头下载）
        Path.clear(file);
        String msg = last != null ? last.getMessage() : null;
        if (callback != null) App.post(() -> callback.error(msg));
        else if (last != null) throw new RuntimeException(last.getMessage(), last);
    }

    private int currentCode = -1;

    private File part() {
        return new File(file.getParentFile(), file.getName() + PART);
    }

    /** 已下载字节数（用于断点）。 */
    private long downloaded() {
        File p = part();
        return p.exists() ? p.length() : 0L;
    }

    /**
     * 尝试从单个通道下载（支持断点续传）。
     *
     * @return true 表示完整下载成功
     */
    private boolean downloadOnce(String url) throws IOException {
        long resume = downloaded();
        Request.Builder builder = new Request.Builder().url(url);
        // JKTV 实测（2026-10-20）：GitHub Release 的 CDN 对无 UA 的请求会挂起超时，
        // 带浏览器 UA 才返回 206。原版 FongMi 能直连升级成功也是走这条路。
        builder.header("User-Agent", UA);
        builder.header("Accept", "*/*");
        if (resume > 0) builder.header("Range", "bytes=" + resume + "-");
        Request request = builder.build();

        try (Response res = OkHttp.downloadClient().newCall(request).execute()) {
            currentCode = res.code();
            // 服务端忽略 Range（返回 200 而非 206）时，从头写
            boolean append = resume > 0 && res.code() == 206;
            if (!append) resume = 0;

            if (res.code() == 429) {
                // 被限流：立即换下一通道（外层循环），保留断点
                return false;
            }
            if (!res.isSuccessful()) return false;

            try (InputStream is = new BufferedInputStream(res.body().byteStream());
                 RandomAccessFile raf = new RandomAccessFile(part(), "rw")) {
                raf.setLength(resume);
                raf.seek(resume);
                long total = resume + (long) getLength(res);

                byte[] buffer = new byte[32768];
                int read;
                long written = resume;
                while ((read = is.read(buffer)) != -1) {
                    if (Thread.interrupted()) return false;
                    raf.write(buffer, 0, read);
                    written += read;
                    // 总长未知（镜像返回 chunked）时仍要上报进度，否则界面永远停在 0%。
                    // 用已下载量折算一个"看起来在走"的百分比并封顶 99%——
                    // 真正下载完成时才会置 100，避免用户误以为卡死。
                    int pct;
                    if (total > 0) {
                        pct = (int) (written / total * 100.0);
                    } else {
                        long mb = written / (1024 * 1024);
                        pct = (int) Math.min(99, Math.max(1, mb * 100 / 12));
                    }
                    if (pct > 100) pct = 100;
                    final int progress = pct;
                    if (callback != null) {
                        final int pr = progress;
                        final long done = written;
                        final long tot = total;
                        App.post(() -> callback.progress(pr));
                        App.post(() -> callback.progress(done, tot));
                    }
                }
            }
        }

        // 校验完整性：能读出中央目录才算下载完成
        if (!isCompleteApk(part())) return false;

        // 原子替换
        Path.clear(file);
        if (!part().renameTo(file)) {
            copy(part(), file);
            Path.clear(part());
        }
        return true;
    }

    /** 完整性判定：ZIP 魔数 + 中央目录记录。 */
    private static boolean isCompleteApk(File f) {
        if (f == null || !f.exists() || f.length() < 1024) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] head = new byte[4];
            raf.readFully(head);
            if (head[0] != 'P' || head[1] != 'K') return false;
            long len = raf.length();
            long scan = Math.min(len, 1024L * 1024L);
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

    /** 取 URL 的 host，报错时用于定位是哪条通道失败。 */
    private static String hostOf(String url) {
        try {
            int i = url.indexOf("://");
            if (i < 0) return url;
            int start = i + 3;
            int end = url.indexOf('/', start);
            return end < 0 ? url.substring(start) : url.substring(start, end);
        } catch (Exception e) {
            return url;
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream is = new java.io.FileInputStream(from);
             FileOutputStream os = new FileOutputStream(to)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        }
    }

    /**
     * 响应总长度。
     *
     * <p>JKTV 实测：GitHub Release 经镜像代理时，302 跳转后的响应可能不带
     * {@code Content-Length}（chunked 传输）。旧实现此时返回 -1，
     * 导致进度回调被跳过，界面永远停在 0%——实际数据是在下载的。</p>
     *
     * <p>因此依次尝试：{@code Content-Length} → {@code Content-Range} 里的总长
     * （形如 {@code bytes 0-100/64914071}）。</p>
     */
    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            if (header != null) return Double.parseDouble(header);
        } catch (Exception ignored) {
        }
        try {
            String range = res.header("Content-Range"); // bytes 0-100/64914071
            if (range != null) {
                int slash = range.lastIndexOf('/');
                if (slash > 0 && slash < range.length() - 1) {
                    String total = range.substring(slash + 1).trim();
                    if (!"*".equals(total)) return Double.parseDouble(total);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    public interface Callback {

        void progress(int progress);

        /** 进度未知时的已下载字节数（用于显示「已下载 x MB」）。 */
        default void progress(long downloaded, long total) {
        }

        void error(String msg);

        void success(File file);
    }
}