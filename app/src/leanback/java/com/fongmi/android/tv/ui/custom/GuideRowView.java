package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TV Guide 单行：左侧固定频道信息（台号 / 台标 / 名称 / 回看标记）+ 右侧时间轴节目块。
 *
 * <p>无 EPG 数据时按整点绘制占位块，保证网格形态完整可读。</p>
 *
 * <p>所有行共享同一个横向偏移，用平移而非真实滚动实现同步，滚动时不会错位。</p>
 */
public class GuideRowView extends View {

    // 取自参照界面实测色值
    private static final int C_ROW_A = 0xFF131619;
    private static final int C_ROW_B = 0xFF171B1F;
    private static final int C_BLOCK = 0xFF424547;
    private static final int C_BLOCK_FOCUS = 0xFFDEE0E2;
    private static final int C_BLOCK_ALT = 0xFF3A3D3F;
    private static final int C_TEXT = 0xFFD6D9DC;
    private static final int C_TEXT_ON_FOCUS = 0xFF1A1D21;
    private static final int C_TEXT_DIM = 0xFF8A9096;
    private static final int C_DIVIDER = 0xFF313336;
    private static final int C_LOGO_BG = 0xFF232629;
    private static final int C_ACCENT = 0xFF90CAF9;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint numberPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF logoRect = new RectF();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final float density;
    private float channelWidth;
    private float pxPerMin;
    private long dayStart;

    private Channel channel;
    private List<EpgData> programs = new ArrayList<>();
    private float offsetPx;
    private int cursor = -1;
    private int rowIndex;
    private boolean active;
    private Bitmap logo;
    private CustomTarget<Bitmap> logoTarget;

    public GuideRowView(Context context) {
        this(context, null);
    }

    public GuideRowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        channelWidth = GuideTimeline.channelWidth(context);
        pxPerMin = GuideTimeline.pxPerMin(context);
        dayStart = GuideTimeline.dayStart();

        namePaint.setColor(0xFFEDF1F7);
        namePaint.setTextSize(15f * density);
        numberPaint.setColor(C_TEXT_DIM);
        numberPaint.setTextSize(13f * density);
        titlePaint.setColor(C_TEXT);
        titlePaint.setTextSize(14f * density);
        metaPaint.setColor(C_TEXT_DIM);
        metaPaint.setTextSize(11f * density);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, density));
        setWillNotDraw(false);
        setFocusable(false);
    }

    public void bind(Channel channel, int rowIndex, int cursor) {
        this.channel = channel;
        this.rowIndex = rowIndex;
        this.cursor = cursor;
        this.programs = GuideTimeline.today(channel, dayStart);
        invalidate();
        loadLogo();
    }

    private void loadLogo() {
        if (logoTarget != null) {
            Glide.with(this).clear(logoTarget);
            logoTarget = null;
        }
        logo = null;
        if (channel == null) return;
        String url = channel.getLogo();
        if (url == null || url.isEmpty()) return;
        int size = (int) (rowHeightPx() - 8 * density);
        if (size <= 0) return;
        logoTarget = new CustomTarget<Bitmap>(size, size) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                logo = resource;
                invalidate();
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                logo = null;
                invalidate();
            }
        };
        Glide.with(this).asBitmap().load(url).into(logoTarget);
    }

    public void clearLogo() {
        if (logoTarget != null) {
            Glide.with(this).clear(logoTarget);
            logoTarget = null;
        }
        logo = null;
    }

    private float rowHeightPx() {
        return GuideTimeline.rowHeight(getContext());
    }

    public Channel getChannel() {
        return channel;
    }

    public List<EpgData> getPrograms() {
        return programs;
    }

    public int getCursor() {
        return cursor;
    }

    public void setCursor(int cursor) {
        if (this.cursor == cursor) return;
        this.cursor = cursor;
        invalidate();
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        invalidate();
    }

    public void setOffset(float offsetPx) {
        if (this.offsetPx == offsetPx) return;
        this.offsetPx = offsetPx;
        invalidate();
    }

    public Bitmap getLogo() {
        return logo;
    }

    public void refreshTimes() {
        long ds = GuideTimeline.dayStart();
        if (ds != dayStart) {
            dayStart = ds;
            if (channel != null) programs = GuideTimeline.today(channel, dayStart);
        }
        invalidate();
    }

    /** 正在播出的节目下标；无数据返回 -1。 */
    public int indexOfAiring() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < programs.size(); i++) {
            EpgData d = programs.get(i);
            if (d.getStartTime() <= now && now < d.getEndTime()) return i;
        }
        return -1;
    }

    /** 已开始且最接近给定时间的节目下标。 */
    public int indexAt(long timeMs) {
        int best = -1;
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).getStartTime() <= timeMs) best = i;
            else break;
        }
        return best < 0 ? (programs.isEmpty() ? -1 : 0) : best;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) rowHeightPx());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        channelWidth = GuideTimeline.channelWidth(getContext());
        pxPerMin = GuideTimeline.pxPerMin(getContext());

        fill.setColor(rowIndex % 2 == 0 ? C_ROW_A : C_ROW_B);
        canvas.drawRect(0, 0, w, h, fill);

        drawChannelCell(canvas, h);
        drawPrograms(canvas, w, h);

        fill.setColor(C_DIVIDER);
        canvas.drawRect(0, h - Math.max(1f, density), w, h, fill);
    }

    private void drawChannelCell(Canvas canvas, float h) {
        if (channel == null) return;

        float pad = 4f * density;
        float box = 40f * density;
        float logoLeft = 63f * density;

        // 台标（有图则绘制，无图绘制圆角底色 + 首字）
        logoRect.set(logoLeft, pad, logoLeft + box, h - pad);
        if (logo != null) {
            canvas.save();
            canvas.clipRect(logoRect);
            canvas.drawBitmap(logo, null, logoRect, null);
            canvas.restore();
        } else {
            fill.setColor(C_LOGO_BG);
            canvas.drawRoundRect(logoRect, 5f * density, 5f * density, fill);
            String name = channel.getName() == null ? "" : channel.getName();
            if (!name.isEmpty()) {
                metaPaint.setColor(C_TEXT_DIM);
                metaPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(name.substring(0, 1), logoRect.centerX(),
                        logoRect.centerY() + metaPaint.getTextSize() * 0.36f, metaPaint);
                metaPaint.setTextAlign(Paint.Align.LEFT);
            }
        }

        // 台号
        String number = channel.getNumber();
        if (number == null || number.isEmpty()) number = String.valueOf(rowIndex + 1);
        canvas.drawText(number, 30f * density, h * 0.62f, numberPaint);

        // 台名
        float nameLeft = 143f * density;
        float nameRight = channelWidth - 8f * density;
        if (nameRight > nameLeft) {
            CharSequence shown = TextUtils.ellipsize(
                    channel.getName() == null ? "" : channel.getName(),
                    namePaint, nameRight - nameLeft, TextUtils.TruncateAt.END);
            canvas.drawText(shown.toString(), nameLeft, h * 0.62f, namePaint);
        }

        // 回看标记（支持 catchup 的频道）
        if (channel.getCatchup() != null && !channel.getCatchup().isEmpty()) {
            drawCatchupIcon(canvas, channelWidth - 20f * density, h * 0.5f);
        }
    }

    /** 回看标记：圆圈 + 回转箭头，简洁不抢眼。 */
    private void drawCatchupIcon(Canvas canvas, float cx, float cy) {
        float r = 7f * density;
        stroke.setColor(C_ACCENT);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.4f, 1.4f * density));
        canvas.drawCircle(cx, cy, r, stroke);
        float hand = r * 0.62f;
        canvas.drawLine(cx, cy, cx, cy - hand, stroke);
        canvas.drawLine(cx, cy, cx + hand * 0.8f, cy, stroke);
        stroke.setStrokeWidth(Math.max(1f, density));
    }

    private void drawPrograms(Canvas canvas, float w, float h) {
        canvas.save();
        canvas.clipRect(channelWidth, 0, w, h);

        long now = System.currentTimeMillis();
        float inset = 3f * density;
        float radius = 5f * density;
        float gap = 1.5f * density;

        if (programs.isEmpty()) {
            drawPlaceholders(canvas, w, h, inset, radius, gap);
        } else {
            for (int i = 0; i < programs.size(); i++) {
                EpgData d = programs.get(i);
                float x1 = channelWidth + GuideTimeline.x(d.getStartTime(), dayStart, pxPerMin) - offsetPx;
                float x2 = channelWidth + GuideTimeline.x(d.getEndTime(), dayStart, pxPerMin) - offsetPx;
                if (x2 < channelWidth || x1 > w) continue;

                boolean selected = active && i == cursor;
                boolean airing = d.getStartTime() <= now && now < d.getEndTime();
                drawBlock(canvas, x1 + gap, inset, x2 - gap, h - inset, radius,
                        selected, airing, d.getTitle(), d.getStartTime(), d.getEndTime(), h);
            }
        }

        canvas.restore();

        float nx = channelWidth + GuideTimeline.nowX(dayStart, pxPerMin) - offsetPx;
        if (nx >= channelWidth && nx <= w) {
            stroke.setColor(0xFF4A6A8A);
            stroke.setStrokeWidth(Math.max(1f, density));
            canvas.drawLine(nx, 0, nx, h, stroke);
        }

        if (active) {
            stroke.setColor(C_ACCENT);
            stroke.setStrokeWidth(2f * density);
            rect.set(density, density, w - density, h - density);
            canvas.drawRoundRect(rect, 3f * density, 3f * density, stroke);
            stroke.setStrokeWidth(Math.max(1f, density));
        }
    }

    /** 无 EPG 时按整点画占位块。 */
    private void drawPlaceholders(Canvas canvas, float w, float h, float inset, float radius, float gap) {
        long now = System.currentTimeMillis();
        for (int m = 0; m < 24 * 60; m += GuideTimeline.PLACEHOLDER_MIN) {
            long start = dayStart + m * 60000L;
            long end = start + GuideTimeline.PLACEHOLDER_MIN * 60000L;
            float x1 = channelWidth + GuideTimeline.x(start, dayStart, pxPerMin) - offsetPx;
            float x2 = channelWidth + GuideTimeline.x(end, dayStart, pxPerMin) - offsetPx;
            if (x2 < channelWidth || x1 > w) continue;
            boolean airing = start <= now && now < end;
            drawBlock(canvas, x1 + gap, inset, x2 - gap, h - inset, radius,
                    false, airing, "", start, end, h);
        }
    }

    private void drawBlock(Canvas canvas, float l, float t, float r, float b, float radius,
                           boolean selected, boolean airing, String title,
                           long startTime, long endTime, float h) {
        if (r - l < 1f) return;
        rect.set(l, t, r, b);
        if (selected) fill.setColor(C_BLOCK_FOCUS);
        else if (airing) fill.setColor(C_BLOCK_ALT);
        else fill.setColor(C_BLOCK);
        canvas.drawRoundRect(rect, radius, radius, fill);

        float avail = rect.width() - 12f * density;
        if (avail < 24f * density) return;

        int textColor = selected ? C_TEXT_ON_FOCUS : C_TEXT;
        int dimColor = selected ? 0xFF4A4F55 : C_TEXT_DIM;

        String label = (title == null || title.isEmpty())
                ? getContext().getString(com.fongmi.android.tv.R.string.guide_no_info)
                : title;

        titlePaint.setColor(textColor);
        CharSequence shownTitle = TextUtils.ellipsize(label, titlePaint, avail, TextUtils.TruncateAt.END);
        canvas.drawText(shownTitle.toString(), rect.left + 6f * density, h * 0.44f, titlePaint);

        String range = timeFormat.format(new Date(startTime)) + " - " + timeFormat.format(new Date(endTime));
        metaPaint.setColor(dimColor);
        CharSequence shownRange = TextUtils.ellipsize(range, metaPaint, avail, TextUtils.TruncateAt.END);
        canvas.drawText(shownRange.toString(), rect.left + 6f * density, h * 0.72f, metaPaint);
    }
}
