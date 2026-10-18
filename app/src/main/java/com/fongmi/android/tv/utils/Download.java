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
                last = new IOException("HTTP " + currentCode);
            } catch (Exception e) {
                last = e;
            }
        }
        // 全部通道失败：清理断点文件，回报错误
        Path.clear(file);
        Path.clear(part());
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
        if (resume > 0) builder.header("Range", "bytes=" + resume + "-");
        Request request = builder.build();

        try (Response res = OkHttp.downloadClient().newCall(request).execute()) {
            currentCode = res.code();
            // 服务端忽略 Range（返回 200 而非 206）时，从头写
            boolean append = resume > 0 && res.code() == 206;
            if (!append) resume = 0;

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
                    if (total <= 0) continue;
                    int pct = (int) (written / total * 100.0);
                    if (pct > 100) pct = 100;
                    final int progress = pct;
                    if (callback != null) App.post(() -> callback.progress(progress));
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

    private static void copy(File from, File to) throws IOException {
        try (InputStream is = new java.io.FileInputStream(from);
             FileOutputStream os = new FileOutputStream(to)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        }
    }

    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Double.parseDouble(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress);

        void error(String msg);

        void success(File file);
    }
}