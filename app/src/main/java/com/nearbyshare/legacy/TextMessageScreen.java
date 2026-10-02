package com.nearbyshare.legacy;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class TextMessageScreen extends ScrollView {
    interface Listener {
        void onSaveText(String text);
        void onCopyText(String text);
    }

    TextMessageScreen(Context context, String senderName, String message,
                      final Listener listener) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(MaterialUi.BACKGROUND);

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(context, 22), dp(context, 30),
                dp(context, 24), dp(context, 28));
        addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout iconBadge = new LinearLayout(context);
        iconBadge.setGravity(Gravity.CENTER);
        iconBadge.setBackgroundDrawable(MaterialUi.rounded(context,
                MaterialUi.SURFACE_HIGH, 12));
        MaterialIconView deviceIcon = new MaterialIconView(context,
                MaterialIconView.DEVICE);
        deviceIcon.setTint(MaterialUi.PRIMARY);
        iconBadge.addView(deviceIcon, new LinearLayout.LayoutParams(
                dp(context, 32), dp(context, 32)));
        page.addView(iconBadge, new LinearLayout.LayoutParams(
                dp(context, 64), dp(context, 64)));

        TextView sender = new TextView(context);
        sender.setText(senderName == null ? "Thiết bị" : senderName);
        sender.setTextColor(MaterialUi.ON_SURFACE);
        sender.setTextSize(26);
        sender.setGravity(Gravity.CENTER);
        sender.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams senderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        senderParams.topMargin = dp(context, 9);
        page.addView(sender, senderParams);

        TextView subtitle = new TextView(context);
        subtitle.setText("đã gửi cho bạn một tin nhắn:");
        subtitle.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(context, 28);
        page.addView(subtitle, subtitleParams);

        TextView content = new TextView(context);
        content.setText(message);
        content.setTextColor(MaterialUi.ON_SURFACE);
        content.setTextSize(15);
        content.setGravity(Gravity.TOP | Gravity.LEFT);
        content.setPadding(dp(context, 18), dp(context, 16),
                dp(context, 18), dp(context, 16));
        content.setBackgroundDrawable(MaterialUi.container(context,
                MaterialUi.SURFACE_CONTAINER, 12));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = dp(context, 13);
        page.addView(content, contentParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(context, 14);
        page.addView(actions, actionsParams);

        Button save = actionButton(context, "Lưu thành tệp tin", false);
        actions.addView(save, new LinearLayout.LayoutParams(
                0, dp(context, 52), 1));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, dp(context, 52), 1);
        copyParams.leftMargin = dp(context, 10);
        Button copy = actionButton(context, "Sao chép", true);
        actions.addView(copy, copyParams);

        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                listener.onSaveText(message);
            }
        });
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                listener.onCopyText(message);
            }
        });
    }

    private static Button actionButton(Context context, String label,
                                       boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(primary ? MaterialUi.ON_PRIMARY : MaterialUi.ON_SURFACE);
        button.setTextSize(14);
        button.setTransformationMethod(null);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setBackgroundDrawable(primary ?
                MaterialUi.primaryButtonBackground(context) :
                MaterialUi.surfaceButtonBackground(context));
        return button;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}