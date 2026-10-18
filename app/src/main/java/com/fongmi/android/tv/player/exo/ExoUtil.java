package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


public class ExoUtil {

    public static void setPlayerView(PlayerView view) {
        // JKTV: 上游 view.setRender(int) 属私有 PlayerView 扩展（渲染模式切换），官方无此 API，已移除。
        view.getSubtitleView().setStyle(getCaptionStyle());
        view.getSubtitleView().setApplyEmbeddedStyles(true);
        view.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (PlayerSetting.getSubtitlePosition() != 0) view.getSubtitleView().setBottomPaddingFraction(PlayerSetting.getSubtitlePosition());
        if (PlayerSetting.getSubtitleTextSize() != 0) view.getSubtitleView().setFractionalTextSize(PlayerSetting.getSubtitleTextSize());
    }

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        ExoPlayer player = new ExoPlayer.Builder(App.get()).setLoadControl(buildLoadControl()).setTrackSelector(buildTrackSelector()).setRenderersFactory(buildRenderersFactory(getRenderMode(decode))).setMediaSourceFactory(buildMediaSourceFactory()).build();
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static MediaItem getMediaItem(PlaySpec spec, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(spec.getUri());
        builder.setSubtitleConfigurations(buildSubtitleConfigs(spec.getSubs()));
        builder.setDrmConfiguration(buildDrmConfig(spec.getDrm()));
        builder.setRequestMetadata(buildRequestMetadata(spec));
        builder.setMediaMetadata(spec.getMetadata());
        // JKTV: setAdblock 属私有扩展，已移除（广告拦截由 spider 层处理）
        builder.setMimeType(spec.getFormat());
        builder.setImageDurationMs(15000);
        builder.setMediaId(spec.getKey());
        // JKTV: setDecode 属私有扩展，已移除（解码偏好由 DecodeSetting 控制）
        return builder.build();
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) return "application/octet-stream";
        return null;
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    private static int getRenderMode(int decode) {
        return decode == PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static CaptionStyleCompat getCaptionStyle() {
        return PlayerSetting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    private static LoadControl buildLoadControl() {
        return new DefaultLoadControl.Builder().setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * PlayerSetting.getBuffer(), DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * PlayerSetting.getBuffer(), DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS).build();
    }

    private static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (PlayerSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguage(Locale.getDefault().getISO3Language());
        builder.setTunnelingEnabled(PlayerSetting.isTunnel());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    /**
     * 构造渲染器工厂：硬解(MediaCodec) + FFmpeg 软解兜底。
     *
     * <p>JKTV 集成 Media3 官方 {@code decoder_ffmpeg} 模块（Apache-2.0）作为软解内核：
     * 硬解失败（部分 HEVC/AV1 片源、设备解码器缺失）时自动回退到 FFmpeg 软解，
     * 避免"有声音无画面"。FFmpeg 库不可用时自动降级为纯硬解。</p>
     */
    private static RenderersFactory buildRenderersFactory(int renderMode) {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get())
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(renderMode);
        // JKTV: 集成 Media3 官方 decoder_ffmpeg 模块（Apache-2.0）作软解内核。
        // 按官方 README，给 DefaultRenderersFactory 设 EXTENSION_RENDERER_MODE_ON
        // 即可自动启用 FFmpeg 扩展渲染器：硬解不支持该格式时回退软解，
        // 解决"有声音无画面"。FFmpeg 库缺失时自动降级为纯硬解。
        if (FfmpegLibrary.isAvailable()) {
            factory.setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        }
        return factory;
    }

    private static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    private static MediaItem.RequestMetadata buildRequestMetadata(PlaySpec spec) {
        return new MediaItem.RequestMetadata.Builder().setMediaUri(spec.getUri()).setExtras(PlayerHelper.toBundle(spec.getHeaders())).build();
    }

    private static List<MediaItem.SubtitleConfiguration> buildSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs != null) for (Sub sub : subs) configs.add(buildSubConfig(sub));
        return configs;
    }

    private static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub) {
        return new MediaItem.SubtitleConfiguration.Builder(Uri.parse(UrlUtil.convert(sub.getUrl()))).setLabel(sub.getName()).setMimeType(sub.getFormat()).setSelectionFlags(sub.getFlag()).setLanguage(sub.getLang()).build();
    }

    private static MediaItem.DrmConfiguration buildDrmConfig(Drm drm) {
        return drm == null ? null : new MediaItem.DrmConfiguration.Builder(drm.getUUID()).setMultiSession(!C.CLEARKEY_UUID.equals(drm.getUUID())).setForceDefaultLicenseUri(drm.isForceKey()).setLicenseRequestHeaders(drm.getHeader()).setLicenseUri(drm.getKey()).build();
    }
}
