package com.nearbyshare.legacy;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class SelectedFilesOverlay {
    interface Listener {
        void onRemoveFile(int index);
        void onRemoveText();
        void onClearAll();
    }

    private SelectedFilesOverlay() {
    }

    static Dialog show(final Activity activity, List<Uri> selectedUris,
                       final String selectedText, final Listener listener) {
        final ArrayList<Uri> files = new ArrayList<Uri>(selectedUris);
        final boolean textMode = selectedText != null;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialUi.BACKGROUND);
        root.setPadding(dp(activity, 20), dp(activity, 8),
                dp(activity, 20), dp(activity, 12));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, fullWidth());

        FrameLayout back = new FrameLayout(activity);
        back.setContentDescription("Đóng danh sách tệp đã chọn");
        back.setBackgroundDrawable(MaterialUi.textButtonBackground(activity));
        MaterialIconView backIcon = new MaterialIconView(activity,
                MaterialIconView.BACK);
        backIcon.setTint(MaterialUi.ON_SURFACE);
        back.addView(backIcon, new FrameLayout.LayoutParams(
                dp(activity, 24), dp(activity, 24),
                Gravity.LEFT | Gravity.CENTER_VERTICAL));
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 48),
                dp(activity, 52)));
        TextView title = text(activity, "Tệp đang chờ", 21,
                MaterialUi.ON_SURFACE, true);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(0, dp(activity, 52), 1);
        titleParams.leftMargin = dp(activity, 8);
        header.addView(title, titleParams);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsParams = fullWidth();
        controlsParams.topMargin = dp(activity, 12);
        controlsParams.bottomMargin = dp(activity, 20);
        root.addView(controls, controlsParams);

        final TextView summary = text(activity, "", 14,
                MaterialUi.ON_SURFACE_VARIANT, false);
        summary.setLineSpacing(dp(activity, 2), 1f);
        controls.addView(summary, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button clearAll = new Button(activity);
        clearAll.setText("Xóa tất cả");
        clearAll.setTextSize(13);
        MaterialUi.stylePrimaryButton(activity, clearAll);
        clearAll.setMinHeight(dp(activity, 44));
        clearAll.setPadding(dp(activity, 12), dp(activity, 6),
                dp(activity, 12), dp(activity, 6));
        clearAll.setContentDescription("Xóa tất cả nội dung khỏi hàng chờ");
        controls.addView(clearAll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 44)));

        final LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll = new ScrollView(activity);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setClipToPadding(false);
        listScroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final Runnable[] renderRows = new Runnable[1];
        renderRows[0] = new Runnable() {
            public void run() {
                int count = textMode ? 1 : files.size();
                if (count == 0) {
                    dialog.dismiss();
                    return;
                }
                summary.setText(summary(activity, files, selectedText));
                clearAll.setVisibility(View.VISIBLE);
                rows.removeAllViews();

                if (textMode) {
                    long size = utf8Length(selectedText);
                    rows.addView(createRow(activity, "Tin nhắn văn bản",
                            Formatter.formatFileSize(activity, size),
                            MaterialIconView.CLIPBOARD, new Runnable() {
                                public void run() {
                                    listener.onRemoveText();
                                    dialog.dismiss();
                                }
                            }), rowParams(activity));
                    return;
                }

                for (int i = 0; i < files.size(); i++) {
                    final int index = i;
                    final Uri uri = files.get(i);
                    String name = FileSender.displayName(activity, uri);
                    long size = fileSize(activity, uri);
                    String sizeLabel = size < 0 ? "Kích thước chưa rõ" :
                            Formatter.formatFileSize(activity, size);
                    rows.addView(createRow(activity, name, sizeLabel,
                            iconFor(activity, uri), new Runnable() {
                                public void run() {
                                    listener.onRemoveFile(index);
                                    files.remove(index);
                                    renderRows[0].run();
                                }
                            }), rowParams(activity));
                }
            }
        };

        clearAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                listener.onClearAll();
                dialog.dismiss();
            }
        });

        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(MaterialUi.BACKGROUND));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.FILL);
            if (Build.VERSION.SDK_INT >= 21) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(MaterialUi.BACKGROUND);
                window.setNavigationBarColor(MaterialUi.BACKGROUND);
            }
        }
        renderRows[0].run();
        return dialog;
    }

    static String summary(Activity activity, List<Uri> files, String text) {
        if (text != null) {
            return "1 tin nhắn\nKích thước: " +
                    Formatter.formatFileSize(activity, utf8Length(text));
        }
        long total = 0L;
        boolean unknownSize = false;
        for (Uri uri : files) {
            long size = fileSize(activity, uri);
            if (size < 0L) {
                unknownSize = true;
            } else {
                total += size;
            }
        }
        String sizeLabel = unknownSize ? "chưa xác định" :
                Formatter.formatFileSize(activity, total);
        return files.size() + " tập tin\nKích thước: " + sizeLabel;
    }

    private static LinearLayout createRow(final Activity activity, String name,
                                          String size, int icon,
                                          final Runnable onRemove) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 11), dp(activity, 10),
                dp(activity, 8), dp(activity, 10));
        row.setBackgroundDrawable(MaterialUi.container(activity,
                MaterialUi.SURFACE_CONTAINER, 17));

        LinearLayout iconTile = new LinearLayout(activity);
        iconTile.setGravity(Gravity.CENTER);
        iconTile.setBackgroundDrawable(MaterialUi.rounded(activity,
                MaterialUi.SURFACE_HIGH, 13));
        MaterialIconView iconView = new MaterialIconView(activity, icon);
        iconView.setTint(MaterialUi.ON_SURFACE);
        iconTile.addView(iconView, new LinearLayout.LayoutParams(
                dp(activity, 34), dp(activity, 34)));
        row.addView(iconTile, new LinearLayout.LayoutParams(
                dp(activity, 58), dp(activity, 58)));

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailsParams.leftMargin = dp(activity, 12);
        detailsParams.rightMargin = dp(activity, 5);
        row.addView(details, detailsParams);

        TextView nameView = text(activity, name, 15,
                MaterialUi.ON_SURFACE, false);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        details.addView(nameView, fullWidth());
        TextView sizeView = text(activity, size, 12,
                MaterialUi.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams sizeParams = fullWidth();
        sizeParams.topMargin = dp(activity, 3);
        details.addView(sizeView, sizeParams);

        LinearLayout remove = new LinearLayout(activity);
        remove.setGravity(Gravity.CENTER);
        remove.setContentDescription("Gỡ " + name + " khỏi hàng chờ");
        remove.setBackgroundDrawable(MaterialUi.textButtonBackground(activity));
        MaterialIconView removeIcon = new MaterialIconView(activity,
                MaterialIconView.REMOVE);
        removeIcon.setTint(MaterialUi.ON_SURFACE_VARIANT);
        remove.addView(removeIcon, new LinearLayout.LayoutParams(
                dp(activity, 22), dp(activity, 22)));
        row.addView(remove, new LinearLayout.LayoutParams(
                dp(activity, 44), dp(activity, 48)));
        remove.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                onRemove.run();
            }
        });
        return row;
    }

    private static int iconFor(Activity activity, Uri uri) {
        try {
            String mime = activity.getContentResolver().getType(uri);
            if (mime != null && (mime.startsWith("image/") ||
                    mime.startsWith("video/") || mime.startsWith("audio/"))) {
                return MaterialIconView.MEDIA;
            }
        } catch (Exception ignored) {
        }
        return MaterialIconView.FILE;
    }

    private static long fileSize(Activity activity, Uri uri) {
        if (uri == null) {
            return -1L;
        }
        if ("file".equals(uri.getScheme())) {
            return new File(uri.getPath()).length();
        }
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri,
                    new String[] {"_size"}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex("_size");
                if (column >= 0 && !cursor.isNull(column)) {
                    return cursor.getLong(column);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = activity.getContentResolver()
                    .openAssetFileDescriptor(uri, "r");
            return descriptor == null ? -1L : descriptor.getLength();
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (descriptor != null) {
                try {
                    descriptor.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long utf8Length(String value) {
        try {
            return value.getBytes("UTF-8").length;
        } catch (Exception ignored) {
            return value.length();
        }
    }

    private static LinearLayout.LayoutParams rowParams(Activity activity) {
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(activity, 9);
        return params;
    }

    private static TextView text(Activity activity, String value, int sizeSp,
                                 int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
        }
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Activity activity, int value) {
        return MaterialUi.dp(activity, value);
    }
}
