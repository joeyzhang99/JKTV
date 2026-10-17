package androidx.media3.ui.danmaku;

/**
 * JKTV: 上游将弹幕渲染封装为不公开的预编译 AAR（androidx.media3.ui.danmaku）。
 * 此处重建其中的纯数据配置类，使工程可独立构建。
 *
 * 说明：本类只承载弹幕的样式/密度/显示配置，不包含渲染实现。
 * V1.0.0 的弹幕渲染已降级（见 UPSTREAM.md），配置项仍完整保留，
 * 便于后续接入开源弹幕渲染库后直接生效。
 */
public final class DanmakuConfig {

    // ---- 样式模式 ----
    public static final int STYLE_NONE = 0;
    public static final int STYLE_STROKE = 1;
    public static final int STYLE_SHADOW = 2;
    public static final int STYLE_PROJECTION = 3;

    // ---- 颜色模式 ----
    public static final int COLOR_MODE_DEFAULT = 0;
    public static final int COLOR_MODE_COLORFUL = 1;
    public static final int COLOR_MODE_GRADIENT = 2;

    /** 默认配置（与上游出厂默认一致）。 */
    public static final DanmakuConfig DEFAULT = new Builder().build();

    public final float textScale;
    public final float transparency;
    public final boolean textBold;
    public final int styleMode;
    public final float shadowTransparency;
    public final float strokeWidthMultiplier;
    public final float projectionOffsetXMultiplier;
    public final float projectionOffsetYMultiplier;
    public final float projectionTransparency;
    public final int colorMode;
    public final long durationMs;
    public final long fixedDurationMs;
    public final long timeOffsetMs;
    public final int maxOnScreen;
    public final float scrollAreaRatio;
    public final float scrollGapRatio;
    public final float lineSpacing;
    public final int maxScrollLines;
    public final int maxTopLines;
    public final int maxBottomLines;
    public final boolean showScroll;
    public final boolean showTop;
    public final boolean showBottom;
    public final boolean showReverse;
    public final boolean showPositioned;
    public final boolean showSubtitle;
    public final boolean showSpecial;

    private DanmakuConfig(Builder b) {
        this.textScale = b.textScale;
        this.transparency = b.transparency;
        this.textBold = b.textBold;
        this.styleMode = b.styleMode;
        this.shadowTransparency = b.shadowTransparency;
        this.strokeWidthMultiplier = b.strokeWidthMultiplier;
        this.projectionOffsetXMultiplier = b.projectionOffsetXMultiplier;
        this.projectionOffsetYMultiplier = b.projectionOffsetYMultiplier;
        this.projectionTransparency = b.projectionTransparency;
        this.colorMode = b.colorMode;
        this.durationMs = b.durationMs;
        this.fixedDurationMs = b.fixedDurationMs;
        this.timeOffsetMs = b.timeOffsetMs;
        this.maxOnScreen = b.maxOnScreen;
        this.scrollAreaRatio = b.scrollAreaRatio;
        this.scrollGapRatio = b.scrollGapRatio;
        this.lineSpacing = b.lineSpacing;
        this.maxScrollLines = b.maxScrollLines;
        this.maxTopLines = b.maxTopLines;
        this.maxBottomLines = b.maxBottomLines;
        this.showScroll = b.showScroll;
        this.showTop = b.showTop;
        this.showBottom = b.showBottom;
        this.showReverse = b.showReverse;
        this.showPositioned = b.showPositioned;
        this.showSubtitle = b.showSubtitle;
        this.showSpecial = b.showSpecial;
    }

    /** 复制并应用新 Builder（用于局部修改）。 */
    public Builder buildUpon() {
        return new Builder(this);
    }

    public static final class Builder {
        private float textScale = 1.0f;
        private float transparency = 0.0f;
        private boolean textBold = false;
        private int styleMode = STYLE_STROKE;
        private float shadowTransparency = 0.0f;
        private float strokeWidthMultiplier = 1.0f;
        private float projectionOffsetXMultiplier = 1.0f;
        private float projectionOffsetYMultiplier = 1.0f;
        private float projectionTransparency = 0.0f;
        private int colorMode = COLOR_MODE_DEFAULT;
        private long durationMs = 8000L;
        private long fixedDurationMs = 5000L;
        private long timeOffsetMs = 0L;
        private int maxOnScreen = 100;
        private float scrollAreaRatio = 0.5f;
        private float scrollGapRatio = 0.5f;
        private float lineSpacing = 1.0f;
        private int maxScrollLines = 10;
        private int maxTopLines = 3;
        private int maxBottomLines = 3;
        private boolean showScroll = true;
        private boolean showTop = true;
        private boolean showBottom = true;
        private boolean showReverse = false;
        private boolean showPositioned = true;
        private boolean showSubtitle = true;
        private boolean showSpecial = true;

        public Builder() {
        }

        private Builder(DanmakuConfig c) {
            this.textScale = c.textScale;
            this.transparency = c.transparency;
            this.textBold = c.textBold;
            this.styleMode = c.styleMode;
            this.shadowTransparency = c.shadowTransparency;
            this.strokeWidthMultiplier = c.strokeWidthMultiplier;
            this.projectionOffsetXMultiplier = c.projectionOffsetXMultiplier;
            this.projectionOffsetYMultiplier = c.projectionOffsetYMultiplier;
            this.projectionTransparency = c.projectionTransparency;
            this.colorMode = c.colorMode;
            this.durationMs = c.durationMs;
            this.fixedDurationMs = c.fixedDurationMs;
            this.timeOffsetMs = c.timeOffsetMs;
            this.maxOnScreen = c.maxOnScreen;
            this.scrollAreaRatio = c.scrollAreaRatio;
            this.scrollGapRatio = c.scrollGapRatio;
            this.lineSpacing = c.lineSpacing;
            this.maxScrollLines = c.maxScrollLines;
            this.maxTopLines = c.maxTopLines;
            this.maxBottomLines = c.maxBottomLines;
            this.showScroll = c.showScroll;
            this.showTop = c.showTop;
            this.showBottom = c.showBottom;
            this.showReverse = c.showReverse;
            this.showPositioned = c.showPositioned;
            this.showSubtitle = c.showSubtitle;
            this.showSpecial = c.showSpecial;
        }

        public Builder setTextScale(float v) { this.textScale = v; return this; }
        public Builder setTransparency(float v) { this.transparency = v; return this; }
        public Builder setTextBold(boolean v) { this.textBold = v; return this; }
        public Builder setStyleMode(int v) { this.styleMode = v; return this; }
        public Builder setShadowTransparency(float v) { this.shadowTransparency = v; return this; }
        public Builder setStrokeWidthMultiplier(float v) { this.strokeWidthMultiplier = v; return this; }
        public Builder setProjectionOffsetXMultiplier(float v) { this.projectionOffsetXMultiplier = v; return this; }
        public Builder setProjectionOffsetYMultiplier(float v) { this.projectionOffsetYMultiplier = v; return this; }
        public Builder setProjectionTransparency(float v) { this.projectionTransparency = v; return this; }
        public Builder setColorMode(int v) { this.colorMode = v; return this; }
        public Builder setDurationMs(long v) { this.durationMs = v; return this; }
        public Builder setFixedDurationMs(long v) { this.fixedDurationMs = v; return this; }
        public Builder setTimeOffsetMs(long v) { this.timeOffsetMs = v; return this; }
        public Builder setMaxOnScreen(int v) { this.maxOnScreen = v; return this; }
        public Builder setScrollAreaRatio(float v) { this.scrollAreaRatio = v; return this; }
        public Builder setScrollGapRatio(float v) { this.scrollGapRatio = v; return this; }
        public Builder setLineSpacing(float v) { this.lineSpacing = v; return this; }
        public Builder setMaxScrollLines(int v) { this.maxScrollLines = v; return this; }
        public Builder setMaxTopLines(int v) { this.maxTopLines = v; return this; }
        public Builder setMaxBottomLines(int v) { this.maxBottomLines = v; return this; }
        public Builder setShowScroll(boolean v) { this.showScroll = v; return this; }
        public Builder setShowTop(boolean v) { this.showTop = v; return this; }
        public Builder setShowBottom(boolean v) { this.showBottom = v; return this; }
        public Builder setShowReverse(boolean v) { this.showReverse = v; return this; }
        public Builder setShowPositioned(boolean v) { this.showPositioned = v; return this; }
        public Builder setShowSubtitle(boolean v) { this.showSubtitle = v; return this; }
        public Builder setShowSpecial(boolean v) { this.showSpecial = v; return this; }

        public DanmakuConfig build() { return new DanmakuConfig(this); }
    }
}
