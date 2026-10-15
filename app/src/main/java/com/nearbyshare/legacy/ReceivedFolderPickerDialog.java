package com.nearbyshare.legacy;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
                dp(activity, 18), dp(activity, 12));
        panel.setBackgroundColor(MaterialUi.BACKGROUND);

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(toolbar, fullWidth());

        TextView upButton = new TextView(activity);
        upButton.setText("↑");
        upButton.setTextSize(26);
        upButton.setTextColor(MaterialUi.ON_SURFACE);
        upButton.setGravity(Gravity.CENTER);
        upButton.setBackgroundDrawable(MaterialUi.surfaceButtonBackground(activity));
        upButton.setFocusable(true);
        upButton.setClickable(true);
        toolbar.addView(upButton, new LinearLayout.LayoutParams(dp(activity, 56),
                dp(activity, 56)));

        final TextView currentPath = new TextView(activity);
        currentPath.setGravity(Gravity.CENTER_VERTICAL);
        currentPath.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        currentPath.setTextSize(16);
        currentPath.setSingleLine(true);
        currentPath.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        currentPath.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        currentPath.setBackgroundDrawable(MaterialUi.container(
                activity, MaterialUi.SURFACE_CONTAINER, 24));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                0, dp(activity, 56), 1);
        pathParams.leftMargin = dp(activity, 12);
        pathParams.rightMargin = dp(activity, 12);
        toolbar.addView(currentPath, pathParams);

        final boolean[] reverseSort = new boolean[] {false};
        TextView sortButton = new TextView(activity);
        sortButton.setText("≡");
        sortButton.setTextSize(24);
        sortButton.setTextColor(MaterialUi.ON_SURFACE);
        sortButton.setGravity(Gravity.CENTER);
        sortButton.setBackgroundDrawable(MaterialUi.surfaceButtonBackground(activity));
        sortButton.setFocusable(true);
        sortButton.setClickable(true);
        toolbar.addView(sortButton, new LinearLayout.LayoutParams(dp(activity, 56),
                dp(activity, 56)));
        TextView subtitle = new TextView(activity);
        subtitle.setText("Chọn vị trí lưu");
        subtitle.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = fullWidth();
        subtitleParams.topMargin = dp(activity, 12);
        subtitleParams.bottomMargin = dp(activity, 4);
        panel.addView(subtitle, subtitleParams);

        final LinearLayout directoryRows = new LinearLayout(activity);
        final Button[] selectButtonRef = new Button[1];
        directoryRows.setOrientation(LinearLayout.VERTICAL);
        ScrollView directoryScroll = new ScrollView(activity);
        directoryScroll.setFillViewport(false);
        directoryScroll.setVerticalScrollBarEnabled(true);
        directoryScroll.addView(directoryRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
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
                        MaterialUi.ON_SURFACE : MaterialUi.ON_SURFACE_VARIANT);
                boolean canSelect = DownloadFolders.isBelowExternalStorageRoot(
                        currentDirectory[0]) &&
                        currentDirectory[0].isDirectory() &&
                        currentDirectory[0].canWrite();
                if (selectButtonRef[0] != null) {
                    selectButtonRef[0].setEnabled(canSelect);
                }
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
                        int order = left.getName().compareToIgnoreCase(right.getName());
                        return reverseSort[0] ? -order : order;
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
                    LinearLayout row = new LinearLayout(activity);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(activity, 12), dp(activity, 8),
                            dp(activity, 8), dp(activity, 8));
                    row.setBackgroundDrawable(MaterialUi.textButtonBackground(activity));
                    row.setFocusable(true);
                    row.setClickable(true);
                    directoryRows.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 76)));
                    FrameLayout iconSurface = new FrameLayout(activity);
                    iconSurface.setBackgroundDrawable(MaterialUi.container(
                            activity, MaterialUi.SURFACE_HIGH, 12));
                    row.addView(iconSurface, new LinearLayout.LayoutParams(
                            dp(activity, 56), dp(activity, 56)));
                    MaterialIconView folderIcon = new MaterialIconView(
                            activity, MaterialIconView.FOLDER);
                    folderIcon.setTint(MaterialUi.ON_SURFACE_VARIANT);
                    iconSurface.addView(folderIcon, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    TextView name = new TextView(activity);
                    name.setText(child.getName() + "/");
                    name.setTextColor(MaterialUi.ON_SURFACE);
                    name.setTextSize(16);
                    name.setGravity(Gravity.CENTER_VERTICAL);
                    name.setSingleLine(true);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    nameParams.leftMargin = dp(activity, 16);
                    row.addView(name, nameParams);
                    row.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            currentDirectory[0] = canonical(child);
                            refresh[0].run();
                            directoryScroll.scrollTo(0, 0);
                        }
                    });
                    View divider = new View(activity);
                    divider.setBackgroundColor(MaterialUi.SURFACE_HIGH);
                    directoryRows.addView(divider, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)));
                }
            }
        };

        sortButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                reverseSort[0] = !reverseSort[0];
                refresh[0].run();
            }
        });
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
        cancelButton.setTextColor(MaterialUi.ON_SURFACE);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setMinWidth(dp(activity, 84));
        cancelButton.setMinHeight(dp(activity, 48));
        cancelButton.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        cancelButton.setBackgroundDrawable(MaterialUi.surfaceButtonBackground(activity));
        cancelButton.setFocusable(true);
        cancelButton.setClickable(true);
        actions.addView(cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        Button selectButton = new Button(activity);
        selectButtonRef[0] = selectButton;
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
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0f;
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
