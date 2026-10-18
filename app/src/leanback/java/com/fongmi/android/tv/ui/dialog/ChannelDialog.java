package com.fongmi.android.tv.ui.dialog;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Github;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 更新通道选择（leanback）。
 *
 * <p>列出 {@link Github} 已知的全部通道，供用户手动指定检查更新走哪条路径；
 * 选「跟随系统」则按内置顺序自动尝试。选择持久化到 {@link Setting}。</p>
 */
public class ChannelDialog {

    public static void show(FragmentActivity activity) {
        String[] names = Github.CHANNEL_NAMES.toArray(new String[0]);
        int current = Setting.getUpdateChannel();
        // 「跟随系统」显示为第一项，对应 CH_AUTO(-1)；其余通道依次后移
        String[] items = new String[names.length + 1];
        items[0] = "跟随系统（自动尝试）";
        System.arraycopy(names, 0, items, 1, names.length);
        int checked = current == Github.CH_AUTO ? 0 : current + 1;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_channel_pick)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    int channel = which == 0 ? Github.CH_AUTO : which - 1;
                    Setting.putUpdateChannel(channel);
                    String label = channel == Github.CH_AUTO ? activity.getString(R.string.update_channel_auto) : Github.CHANNEL_NAMES.get(channel);
                    Notify.show(activity.getString(R.string.update_channel_set, label));
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
