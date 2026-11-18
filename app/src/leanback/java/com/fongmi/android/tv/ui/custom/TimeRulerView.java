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
import java.util.Date;
import java.util.Locale;

/**
 * TV Guide 顶部时间刻度尺。
 *
 * <p>与节目行共用 {@link GuideTimeline#pxPerMin} 与同一滚动偏移，因此刻度始终对齐节目块。</p>
 */
public class TimeRulerView extends View {

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private float offsetPx;
    private float channelWidth;
    private float pxPerMin;
    private long dayStart;

    public TimeRulerView(Context context) {
        this(context, null);
    }

    public TimeRulerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        bgPaint.setColor(0xFF14171E);
        tickPaint.setColor(0xFF3A4152);
        tickPaint.setStrokeWidth(Math.max(1f, d));
        textPaint.setColor(0xFFB7C0D0);
        textPaint.setTextSize(13f * d);
        textPaint.setAntiAlias(true);
        channelWidth = GuideTimeline.channelWidth(context);
        pxPerMin = GuideTimeline.pxPerMin(context);
        dayStart = GuideTimeline.dayStart();
    }

    public void setOffset(float offsetPx) {
        if (this.offsetPx == offsetPx) return;
        this.offsetPx = offsetPx;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();

        // 左侧频道列背景（与节目区的刻度区分）
        canvas.drawRect(0, 0, channelWidth, h, bgPaint);

        canvas.save();
        canvas.clipRect(channelWidth, 0, w, h);

        // 每 30 分钟一个刻度，整点加长并标时间
        int stepMin = 30;
        int totalMin = 24 * 60;
        for (int m = 0; m <= totalMin; m += stepMin) {
            float cx = channelWidth + m * pxPerMin - offsetPx;
            if (cx < channelWidth - 2 || cx > w + 2) continue;
            boolean hour = (m % 60 == 0);
            float top = hour ? h * 0.34f : h * 0.58f;
            canvas.drawLine(cx, top, cx, h, tickPaint);
            if (hour) {
                long t = dayStart + m * 60000L;
                String label = hourFormat.format(new Date(t));
                canvas.drawText(label, cx + 4f * getResources().getDisplayMetrics().density, top - 4f, textPaint);
            }
        }

        // 当前时间指示
        float nx = channelWidth + GuideTimeline.nowX(dayStart, pxPerMin) - offsetPx;
        if (nx >= channelWidth && nx <= w) {
            tickPaint.setColor(0xFFE53935);
            canvas.drawLine(nx, 0, nx, h, tickPaint);
            tickPaint.setColor(0xFF3A4152);
        }

        canvas.restore();

        // 底部细分隔线
        tickPaint.setColor(0xFF2A2F3C);
        canvas.drawLine(0, h - 1, w, h - 1, tickPaint);
        tickPaint.setColor(0xFF3A4152);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        channelWidth = GuideTimeline.channelWidth(getContext());
        pxPerMin = GuideTimeline.pxPerMin(getContext());
    }
}
