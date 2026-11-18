package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
 * <p>上方显示当前选中频道与节目的信息，下方是频道×时间网格。</p>
 *
 * <p>交互：上下切换频道，左右切换节目，OK 播放，返回退出。</p>
 */
public class TvGuideActivity extends AppCompatActivity implements GuideAdapter.Callback {

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private TimeRulerView ruler;
    private RecyclerView recycler;
    private GuideAdapter adapter;

    private ImageView previewLogo;
    private TextView previewNumber;
    private TextView channelName;
    private TextView programTitle;
    private TextView programTime;
    private TextView programRemain;
    private TextView programDesc;
    private TextView groupName;
    private ProgressBar programProgress;

    private final List<Channel> rows = new ArrayList<>();
    private float offsetPx;
    private float density;
    private boolean paused;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (paused) return;
            syncRows();
            updateInfoPanel(adapter == null ? -1 : adapter.getActiveIndex());
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
        bindViews();

        loadChannels();
        setupRecycler();
        initOffset();
    }

    private void bindViews() {
        ruler = findViewById(R.id.ruler);
        recycler = findViewById(R.id.recycler);
        previewLogo = findViewById(R.id.previewLogo);
        previewNumber = findViewById(R.id.previewNumber);
        channelName = findViewById(R.id.channelName);
        programTitle = findViewById(R.id.programTitle);
        programTime = findViewById(R.id.programTime);
        programRemain = findViewById(R.id.programRemain);
        programDesc = findViewById(R.id.programDesc);
        groupName = findViewById(R.id.groupName);
        programProgress = findViewById(R.id.programProgress);
    }

    private void loadChannels() {
        rows.clear();
        Live live = LiveConfig.get().getHome();
        if (live == null) return;
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) rows.add(channel);
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
                recycler.getChildAt(0).requestFocus();
                onRowFocused(0);
            }
        });
    }

    private void initOffset() {
        float ppm = GuideTimeline.pxPerMin(this);
        float cw = GuideTimeline.channelWidth(this);
        float viewport = getResources().getDisplayMetrics().widthPixels - cw;
        float now = GuideTimeline.x(System.currentTimeMillis(), GuideTimeline.dayStart(), ppm);
        offsetPx = clamp(now - viewport / 4f, 0, Math.max(0, GuideTimeline.dayWidth(this) - viewport));
        applyOffset();
    }

    private void applyOffset() {
        if (ruler != null) ruler.setOffset(offsetPx);
        if (adapter != null) adapter.setOffset(offsetPx);
    }

    private void syncRows() {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View v = recycler.getChildAt(i);
            if (v instanceof GuideRowView) ((GuideRowView) v).refreshTimes();
        }
        if (ruler != null) ruler.refresh();
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

    private void ensureCursorVisible(int rowIndex) {
        View v = findRowView(rowIndex);
        if (!(v instanceof GuideRowView)) return;
        GuideRowView row = (GuideRowView) v;
        List<EpgData> programs = row.getPrograms();
        int cursor = row.getCursor();
        float ppm = GuideTimeline.pxPerMin(this);
        long dayStart = GuideTimeline.dayStart();
        float vw = viewportWidth();
        float margin = 24 * density;

        if (cursor >= 0 && cursor < programs.size()) {
            EpgData d = programs.get(cursor);
            float x1 = GuideTimeline.x(d.getStartTime(), dayStart, ppm);
            float x2 = GuideTimeline.x(d.getEndTime(), dayStart, ppm);
            if (x1 - margin < offsetPx) offsetPx = x1 - margin;
            else if (x2 + margin > offsetPx + vw) offsetPx = x2 + margin - vw;
        }
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

    /** 刷新上方信息面板。 */
    private void updateInfoPanel(int index) {
        if (index < 0 || index >= rows.size()) {
            channelName.setText("");
            programTitle.setText(R.string.guide_no_channel);
            programTime.setText("");
            programRemain.setText("");
            programProgress.setProgress(0);
            programDesc.setText("");
            groupName.setText("");
            previewNumber.setText("");
            previewLogo.setImageDrawable(null);
            return;
        }

        Channel channel = rows.get(index);
        Group group = channel.getGroup();
        groupName.setText(group == null ? "" : group.getName());
        channelName.setText(channel.getName());

        String number = channel.getNumber();
        previewNumber.setText(number == null || number.isEmpty() ? "" : number);

        if (channel.getLogo() != null && !channel.getLogo().isEmpty()) {
            Glide.with(this).load(channel.getLogo()).into(previewLogo);
        } else {
            previewLogo.setImageDrawable(null);
        }

        View rowView = findRowView(index);
        EpgData current = null;
        if (rowView instanceof GuideRowView) {
            GuideRowView row = (GuideRowView) rowView;
            List<EpgData> programs = row.getPrograms();
            int cursor = row.getCursor();
            if (cursor >= 0 && cursor < programs.size()) current = programs.get(cursor);
        }

        long now = System.currentTimeMillis();
        if (current == null) {
            programTitle.setText(R.string.guide_no_info);
            programTime.setText("");
            programRemain.setText("");
            programProgress.setProgress(0);
            programDesc.setText("");
            return;
        }

        programTitle.setText(current.getTitle() == null || current.getTitle().isEmpty()
                ? getString(R.string.guide_no_info) : current.getTitle());
        programTime.setText(clockFormat.format(new Date(current.getStartTime()))
                + " - " + clockFormat.format(new Date(current.getEndTime())));

        long total = Math.max(1, current.getEndTime() - current.getStartTime());
        long elapsed = Math.max(0, Math.min(total, now - current.getStartTime()));
        programProgress.setProgress((int) (elapsed * 100 / total));

        long remainMs = current.getEndTime() - now;
        if (remainMs > 0) {
            programRemain.setText(getString(R.string.guide_minutes, String.valueOf(remainMs / 60000)));
        } else {
            programRemain.setText("");
        }

        programDesc.setText("");
    }

    @Override
    public void onRowFocused(int index) {
        if (adapter == null) return;
        adapter.setActive(index);
        View v = findRowView(index);
        if (v instanceof GuideRowView) {
            GuideRowView row = (GuideRowView) v;
            if (row.getCursor() < 0) {
                int airing = row.indexOfAiring();
                row.setCursor(airing >= 0 ? airing : row.indexAt(System.currentTimeMillis()));
            }
        }
        ensureCursorVisible(index);
        updateInfoPanel(index);
    }

    @Override
    public void onCursorMove(int index, int delta) {
        View v = findRowView(index);
        if (!(v instanceof GuideRowView)) return;
        GuideRowView row = (GuideRowView) v;
        int size = row.getPrograms().size();
        if (size == 0) {
            offsetPx += delta * 60 * density;
            clampOffset();
            applyOffset();
            return;
        }
        int cursor = row.getCursor();
        if (cursor < 0) cursor = row.indexOfAiring();
        int next = Math.max(0, Math.min(size - 1, cursor + delta));
        if (next == cursor) {
            offsetPx += delta * 60 * density;
            clampOffset();
            applyOffset();
            return;
        }
        row.setCursor(next);
        ensureCursorVisible(index);
        updateInfoPanel(index);
    }

    @Override
    public void onPlay(int index) {
        if (index < 0 || index >= rows.size()) return;
        Channel channel = rows.get(index);
        Intent intent = new Intent(this, LiveActivity.class);
        intent.putExtra("empty", false);
        intent.putExtra("guide_channel", channel.getName());
        String gn = channel.getGroup() == null ? "" : channel.getGroup().getName();
        intent.putExtra("guide_group", gn);
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
