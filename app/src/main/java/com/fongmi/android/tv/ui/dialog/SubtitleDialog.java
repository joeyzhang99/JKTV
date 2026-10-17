package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSubtitleBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

public final class SubtitleDialog extends BaseBottomSheetDialog {

    private DialogSubtitleBinding binding;
    private SubtitleView subtitleView;

    public static SubtitleDialog create() {
        return new SubtitleDialog();
    }

    public SubtitleDialog view(SubtitleView subtitleView) {
        this.subtitleView = subtitleView;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SubtitleDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean isFull() {
        return Util.isFullscreen(getActivity());
    }

    @Override
    protected boolean transparent() {
        return isFull();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        int count = binding.getRoot().getChildCount();
        if (isFull()) for (int i = 0; i < count; i++) ((ImageView) binding.getRoot().getChildAt(i)).getDrawable().setTint(MDColor.WHITE);
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(this::onUp);
        binding.down.setOnClickListener(this::onDown);
        binding.large.setOnClickListener(this::onLarge);
        binding.small.setOnClickListener(this::onSmall);
        binding.reset.setOnClickListener(this::onReset);
    }

    // JKTV: 上游 5.5.x 依赖私有 SubtitleView 扩展（addPosition/getPosition 等），
    // 官方 Media3 SubtitleView 无这些方法。改为直接维护持久化值并应用官方 API。

    private static final float POSITION_STEP = 0.005f;
    private static final float TEXT_SIZE_STEP = 0.002f;

    private void onUp(View view) {
        applyPosition(PlayerSetting.getSubtitlePosition() + POSITION_STEP);
    }

    private void onDown(View view) {
        applyPosition(PlayerSetting.getSubtitlePosition() - POSITION_STEP);
    }

    private void onLarge(View view) {
        applyTextSize(PlayerSetting.getSubtitleTextSize() + TEXT_SIZE_STEP);
    }

    private void onSmall(View view) {
        applyTextSize(PlayerSetting.getSubtitleTextSize() - TEXT_SIZE_STEP);
    }

    private void onReset(View view) {
        PlayerSetting.putSubtitleTextSize(0.0f);
        PlayerSetting.putSubtitlePosition(0.0f);
        applyTextSize(0.0f);
        applyPosition(0.0f);
    }

    private void applyPosition(float value) {
        float v = Math.max(0.0f, Math.min(1.0f, value));
        PlayerSetting.putSubtitlePosition(v);
        if (subtitleView != null) subtitleView.setBottomPaddingFraction(v);
    }

    private void applyTextSize(float value) {
        float v = Math.max(0.0f, value);
        PlayerSetting.putSubtitleTextSize(v);
        if (subtitleView != null && v > 0.0f) subtitleView.setFractionalTextSize(v);
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ResUtil.dp2px(isFull() ? 232 : 216), -1);
    }
}