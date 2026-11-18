package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * TV Guide 顶部时间刻度尺。
 *
 * <p>左侧固定格显示日期与当前时间，右侧每 30 分钟一格、整点标注。
 * 与节目行共用同一 pxPerMin 与滚动偏移，因此刻度始终对齐节目块边界。</p>
 */
public class TimeRulerView extends View {

    private static final int C_BG = 0xFF131619;
    private static final int C_TICK = 0xFF313336;
    private static final int C_TEXT = 0xFF9AA3AD;
    private static final int C_ACCENT = 0xFF90CAF9;
    private static final int C_NOW = 0xFF4A6A8A;

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日EEE", Locale.getDefault());

    private float offsetPx;
    private float channelWidth;
    private float pxPerMin;
    private final float density;
    private long dayStart;

    public TimeRulerView(Context context) {
        this(context, null);
    }

    public TimeRulerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        bgPaint.setColor(C_BG);
        accentPaint.setColor(C_ACCENT);
        accentPaint.setFakeBoldText(true);
        accentPaint.setTextSize(15f * density);
        tickPaint.setColor(C_TICK);
        tickPaint.setStrokeWidth(Math.max(1f, density));
        textPaint.setColor(C_TEXT);
        textPaint.setTextSize(13f * density);
        channelWidth = GuideTimeline.channelWidth(context);
        pxPerMin = GuideTimeline.pxPerMin(context);
        dayStart = GuideTimeline.dayStart();
    }

    public void setOffset(float offsetPx) {
        if (this.offsetPx == offsetPx) return;
        this.offsetPx = offsetPx;
        invalidate();
    }

    public void refresh() {
        long ds = GuideTimeline.dayStart();
        if (ds != dayStart) dayStart = ds;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();

        canvas.drawRect(0, 0, w, h, bgPaint);

        // 左侧：日期 + 当前时间（黄色调强调，参照界面同位置）
        String date = dateFormat.format(new Date());
        canvas.drawText(date, 10f * density, h * 0.66f, accentPaint);

        canvas.save();
        canvas.clipRect(channelWidth, 0, w, h);

        for (int m = 0; m <= 24 * 60; m += 30) {
            float cx = channelWidth + m * pxPerMin - offsetPx;
            if (cx < channelWidth - 2 || cx > w + 2) continue;
            boolean hour = (m % 60 == 0);
            canvas.drawLine(cx, hour ? h * 0.42f : h * 0.62f, cx, h, tickPaint);
            if (hour) {
                long t = dayStart + m * 60000L;
                canvas.drawText(hourFormat.format(new Date(t)), cx + 6f * density,
                        h * 0.66f, textPaint);
            }
        }

        float nx = channelWidth + GuideTimeline.nowX(dayStart, pxPerMin) - offsetPx;
        if (nx >= channelWidth && nx <= w) {
            tickPaint.setColor(C_NOW);
            canvas.drawLine(nx, 0, nx, h, tickPaint);
            tickPaint.setColor(C_TICK);
        }

        canvas.restore();

        tickPaint.setColor(0xFF313336);
        canvas.drawLine(0, h - 1, w, h - 1, tickPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        channelWidth = GuideTimeline.channelWidth(getContext());
        pxPerMin = GuideTimeline.pxPerMin(getContext());
    }
}
