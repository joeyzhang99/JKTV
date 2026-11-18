package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.ui.adapter.GuideAdapter;
import com.fongmi.android.tv.ui.custom.GuideRowView;
import com.fongmi.android.tv.ui.custom.GuideTimeline;
import com.fongmi.android.tv.ui.custom.TimeRulerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TV Guide：多频道时间轴节目单。
 *
 * <p>交互：上下切换频道，左右切换节目，OK 播放，返回退出。</p>
 *
 * <p>实现说明：所有行共享同一横向偏移（平移而非各自滚动），
 * 因此刻度尺与节目块永远对齐。JKTV 原创实现。</p>
 */
public class TvGuideActivity extends AppCompatActivity implements GuideAdapter.Callback {

    private static final long PAN_STEP_DP = 40;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private TimeRulerView ruler;
    private RecyclerView recycler;
    private TextView clock;
    private GuideAdapter adapter;

    private final List<Channel> rows = new ArrayList<>();
    private float offsetPx;
    private float density;
    private boolean paused;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (paused) return;
            if (clock != null) clock.setText(clockFormat.format(new Date()));
            syncRows();
            ticker.postDelayed(this, 1000L);
        }
    };

    public static void start(AppCompatActivity activity) {
        activity.startActivity(new Intent(activity, TvGuideActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_guide);
        density = getResources().getDisplayMetrics().density;
        ruler = findViewById(R.id.ruler);
        clock = findViewById(R.id.clock);
        recycler = findViewById(R.id.recycler);

        loadChannels();
        setupRecycler();
        initOffset();

        clock.setText(clockFormat.format(new Date()));
    }

    private void loadChannels() {
        rows.clear();
        Live live = LiveConfig.get().getHome();
        if (live == null) return;
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                rows.add(channel);
            }
        }
        if (rows.isEmpty()) {
            TextView title = findViewById(R.id.title);
            if (title != null) title.setText(R.string.guide_no_channel);
        }
    }

    private void setupRecycler() {
        adapter = new GuideAdapter(this);
        adapter.submit(rows);
        recycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        recycler.setAdapter(adapter);
        recycler.setItemAnimator(null);
        recycler.post(() -> {
            if (recycler.getChildCount() > 0) {
                View first = recycler.getChildAt(0);
                first.requestFocus();
                onRowFocused(0);
            }
        });
    }

    /** 初始把时间轴对到当前时间，留出一小时左侧余量。 */
    private void initOffset() {
        float ppm = GuideTimeline.pxPerMin(this);
        float cw = GuideTimeline.channelWidth(this);
        float viewport = getResources().getDisplayMetrics().widthPixels - cw;
        float start = GuideTimeline.x(System.currentTimeMillis(), GuideTimeline.dayStart(), ppm) - viewport / 3f;
        offsetPx = clamp(start, 0, Math.max(0, GuideTimeline.dayWidth(this) - viewport));
        applyOffset();
    }

    private void applyOffset() {
        if (ruler != null) ruler.setOffset(offsetPx);
        if (adapter != null) adapter.setOffset(offsetPx);
    }

    private void syncRows() {
        // 让可见行重画「正在播出」与当前时间线；顺带处理跨天切换
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View v = recycler.getChildAt(i);
            if (v instanceof GuideRowView) ((GuideRowView) v).refreshTimes();
        }
        if (ruler != null) ruler.invalidate();
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float viewportWidth() {
        return getResources().getDisplayMetrics().widthPixels - GuideTimeline.channelWidth(this);
    }

    private void clampOffset() {
        offsetPx = clamp(offsetPx, 0, Math.max(0, GuideTimeline.dayWidth(this) - viewportWidth()));
    }

    /** 平移时间轴，保证光标所在节目完整可见。 */
    private void ensureCursorVisible(int rowIndex) {
        View v = findRowView(rowIndex);
        if (!(v instanceof GuideRowView)) return;
        GuideRowView row = (GuideRowView) v;
        List<EpgData> programs = row.getPrograms();
        int cursor = row.getCursor();
        if (cursor < 0 || cursor >= programs.size()) return;

        EpgData d = programs.get(cursor);
        float ppm = GuideTimeline.pxPerMin(this);
        long dayStart = GuideTimeline.dayStart();
        float x1 = GuideTimeline.x(d.getStartTime(), dayStart, ppm);
        float x2 = GuideTimeline.x(d.getEndTime(), dayStart, ppm);
        float vw = viewportWidth();
        float margin = 20 * density;

        if (x1 - margin < offsetPx) offsetPx = x1 - margin;
        else if (x2 + margin > offsetPx + vw) offsetPx = x2 + margin - vw;

        clampOffset();
        applyOffset();
    }

    private View findRowView(int adapterIndex) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (recycler.getChildAdapterPosition(child) == adapterIndex) return child;
        }
        return null;
    }

    @Override
    public void onRowFocused(int index) {
        if (adapter == null) return;
        adapter.setActive(index);
        View v = findRowView(index);
        if (v instanceof GuideRowView) {
            GuideRowView row = (GuideRowView) v;
            int cursor = row.getCursor();
            if (cursor < 0) {
                int airing = row.indexOfAiring();
                row.setCursor(airing >= 0 ? airing : row.indexAt(System.currentTimeMillis()));
            }
        }
        ensureCursorVisible(index);
    }

    @Override
    public void onCursorMove(int index, int delta) {
        View v = findRowView(index);
        if (!(v instanceof GuideRowView)) return;
        GuideRowView row = (GuideRowView) v;
        int size = row.getPrograms().size();
        if (size == 0) return;
        int cursor = row.getCursor();
        if (cursor < 0) cursor = row.indexOfAiring();
        int next = Math.max(0, Math.min(size - 1, cursor + delta));
        if (next == cursor) {
            // 已在边界：把时间轴继续平移，允许浏览空档
            offsetPx += delta * PAN_STEP_DP * density;
            clampOffset();
            applyOffset();
            return;
        }
        row.setCursor(next);
        ensureCursorVisible(index);
    }

    @Override
    public void onPlay(int index) {
        if (index < 0 || index >= rows.size()) return;
        Channel channel = rows.get(index);
        Intent intent = new Intent(this, LiveActivity.class);
        intent.putExtra("empty", false);
        intent.putExtra("guide_channel", channel.getName());
        String groupName = channel.getGroup() == null ? "" : channel.getGroup().getName();
        intent.putExtra("guide_group", groupName);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        paused = true;
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ticker.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
