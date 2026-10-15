package com.nearbyshare.legacy;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;

final class MaterialDialog {
    interface Action {
        boolean onClick(Dialog dialog);
    }

    interface ChoiceListener {
        void onChoice(int index);
    }

    private MaterialDialog() {
    }

    static Dialog show(Context context, String title, String message, View content,
                       String negativeLabel, final Action negativeAction,
                       String positiveLabel, final Action positiveAction) {
        final Dialog dialog = new Dialog(context);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 22), dp(context, 22),
                dp(context, 22), dp(context, 16));
        panel.setBackgroundDrawable(MaterialUi.container(context,
                MaterialUi.SURFACE_CONTAINER, 16));

        if (title != null && title.length() > 0) {
            TextView heading = new TextView(context);
            heading.setText(title);
            heading.setTextColor(MaterialUi.ON_SURFACE);
            heading.setTextSize(21);
            heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            panel.addView(heading, fullWidth());
        }

        if (message != null && message.length() > 0) {
            TextView body = new TextView(context);
            body.setText(message);
            body.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
            body.setTextSize(15);
            body.setLineSpacing(dp(context, 2), 1f);
            LinearLayout.LayoutParams bodyParams = fullWidth();
            bodyParams.topMargin = dp(context, 10);
            if (message.length() > 320) {
                ScrollView messageScroll = new ScrollView(context);
                messageScroll.setVerticalScrollBarEnabled(true);
                messageScroll.addView(body, new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                bodyParams.height = dp(context, 220);
                panel.addView(messageScroll, bodyParams);
            } else {
                panel.addView(body, bodyParams);
            }
        }

        if (content != null) {
            LinearLayout.LayoutParams contentParams = fullWidth();
            contentParams.topMargin = dp(context,
                    message != null && message.length() > 0 ? 16 : 12);
            panel.addView(content, contentParams);
        }

        if (negativeLabel != null || positiveLabel != null) {
            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            int actionGravity = Gravity.CENTER_VERTICAL;
            if (negativeLabel == null || positiveLabel == null) {
                actionGravity |= Gravity.RIGHT;
            }
            actions.setGravity(actionGravity);
            LinearLayout.LayoutParams actionsParams = fullWidth();
            actionsParams.topMargin = dp(context, 20);
            panel.addView(actions, actionsParams);

            if (negativeLabel != null) {
                TextView negative = actionButton(context, negativeLabel, false);
                LinearLayout.LayoutParams negativeParams = positiveLabel == null ?
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 48)) :
                        new LinearLayout.LayoutParams(0, dp(context, 48), 1);
                if (positiveLabel != null) {
                    negativeParams.leftMargin = dp(context, 4);
                    negativeParams.rightMargin = dp(context, 4);
                }
                actions.addView(negative, negativeParams);
                negative.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if (negativeAction == null || negativeAction.onClick(dialog)) {
                            dialog.dismiss();
                        }
                    }
                });
            }

            if (positiveLabel != null) {
                TextView positive = actionButton(context, positiveLabel, true);
                LinearLayout.LayoutParams positiveParams = negativeLabel == null ?
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 48)) :
                        new LinearLayout.LayoutParams(0, dp(context, 48), 1);
                if (negativeLabel != null) {
                    positiveParams.leftMargin = dp(context, 4);
                    positiveParams.rightMargin = dp(context, 4);
                }
                actions.addView(positive, positiveParams);
                positive.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if (positiveAction == null || positiveAction.onClick(dialog)) {
                            dialog.dismiss();
                        }
                    }
                });
            }
        }

        ScrollView dialogScroll = new ScrollView(context);
        dialogScroll.setFillViewport(false);
        dialogScroll.setVerticalScrollBarEnabled(false);
        dialogScroll.setClipToPadding(false);
        dialogScroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(dialogScroll);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            int availableWidth = context.getResources().getDisplayMetrics().widthPixels -
                    dp(context, 40);
            params.width = Math.min(dp(context, 500), availableWidth);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.64f;
            window.setAttributes(params);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        return dialog;
    }

    static void focusInput(final Dialog dialog, final EditText input) {
        input.setFocusableInTouchMode(true);
        input.requestFocus();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        input.post(new Runnable() {
            public void run() {
                InputMethodManager keyboard = (InputMethodManager)
                        input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    static Dialog showChoices(Context context, String title, String[] choices,
                              final ChoiceListener listener) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] dialogRef = new Dialog[1];
        for (int i = 0; i < choices.length; i++) {
            final int index = i;
            TextView choice = new TextView(context);
            choice.setText(choices[i]);
            choice.setTextSize(16);
            choice.setTextColor(MaterialUi.ON_SURFACE);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(context, 16), 0, dp(context, 16), 0);
            choice.setMinHeight(dp(context, 50));
            choice.setFocusable(true);
            choice.setClickable(true);
            choice.setBackgroundDrawable(MaterialUi.stateBackground(context,
                    MaterialUi.SURFACE, MaterialUi.SURFACE_HIGH,
                    MaterialUi.SURFACE, 10));
            LinearLayout.LayoutParams choiceParams = fullWidth();
            choiceParams.topMargin = dp(context, 5);
            list.addView(choice, choiceParams);
            choice.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    dialogRef[0].dismiss();
                    if (listener != null) {
                        listener.onChoice(index);
                    }
                }
            });
        }
        Dialog dialog = show(context, title, null, list,
                "Hủy", null, null, null);
        dialogRef[0] = dialog;
        return dialog;
    }

    static Dialog showSourceMenu(Context context,
                                 final ChoiceListener listener) {
        final Dialog dialog = new Dialog(context);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 20), dp(context, 18),
                dp(context, 20), dp(context, 20));
        panel.setBackgroundDrawable(MaterialUi.container(context,
                MaterialUi.SURFACE_CONTAINER, 16));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        panel.addView(header, fullWidth());

        TextView title = new TextView(context);
        title.setText("Thêm nội dung");
        title.setTextColor(MaterialUi.ON_SURFACE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, dp(context, 48), 1));

        TextView close = new TextView(context);
        close.setText("×");
        close.setTextSize(28);
        close.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        close.setGravity(Gravity.TOP | Gravity.RIGHT);
        close.setFocusable(false);
        close.setClickable(true);
        close.setContentDescription("Đóng chọn nội dung");
        close.setBackgroundDrawable(null);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                dp(context, 44), dp(context, 48));
        closeParams.topMargin = -dp(context, 3);
        header.addView(close, closeParams);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        final String[] labels = new String[] {"Tệp", "Media", "Dán", "Nhập"};
        final String[] descriptions = new String[] {
                "Chọn tài liệu hoặc tệp bất kỳ",
                "Chọn ảnh và video",
                "Dùng nội dung đã sao chép",
                "Soạn tin nhắn văn bản"
        };
        final int[] icons = new int[] {
                MaterialIconView.FILE, MaterialIconView.MEDIA,
                MaterialIconView.CLIPBOARD, MaterialIconView.EDIT
        };
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams optionsParams = fullWidth();
        optionsParams.topMargin = 0;
        panel.addView(options, optionsParams);

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 12), dp(context, 8),
                    dp(context, 14), dp(context, 8));
            row.setBackgroundDrawable(MaterialUi.stateBackground(context,
                    MaterialUi.SURFACE_HIGH, MaterialUi.SURFACE_CONTAINER,
                    MaterialUi.SURFACE_HIGH, 12));
            row.setFocusable(true);
            row.setClickable(true);
            row.setContentDescription(labels[i] + ". " + descriptions[i]);

            LinearLayout iconTile = new LinearLayout(context);
            iconTile.setGravity(Gravity.CENTER);
            iconTile.setBackgroundDrawable(MaterialUi.rounded(context,
                    MaterialUi.SURFACE_CONTAINER, 12));
            MaterialIconView icon = new MaterialIconView(context, icons[i]);
            icon.setTint(MaterialUi.PRIMARY);
            iconTile.addView(icon, new LinearLayout.LayoutParams(
                    dp(context, 24), dp(context, 24)));
            row.addView(iconTile, new LinearLayout.LayoutParams(
                    dp(context, 44), dp(context, 44)));

            LinearLayout details = new LinearLayout(context);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams detailsParams =
                    new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            detailsParams.leftMargin = dp(context, 12);
            row.addView(details, detailsParams);

            TextView label = new TextView(context);
            label.setText(labels[i]);
            label.setTextColor(MaterialUi.ON_SURFACE);
            label.setTextSize(15);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            details.addView(label, fullWidth());
            TextView description = new TextView(context);
            description.setText(descriptions[i]);
            description.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
            description.setTextSize(12);
            LinearLayout.LayoutParams descriptionParams = fullWidth();
            descriptionParams.topMargin = dp(context, 2);
            details.addView(description, descriptionParams);

            LinearLayout.LayoutParams rowParams = fullWidth();
            rowParams.topMargin = dp(context, i == 0 ? 0 : 8);
            options.addView(row, rowParams);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onChoice(index);
                    }
                }
            });
        }

        ScrollView dialogScroll = new ScrollView(context);
        dialogScroll.setFillViewport(false);
        dialogScroll.setVerticalScrollBarEnabled(false);
        dialogScroll.setClipToPadding(false);
        dialogScroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(dialogScroll);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            int availableWidth = context.getResources().getDisplayMetrics().widthPixels -
                    dp(context, 40);
            params.width = Math.min(dp(context, 500), availableWidth);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.64f;
            window.setAttributes(params);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        return dialog;
    }

    private static Button actionButton(Context context, String label,
                                       boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTransformationMethod(null);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        button.setMinWidth(dp(context, 64));
        button.setMaxLines(2);
        button.setFocusable(true);
        button.setClickable(true);
        if (primary) {
            button.setTextColor(MaterialUi.ON_PRIMARY);
            button.setBackgroundDrawable(MaterialUi.primaryButtonBackground(context));
        } else if ("Hủy".equals(label)) {
            button.setTextColor(MaterialUi.ON_SURFACE);
            button.setBackgroundDrawable(MaterialUi.stateBackground(context,
                    MaterialUi.SURFACE_HIGH, MaterialUi.SURFACE_CONTAINER,
                    MaterialUi.SURFACE_HIGH, 12));
        } else {
            button.setTextColor(MaterialUi.PRIMARY);
            button.setBackgroundDrawable(MaterialUi.textButtonBackground(context));
        }
        return button;
    }

    private static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return MaterialUi.dp(context, value);
    }
}