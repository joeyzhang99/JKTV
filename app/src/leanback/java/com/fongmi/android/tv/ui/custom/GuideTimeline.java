package com.fongmi.android.tv.ui.custom;

import android.content.Context;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * TV Guide 时间轴几何计算。
 *
 * <p>把「一天中的分钟数」映射到横向像素，供刻度尺与节目条共用，保证两者严丝合缝。
 * JKTV 原创实现，只做时间与像素换算。</p>
 */
public final class GuideTimeline {

    /** 每分钟对应多少 dp。1 小时 ≈ 427dp，与主流 IPTV 客户端的可读密度一致。 */
    private static final float DP_PER_MIN = 7.0f;

    /** 频道列宽度（dp）。左侧固定，不随时间轴滚动。 */
    public static final float CHANNEL_WIDTH_DP = 340f;

    /** 行高（dp）。 */
    public static final float ROW_HEIGHT_DP = 40f;

    /** 无 EPG 数据时，占位块的时间跨度（分钟）。 */
    public static final int PLACEHOLDER_MIN = 60;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private GuideTimeline() {
    }

    public static float pxPerMin(Context context) {
        return DP_PER_MIN * context.getResources().getDisplayMetrics().density;
    }

    public static float channelWidth(Context context) {
        return CHANNEL_WIDTH_DP * context.getResources().getDisplayMetrics().density;
    }

    public static float rowHeight(Context context) {
        return ROW_HEIGHT_DP * context.getResources().getDisplayMetrics().density;
    }

    public static float dayWidth(Context context) {
        return 24 * 60 * pxPerMin(context);
    }

    /** 当天 0 点的毫秒时间戳，作为时间轴原点。 */
    public static long dayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 取某频道当天的节目，按开始时间排序，并裁掉跨天越界部分。 */
    public static List<EpgData> today(Channel channel, long dayStart) {
        List<EpgData> out = new ArrayList<>();
        if (channel == null) return out;
        for (Epg epg : channel.getDataList()) {
            for (EpgData data : epg.getList()) {
                if (data.getEndTime() <= dayStart) continue;
                if (data.getStartTime() >= dayStart + DAY_MS) continue;
                out.add(data);
            }
        }
        out.sort((a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));
        return out;
    }

    /** 时间点相对时间轴原点的 x（像素，未减滚动偏移）。 */
    public static float x(long timeMs, long dayStart, float pxPerMin) {
        return (timeMs - dayStart) / 60000f * pxPerMin;
    }

    /** 当前时间在时间轴上的 x（像素）。 */
    public static float nowX(long dayStart, float pxPerMin) {
        return x(System.currentTimeMillis(), dayStart, pxPerMin);
    }
}
