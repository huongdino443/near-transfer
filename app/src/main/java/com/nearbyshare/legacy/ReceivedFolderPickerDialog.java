package com.nearbyshare.legacy;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

final class ReceivedFolderPickerDialog {
    interface Listener {
        boolean onFolderChosen(File directory);
    }

    private ReceivedFolderPickerDialog() {
    }

    static void show(final Activity activity, File initialDirectory,
                     final Listener listener) {
        final File rootDirectory = canonical(DownloadFolders.externalStorageRoot());
        File start = initialDirectory;
        if (start == null || !start.isDirectory() ||
                !DownloadFolders.isInsideExternalStorage(start)) {
            File defaultDirectory = DownloadFolders.defaultReceivedDirectory();
            start = defaultDirectory.getParentFile();
        }
        if (start == null || !start.isDirectory()) {
            start = rootDirectory;
        }
        final File[] currentDirectory = new File[] {canonical(start)};

        final Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 18), dp(activity, 18),
                dp(activity, 18), dp(activity, 14));
        panel.setBackgroundDrawable(MaterialUi.container(
                activity, MaterialUi.SURFACE_CONTAINER, 16));

        TextView title = new TextView(activity);
        title.setText("Chọn thư mục lưu");
        title.setTextColor(MaterialUi.ON_SURFACE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title, fullWidth());

        final TextView currentPath = new TextView(activity);
        currentPath.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        currentPath.setTextSize(12);
        currentPath.setMaxLines(2);
        currentPath.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams pathParams = fullWidth();
        pathParams.topMargin = dp(activity, 8);
        panel.addView(currentPath, pathParams);

        final TextView upButton = new TextView(activity);
        upButton.setText("↑  Thư mục cha");
        upButton.setTextColor(MaterialUi.PRIMARY);
        upButton.setGravity(Gravity.CENTER_VERTICAL);
        upButton.setPadding(dp(activity, 4), dp(activity, 10),
                dp(activity, 4), dp(activity, 10));
        upButton.setFocusable(true);
        upButton.setClickable(true);
        LinearLayout.LayoutParams upParams = fullWidth();
        upParams.topMargin = dp(activity, 4);
        panel.addView(upButton, upParams);

        final LinearLayout directoryRows = new LinearLayout(activity);
        directoryRows.setOrientation(LinearLayout.VERTICAL);
        ScrollView directoryScroll = new ScrollView(activity);
        directoryScroll.setFillViewport(false);
        directoryScroll.setVerticalScrollBarEnabled(true);
        directoryScroll.addView(directoryRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = fullWidth();
        scrollParams.height = dp(activity, 250);
        panel.addView(directoryScroll, scrollParams);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            public void run() {
                currentPath.setText(currentDirectory[0].getAbsolutePath());
                File parent = currentDirectory[0].getParentFile();
                boolean canGoUp = parent != null &&
                        isInside(rootDirectory, parent) &&
                        !currentDirectory[0].equals(rootDirectory);
                upButton.setEnabled(canGoUp);
                upButton.setTextColor(canGoUp ?
                        MaterialUi.PRIMARY : MaterialUi.ON_SURFACE_VARIANT);
                directoryRows.removeAllViews();

                File[] children = currentDirectory[0].listFiles();
                ArrayList<File> directories = new ArrayList<File>();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory() && child.canRead() &&
                                !child.getName().startsWith(".") &&
                                isInside(rootDirectory, child)) {
                            directories.add(child);
                        }
                    }
                }
                Collections.sort(directories, new Comparator<File>() {
                    public int compare(File left, File right) {
                        return left.getName().compareToIgnoreCase(right.getName());
                    }
                });
                if (directories.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("Không có thư mục con.");
                    empty.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
                    empty.setTextSize(14);
                    empty.setPadding(dp(activity, 8), dp(activity, 16),
                            dp(activity, 8), dp(activity, 16));
                    directoryRows.addView(empty, fullWidth());
                    return;
                }
                for (final File child : directories) {
                    TextView row = new TextView(activity);
                    row.setText(child.getName());
                    row.setTextColor(MaterialUi.ON_SURFACE);
                    row.setTextSize(15);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinHeight(dp(activity, 48));
                    row.setPadding(dp(activity, 10), dp(activity, 6),
                            dp(activity, 10), dp(activity, 6));
                    row.setBackgroundDrawable(MaterialUi.surfaceButtonBackground(activity));
                    row.setFocusable(true);
                    row.setClickable(true);
                    LinearLayout.LayoutParams rowParams = fullWidth();
                    rowParams.bottomMargin = dp(activity, 4);
                    directoryRows.addView(row, rowParams);
                    row.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            currentDirectory[0] = canonical(child);
                            refresh[0].run();
                            directoryScroll.scrollTo(0, 0);
                        }
                    });
                }
            }
        };

        upButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                File parent = currentDirectory[0].getParentFile();
                if (parent != null && isInside(rootDirectory, parent)) {
                    currentDirectory[0] = canonical(parent);
                    refresh[0].run();
                    directoryScroll.scrollTo(0, 0);
                }
            }
        });

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = fullWidth();
        actionsParams.topMargin = dp(activity, 12);
        panel.addView(actions, actionsParams);

        TextView cancelButton = new TextView(activity);
        cancelButton.setText("Hủy");
        cancelButton.setTextColor(MaterialUi.PRIMARY);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setMinWidth(dp(activity, 72));
        cancelButton.setMinHeight(dp(activity, 48));
        cancelButton.setFocusable(true);
        cancelButton.setClickable(true);
        actions.addView(cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        Button selectButton = new Button(activity);
        selectButton.setText("Chọn thư mục này");
        MaterialUi.stylePrimaryButton(activity, selectButton);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 48));
        selectParams.leftMargin = dp(activity, 8);
        actions.addView(selectButton, selectParams);
        selectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (listener == null || listener.onFolderChosen(currentDirectory[0])) {
                    dialog.dismiss();
                }
            }
        });

        refresh[0].run();
        dialog.setContentView(panel);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(dp(activity, 420),
                    activity.getResources().getDisplayMetrics().widthPixels -
                            dp(activity, 32));
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.64f;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private static boolean isInside(File root, File candidate) {
        try {
            String rootPath = root.getCanonicalPath();
            String candidatePath = candidate.getCanonicalPath();
            return candidatePath.equals(rootPath) ||
                    candidatePath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static File canonical(File file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Activity activity, int value) {
        return MaterialUi.dp(activity, value);
    }
}
