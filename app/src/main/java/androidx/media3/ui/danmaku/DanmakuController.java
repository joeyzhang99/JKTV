package androidx.media3.ui.danmaku;

import android.net.Uri;

import okhttp3.OkHttpClient;

/**
 * JKTV: 上游将弹幕渲染封装为不公开的预编译 AAR（androidx.media3.ui.danmaku）。
 * 此处重建其控制器接口约定。
 *
 * V1.0.0 未内置渲染实现，调用方（PlayerManager / 设置页）保持完整 API，
 * 便于后续接入开源渲染库（如 DanmakuFlameMaster）时直接提供实现，
 * 无需改动上层代码。
 */
public interface DanmakuController {

    /** 注入下载弹幕数据用的 HTTP 客户端。 */
    void setOkHttpClient(OkHttpClient client);

    /** 应用样式配置。 */
    void setConfig(DanmakuConfig config);

    /** 开关弹幕显示。 */
    void setEnabled(boolean enabled);

    /** 设置弹幕数据源（远程 URL / 本地文件）。 */
    void setDataSource(Uri uri);

    /** 立即发送一条自定义弹幕。 */
    void sendNow(String text);

    /** 清空已加载的弹幕。 */
    void clearItems();

    /** 释放资源。 */
    void release();
}
