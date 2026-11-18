package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TV Guide 中的一行：左侧固定频道信息 + 右侧按时间轴排布的节目块。
 *
 * <p>所有行共享同一个 {@code offsetPx}，用平移而非真实滚动实现横向同步，
 * 因此上下滚动时不会出现错位。JKTV 原创实现。</p>
 */
public class GuideRowView extends View {

    private static final int C_BG = 0xFF171B24;
    private static final int C_BG_ALT = 0xFF1B2029;
    private static final int C_CH_CELL = 0xFF1F2531;
    private static final int C_PROGRAM = 0xFF2A3140;
    private static final int C_PROGRAM_AIRING = 0xFF1F5C4A;
    private static final int C_CURSOR = 0xFF2F6BFF;
    private static final int C_TEXT = 0xFFEDF1F7;
    private static final int C_TEXT_DIM = 0xFF8E98A8;
    private static final int C_DIVIDER = 0xFF262C38;
    private static final int C_NOW = 0xFFE53935;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint channelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint numberPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint placeholderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
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

    public GuideRowView(Context context) {
        this(context, null);
    }

    public GuideRowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        channelWidth = GuideTimeline.channelWidth(context);
        pxPerMin = GuideTimeline.pxPerMin(context);
        dayStart = GuideTimeline.dayStart();

        titlePaint.setColor(C_TEXT);
        titlePaint.setTextSize(14f * density);
        titlePaint.setFakeBoldText(true);

        metaPaint.setColor(C_TEXT_DIM);
        metaPaint.setTextSize(11f * density);

        channelPaint.setColor(C_TEXT);
        channelPaint.setTextSize(13f * density);

        numberPaint.setColor(C_TEXT_DIM);
        numberPaint.setTextSize(11f * density);

        placeholderPaint.setColor(C_TEXT_DIM);
        placeholderPaint.setTextSize(16f * density);
        placeholderPaint.setTextAlign(Paint.Align.CENTER);
        placeholderPaint.setFakeBoldText(true);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, density));
        setWillNotDraw(false);
        setFocusable(false);
    }

    /** 绑定数据。cursor 为 -1 表示该行不显示选中框。 */
    public void bind(Channel channel, int rowIndex, int cursor) {
        this.channel = channel;
        this.rowIndex = rowIndex;
        this.cursor = cursor;
        this.programs = GuideTimeline.today(channel, dayStart);
        invalidate();
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

    public void refreshTimes() {
        long ds = GuideTimeline.dayStart();
        if (ds != dayStart) {
            dayStart = ds;
            if (channel != null) programs = GuideTimeline.today(channel, dayStart);
        }
        invalidate();
    }

    /** 当前时间正在播出的节目下标，找不到返回 -1。 */
    public int indexOfAiring() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < programs.size(); i++) {
            EpgData d = programs.get(i);
            if (d.getStartTime() <= now && now < d.getEndTime()) return i;
        }
        return -1;
    }

    /** 距离给定时间最近、且已开始的节目下标，用于初始化光标。 */
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
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) GuideTimeline.rowHeight(getContext());
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        channelWidth = GuideTimeline.channelWidth(getContext());
        pxPerMin = GuideTimeline.pxPerMin(getContext());

        // 行底
        fill.setColor(rowIndex % 2 == 0 ? C_BG : C_BG_ALT);
        canvas.drawRect(0, 0, w, h, fill);

        drawChannelCell(canvas, h);
        drawPrograms(canvas, w, h);

        // 行分隔线
        fill.setColor(C_DIVIDER);
        canvas.drawRect(0, h - Math.max(1f, density), w, h, fill);
    }

    private void drawChannelCell(Canvas canvas, float h) {
        fill.setColor(C_CH_CELL);
        canvas.drawRect(0, 0, channelWidth, h, fill);

        if (channel == null) return;

        // 台标占位块
        float pad = 8f * density;
        float box = h - pad * 2;
        rect.set(pad, pad, pad + box, h - pad);
        fill.setColor(0xFF2E3546);
        canvas.drawRoundRect(rect, 6f * density, 6f * density, fill);

        String initial = "";
        String name = channel.getName() == null ? "" : channel.getName();
        if (!name.isEmpty()) initial = name.substring(0, 1);
        canvas.drawText(initial, rect.centerX(), rect.centerY() + placeholderPaint.getTextSize() * 0.36f, placeholderPaint);

        float textLeft = rect.right + 8f * density;
        float textRight = channelWidth - 6f * density;
        if (textRight <= textLeft) return;

        String number = channel.getNumber() == null ? "" : channel.getNumber();
        if (!number.isEmpty()) {
            canvas.drawText(number, textLeft, pad + numberPaint.getTextSize(), numberPaint);
        }

        CharSequence shown = TextUtils.ellipsize(name, channelPaint, textRight - textLeft, TextUtils.TruncateAt.END);
        canvas.drawText(shown.toString(), textLeft, h * 0.68f, channelPaint);
    }

    private void drawPrograms(Canvas canvas, float w, float h) {
        canvas.save();
        canvas.clipRect(channelWidth, 0, w, h);

        if (programs.isEmpty()) {
            metaPaint.setColor(C_TEXT_DIM);
            canvas.drawText("暂无节目单", channelWidth + 14f * density, h * 0.58f, metaPaint);
            canvas.restore();
            drawActiveBorder(canvas, w, h);
            return;
        }

        long now = System.currentTimeMillis();
        float inset = 2f * density;
        float radius = 6f * density;

        for (int i = 0; i < programs.size(); i++) {
            EpgData d = programs.get(i);
            float x1 = channelWidth + GuideTimeline.x(d.getStartTime(), dayStart, pxPerMin) - offsetPx;
            float x2 = channelWidth + GuideTimeline.x(d.getEndTime(), dayStart, pxPerMin) - offsetPx;
            if (x2 < channelWidth || x1 > w) continue;

            boolean airing = d.getStartTime() <= now && now < d.getEndTime();
            boolean selected = active && i == cursor;

            rect.set(x1 + 1f, inset, x2 - 1f, h - inset);
            fill.setColor(selected ? C_CURSOR : airing ? C_PROGRAM_AIRING : C_PROGRAM);
            canvas.drawRoundRect(rect, radius, radius, fill);

            if (rect.width() < 28f * density) continue;

            float tx = rect.left + 8f * density;
            float available = rect.width() - 16f * density;

            CharSequence title = TextUtils.ellipsize(d.getTitle() == null ? "" : d.getTitle(),
                    titlePaint, available, TextUtils.TruncateAt.END);
            canvas.drawText(title.toString(), tx, h * 0.46f, titlePaint);

            String range = timeFormat.format(new Date(d.getStartTime()))
                    + " - " + timeFormat.format(new Date(d.getEndTime()));
            CharSequence meta = TextUtils.ellipsize(range, metaPaint, available, TextUtils.TruncateAt.END);
            canvas.drawText(meta.toString(), tx, h * 0.74f, metaPaint);
        }

        canvas.restore();

        // 当前时间竖线
        float nx = channelWidth + GuideTimeline.nowX(dayStart, pxPerMin) - offsetPx;
        if (nx >= channelWidth && nx <= w) {
            stroke.setColor(C_NOW);
            canvas.drawLine(nx, 0, nx, h, stroke);
        }

        drawActiveBorder(canvas, w, h);
    }

    private void drawActiveBorder(Canvas canvas, float w, float h) {
        if (!active) return;
        stroke.setColor(C_CURSOR);
        stroke.setStrokeWidth(2f * density);
        float half = density;
        rect.set(half, half, w - half, h - half);
        canvas.drawRoundRect(rect, 4f * density, 4f * density, stroke);
        stroke.setStrokeWidth(Math.max(1f, density));
    }
}
