package com.fongmi.android.tv.ui.adapter;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.ui.custom.GuideRowView;

import java.util.ArrayList;
import java.util.List;

/**
 * TV Guide 频道行适配器。每行是一个自绘的 {@link GuideRowView}。
 */
public class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.Holder> {

    public interface Callback {

        void onRowFocused(int index);

        void onCursorMove(int index, int delta);

        void onPlay(int index);
    }

    private final Callback callback;
    private final List<Channel> items = new ArrayList<>();
    private float offsetPx;
    private int activeIndex = -1;
    private int cursor;

    public GuideAdapter(Callback callback) {
        this.callback = callback;
        setHasStableIds(false);
    }

    public void submit(List<Channel> channels) {
        items.clear();
        if (channels != null) items.addAll(channels);
        notifyDataSetChanged();
    }

    public List<Channel> getItems() {
        return items;
    }

    public void setOffset(float offsetPx) {
        this.offsetPx = offsetPx;
        for (int i = 0; i < getRowCount(); i++) {
            View v = findViewByIndex(i);
            if (v instanceof GuideRowView) ((GuideRowView) v).setOffset(offsetPx);
        }
    }

    public void setActive(int index) {
        this.activeIndex = index;
        for (int i = 0; i < getRowCount(); i++) {
            View v = findViewByIndex(i);
            if (v instanceof GuideRowView) ((GuideRowView) v).setActive(i == index);
        }
    }

    public void setCursor(int index, int cursor) {
        this.cursor = cursor;
        View v = findViewByIndex(index);
        if (v instanceof GuideRowView) ((GuideRowView) v).setCursor(cursor);
    }

    public int getCursor() {
        return cursor;
    }

    private int getRowCount() {
        return attached == null ? 0 : attached.getChildCount();
    }

    private RecyclerView attached;

    private View findViewByIndex(int adapterIndex) {
        if (attached == null) return null;
        for (int i = 0; i < attached.getChildCount(); i++) {
            View child = attached.getChildAt(i);
            int pos = attached.getChildAdapterPosition(child);
            if (pos == adapterIndex) return child;
        }
        return null;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attached = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        attached = null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        GuideRowView row = new GuideRowView(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) com.fongmi.android.tv.ui.custom.GuideTimeline.rowHeight(parent.getContext())));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setBackgroundResource(R.drawable.selector_item);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GuideRowView row = (GuideRowView) holder.itemView;
        Channel channel = items.get(position);
        row.bind(channel, position, position == activeIndex ? cursor : -1);
        row.setOffset(offsetPx);
        row.setActive(position == activeIndex);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) callback.onRowFocused(holder.getBindingAdapterPosition());
        });
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = holder.getBindingAdapterPosition();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                callback.onCursorMove(pos, -1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                callback.onCursorMove(pos, 1);
                return true;
            }
            return false;
        });
        row.setOnClickListener(v -> callback.onPlay(holder.getBindingAdapterPosition()));
    }

    static class Holder extends RecyclerView.ViewHolder {

        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
