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
import java.util.List;
import java.util.concurrent.Future;

import okhttp3.Response;

/**
 * 带多镜像重试的下载器。
 *
 * <p>GitHub 在国内常被阻断，因此下载时按 {@link #urls} 依次尝试：
 * 原始地址排第一，其后是各加速前缀代理的同一个地址。某一档失败会自动切到下一档，
 * 全部失败才回调 {@link Callback#error(String)}。</p>
 */
public class Download {

    private final File file;
    private final List<String> urls;
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
            try (Response res = OkHttp.newCall(url, url).execute()) {
                if (!res.isSuccessful()) {
                    last = new IOException("HTTP " + res.code());
                    continue;
                }
                download(res.body().byteStream(), getLength(res));
                if (callback != null) App.post(() -> callback.success(file));
                return;
            } catch (Exception e) {
                last = e;
                Path.clear(file);
            }
        }
        Path.clear(file);
        String msg = last != null ? last.getMessage() : null;
        if (callback != null) App.post(() -> callback.error(msg));
        else if (last != null) throw new RuntimeException(last.getMessage(), last);
    }

    private void download(InputStream is, double length) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long totalBytes = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                if (length <= 0) continue;
                int progress = (int) (totalBytes / length * 100.0);
                if (callback != null) App.post(() -> callback.progress(progress));
            }
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
