package com.nearbyshare.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

final class TransferProgressScreen extends LinearLayout {
    interface Listener {
        void onClose();
    }

    private final ArrayList<Row> rows = new ArrayList<Row>();
    private final TransferProgressRingView totalProgress;
    private final TextView totalLabel;
    private final TextView elapsedLabel;
    private final TextView statusLabel;
    private final TextView heading;
    private final TextView itemsTitle;
    private final TextView resultTitle;
    private final TextView resultDetails;
    private final TextView resultMessage;
    private final MaterialIconView resultIcon;
    private final LinearLayout statusCard;
    private final LinearLayout resultCard;
    private final LinearLayout footer;
    private final LinearLayout actionRow;
    private final TextView completeActionLabel;
    private final Listener listener;
    private final boolean outgoing;
    private final Handler clock = new Handler();
    private final long startedAt = System.currentTimeMillis();
    private boolean finished;
    private boolean failed;

    private final Runnable clockTick = new Runnable() {
        public void run() {
            if (!finished) {
                updateTotalLabel();
                clock.postDelayed(this, 1000L);
            }
        }
    };

    TransferProgressScreen(Context context, String title,
                           ArrayList<IncomingOffer.Item> items, Listener listener) {
        super(context);
        this.listener = listener;
        outgoing = title != null && title.indexOf("gửi") >= 0;
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(MaterialUi.BACKGROUND);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 20), dp(context, 22),
                dp(context, 20), dp(context, 10));

        heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(MaterialUi.ON_SURFACE);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams headingParams = fullWidth();
        header.addView(heading, headingParams);
        addView(header, fullWidth());

        ScrollView listScroll = new ScrollView(context);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams listScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        addView(listScroll, listScrollParams);

        LinearLayout fileList = new LinearLayout(context);
        fileList.setOrientation(LinearLayout.VERTICAL);
        fileList.setPadding(dp(context, 20), dp(context, 2),
                dp(context, 20), dp(context, 16));
        listScroll.addView(fileList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(context, 15), dp(context, 15),
                dp(context, 15), dp(context, 15));
        statusCard.setBackgroundDrawable(MaterialUi.container(context,
                MaterialUi.SURFACE_CONTAINER, 16));
        LinearLayout.LayoutParams statusCardParams = fullWidth();
        statusCardParams.bottomMargin = dp(context, 20);
        fileList.addView(statusCard, statusCardParams);

        totalProgress = new TransferProgressRingView(context);
        statusCard.addView(totalProgress, new LinearLayout.LayoutParams(
                dp(context, 88), dp(context, 88)));

        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        summaryParams.leftMargin = dp(context, 15);
        statusCard.addView(summary, summaryParams);

        TextView totalCaption = new TextView(context);
        totalCaption.setText(outgoing ? "DỮ LIỆU ĐÃ GỬI" : "DỮ LIỆU ĐÃ NHẬN");
        totalCaption.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        totalCaption.setTextSize(11);
        totalCaption.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        summary.addView(totalCaption, fullWidth());

        totalLabel = new TextView(context);
        totalLabel.setTextColor(MaterialUi.ON_SURFACE);
        totalLabel.setTextSize(17);
        totalLabel.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams totalParams = fullWidth();
        totalParams.topMargin = dp(context, 5);
        summary.addView(totalLabel, totalParams);

        elapsedLabel = new TextView(context);
        elapsedLabel.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        elapsedLabel.setTextSize(13);
        LinearLayout.LayoutParams elapsedParams = fullWidth();
        elapsedParams.topMargin = dp(context, 4);
        summary.addView(elapsedLabel, elapsedParams);

        statusLabel = new TextView(context);
        statusLabel.setTextColor(MaterialUi.PRIMARY);
        statusLabel.setTextSize(13);
        statusLabel.setMaxLines(2);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(context, 6);
        summary.addView(statusLabel, statusParams);

        resultCard = new LinearLayout(context);
        resultCard.setOrientation(LinearLayout.HORIZONTAL);
        resultCard.setGravity(Gravity.CENTER_VERTICAL);
        resultCard.setPadding(dp(context, 16), dp(context, 16),
                dp(context, 16), dp(context, 16));
        resultCard.setBackgroundDrawable(MaterialUi.container(context,
                MaterialUi.SURFACE_CONTAINER, 16));
        resultCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultCardParams = fullWidth();
        resultCardParams.bottomMargin = dp(context, 20);
        fileList.addView(resultCard, resultCardParams);

        resultIcon = new MaterialIconView(context, MaterialIconView.CHECK);
        resultIcon.setTint(MaterialUi.PRIMARY);
        resultCard.addView(resultIcon, new LinearLayout.LayoutParams(
                dp(context, 52), dp(context, 52)));

        LinearLayout resultCopy = new LinearLayout(context);
        resultCopy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        resultCopyParams.leftMargin = dp(context, 14);
        resultCard.addView(resultCopy, resultCopyParams);

        resultTitle = new TextView(context);
        resultTitle.setTextColor(MaterialUi.ON_SURFACE);
        resultTitle.setTextSize(18);
        resultTitle.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        resultCopy.addView(resultTitle, fullWidth());

        resultDetails = new TextView(context);
        resultDetails.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        resultDetails.setTextSize(13);
        LinearLayout.LayoutParams resultDetailsParams = fullWidth();
        resultDetailsParams.topMargin = dp(context, 4);
        resultCopy.addView(resultDetails, resultDetailsParams);

        resultMessage = new TextView(context);
        resultMessage.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        resultMessage.setTextSize(13);
        resultMessage.setMaxLines(3);
        LinearLayout.LayoutParams resultMessageParams = fullWidth();
        resultMessageParams.topMargin = dp(context, 5);
        resultCopy.addView(resultMessage, resultMessageParams);

        LinearLayout sectionHeader = new LinearLayout(context);
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionHeaderParams = fullWidth();
        sectionHeaderParams.bottomMargin = dp(context, 8);
        fileList.addView(sectionHeader, sectionHeaderParams);

        itemsTitle = new TextView(context);
        itemsTitle.setText("NỘI DUNG");
        itemsTitle.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        itemsTitle.setTextSize(12);
        itemsTitle.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        sectionHeader.addView(itemsTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView itemCount = new TextView(context);
        itemCount.setText(items.size() + " mục");
        itemCount.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        itemCount.setTextSize(12);
        sectionHeader.addView(itemCount);

        for (int i = 0; i < items.size(); i++) {
            LinearLayout.LayoutParams rowParams = fullWidth();
            rowParams.bottomMargin = dp(context, 8);
            fileList.addView(createRow(context, i, items.get(i)), rowParams);
        }

        footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(context, 20), dp(context, 13),
                dp(context, 20), dp(context, 14));
        footer.setBackgroundColor(MaterialUi.SURFACE);

        actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(actionRow, fullWidth());

        LinearLayout advancedAction = (LinearLayout) createAction(
                context, "Chi tiết");
        advancedAction.setBackgroundDrawable(MaterialUi.stateBackground(context,
                MaterialUi.SURFACE_HIGH, MaterialUi.SURFACE_CONTAINER,
                MaterialUi.SURFACE_HIGH, 12));
        LinearLayout.LayoutParams advancedActionParams =
                new LinearLayout.LayoutParams(0, dp(context, 52), 1);
        advancedActionParams.rightMargin = dp(context, 4);
        actionRow.addView(advancedAction, advancedActionParams);
        advancedAction.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                showAdvancedDetails();
            }
        });

        LinearLayout completeAction = (LinearLayout) createAction(context, "Đóng");
        completeActionLabel = (TextView) completeAction.getChildAt(0);
        LinearLayout.LayoutParams completeActionParams =
                new LinearLayout.LayoutParams(0, dp(context, 52), 1.3f);
        completeActionParams.leftMargin = dp(context, 4);
        actionRow.addView(completeAction, completeActionParams);
        completeAction.setBackgroundDrawable(
                MaterialUi.primaryButtonBackground(context));
        completeActionLabel.setTextColor(MaterialUi.ON_PRIMARY);
        completeAction.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (TransferProgressScreen.this.listener != null) {
                    TransferProgressScreen.this.listener.onClose();
                }
            }
        });

        footer.setVisibility(View.GONE);
        addView(footer, fullWidth());
        updateTotalLabel();
        clock.postDelayed(clockTick, 1000L);
        loadThumbnails(context);
    }

    void setStatus(String value) {
        if (finished) {
            return;
        }
        if (value == null || value.length() == 0) {
            statusLabel.setText("Đang truyền nội dung…");
        } else {
            statusLabel.setText(value);
        }
        statusLabel.setTextColor(MaterialUi.PRIMARY);
        statusLabel.setVisibility(View.VISIBLE);
    }

    void updateProgress(int index, long transferred, long total) {
        if (finished || index < 0 || index >= rows.size()) {
            return;
        }
        Row row = rows.get(index);
        row.transferred = clamp(transferred, 0L, row.total);
        row.update();
        updateTotalLabel();
    }

    void completeItem(int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        Row row = rows.get(index);
        row.transferred = row.total;
        row.complete = true;
        row.update();
        updateTotalLabel();
    }

    void completeItem(int index, final File receivedFile) {
        completeItem(index);
        if (outgoing || receivedFile == null || !receivedFile.isFile() ||
                index < 0 || index >= rows.size()) {
            return;
        }
        final Row row = rows.get(index);
        row.receivedFile = receivedFile;
        row.container.setBackgroundDrawable(MaterialUi.stateBackground(getContext(),
                MaterialUi.SURFACE_CONTAINER, MaterialUi.SURFACE_HIGH,
                MaterialUi.SURFACE_CONTAINER, 14));
        row.container.setFocusable(true);
        row.container.setClickable(true);
        row.container.setContentDescription("Mở tệp " + receivedFile.getName());
        row.update();
        row.container.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                FileOpenHelper.open(getContext(), receivedFile);
            }
        });
    }

    void finish(String message) {
        if (finished) {
            return;
        }
        finished = true;
        failed = false;
        for (Row row : rows) {
            row.transferred = row.total;
            row.complete = true;
            row.update();
        }
        showResult(message);
        updateTotalLabel();
        clock.removeCallbacks(clockTick);
    }

    void fail(String message) {
        if (finished) {
            return;
        }
        finished = true;
        failed = true;
        for (Row row : rows) {
            row.update();
        }
        showResult(message);
        updateTotalLabel();
        clock.removeCallbacks(clockTick);
    }

    private void showResult(String message) {
        heading.setText("Kết quả truyền");
        statusCard.setVisibility(View.GONE);
        resultCard.setVisibility(View.VISIBLE);
        itemsTitle.setText(failed ? "NỘI DUNG CHƯA HOÀN TẤT" :
                "NỘI DUNG ĐÃ CHUYỂN");
        resultTitle.setText(failed ? "Truyền bị gián đoạn" :
                (outgoing ? "Đã gửi xong" : "Đã nhận xong"));

        long total = 0L;
        long transferred = 0L;
        for (Row row : rows) {
            total += row.total;
            transferred += row.complete ? row.total : row.transferred;
        }
        long seconds = Math.max(0L,
                (System.currentTimeMillis() - startedAt) / 1000L);
        String sizeSummary = failed ?
                IncomingOffer.formatSize(transferred) + " / " +
                        IncomingOffer.formatSize(total) :
                IncomingOffer.formatSize(total);
        String summary = rows.size() + " mục · " + sizeSummary +
                " · " + formatElapsed(seconds);
        resultDetails.setText(summary);
        resultMessage.setText(message == null || message.length() == 0 ?
                (failed ? "Bạn có thể thử lại sau khi kiểm tra kết nối." :
                        "Tất cả nội dung trong danh sách đã được chuyển.") : message);
        resultMessage.setTextColor(failed ? MaterialUi.ERROR :
                MaterialUi.ON_SURFACE_VARIANT);

        resultIcon.setIcon(failed ? MaterialIconView.INFO : MaterialIconView.CHECK);
        resultIcon.setTint(failed ? MaterialUi.ERROR : MaterialUi.PRIMARY);
        completeActionLabel.setText("Đóng");
        footer.setVisibility(View.VISIBLE);
    }

    boolean isFinished() {
        return finished;
    }

    private View createRow(Context context, int index, IncomingOffer.Item item) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(context, 12), dp(context, 11),
                dp(context, 12), dp(context, 11));
        outer.setBackgroundDrawable(MaterialUi.container(context,
                MaterialUi.SURFACE_CONTAINER, 14));
        FrameLayout thumbnailBox = new FrameLayout(context);
        thumbnailBox.setBackgroundDrawable(MaterialUi.rounded(context,
                MaterialUi.SURFACE_HIGH, 8));
        ImageView thumbnail = new ImageView(context);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setVisibility(View.GONE);
        thumbnailBox.addView(thumbnail, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialIconView placeholderIcon = new MaterialIconView(context,
                isMediaFile(item.name) ? MaterialIconView.MEDIA : MaterialIconView.FILE);
        placeholderIcon.setTint(MaterialUi.PRIMARY);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(context, 32), dp(context, 32), Gravity.CENTER);
        thumbnailBox.addView(placeholderIcon, iconParams);
        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailParams.rightMargin = dp(context, 14);
        outer.addView(details, detailParams);

        LinearLayout metadata = new LinearLayout(context);
        metadata.setOrientation(LinearLayout.HORIZONTAL);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        details.addView(metadata, fullWidth());

        TextView name = new TextView(context);
        name.setText(item.name);
        name.setTextColor(MaterialUi.ON_SURFACE);
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        metadata.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView size = new TextView(context);
        size.setText(IncomingOffer.formatSize(item.size));
        size.setTextColor(MaterialUi.ON_SURFACE_VARIANT);
        size.setTextSize(14);
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeParams.leftMargin = dp(context, 6);
        metadata.addView(size, sizeParams);

        TextView state = new TextView(context);
        state.setTextColor(MaterialUi.PRIMARY);
        state.setTextSize(14);
        LinearLayout.LayoutParams stateParams = fullWidth();
        stateParams.topMargin = dp(context, 2);
        details.addView(state, stateParams);

        ProgressBar progress = progressBar(context);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 5));
        progressParams.topMargin = dp(context, 7);
        details.addView(progress, progressParams);

        outer.addView(thumbnailBox, new LinearLayout.LayoutParams(
                dp(context, 56), dp(context, 56)));

        Row row = new Row(index, item, outer, progress, state, thumbnail,
                placeholderIcon);
        row.update();
        rows.add(row);
        return outer;
    }

    private View createAction(Context context, String label) {
        LinearLayout action = new LinearLayout(context);
        action.setOrientation(LinearLayout.HORIZONTAL);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        action.setFocusable(true);
        action.setClickable(true);
        action.setBackgroundDrawable(MaterialUi.textButtonBackground(context));
        action.setContentDescription(label);

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(MaterialUi.ON_SURFACE);
        text.setTextSize(15);
        action.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return action;
    }

    private void showAdvancedDetails() {
        long totalBytes = 0L;
        for (Row row : rows) {
            totalBytes += row.total;
        }
        long seconds = Math.max(0L,
                (System.currentTimeMillis() - startedAt) / 1000L);
        StringBuilder details = new StringBuilder();
        details.append(failed ? "Trạng thái: Có lỗi\n" : "Trạng thái: Đã xong\n");
        details.append("Số tập tin: ").append(rows.size()).append('\n');
        details.append("Tổng dung lượng: ")
                .append(IncomingOffer.formatSize(totalBytes)).append('\n');
        details.append("Thời gian: ").append(formatElapsed(seconds));
        if (rows.size() > 0) {
            details.append("\n\n");
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                details.append(i + 1).append(". ").append(row.item.name)
                        .append(" — ").append(IncomingOffer.formatSize(row.total));
                if (i + 1 < rows.size()) {
                    details.append('\n');
                }
            }
        }
        MaterialDialog.show(getContext(), "Chi tiết truyền",
                details.toString(), null, "Đóng", null, null, null);
    }

    private void loadThumbnails(Context context) {
        boolean hasPreviews = false;
        for (Row row : rows) {
            if (row.item.previewUri != null) {
                hasPreviews = true;
                break;
            }
        }
        if (!hasPreviews) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                for (final Row row : rows) {
                    if (row.item.previewUri == null) {
                        continue;
                    }
                    final Bitmap bitmap = decodeThumbnail(appContext, row.item.previewUri);
                    if (bitmap != null) {
                        clock.post(new Runnable() {
                            public void run() {
                                row.thumbnail.setImageBitmap(bitmap);
                                row.thumbnail.setVisibility(View.VISIBLE);
                                row.placeholderIcon.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            }
        }, "nearby-thumbnails").start();
    }

    private static Bitmap decodeThumbnail(Context context, Uri uri) {
        InputStream input = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            input = openPreviewStream(context, uri);
            if (input == null) {
                return null;
            }
            BitmapFactory.decodeStream(input, null, bounds);
            input.close();
            input = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sampleSize = 1;
            while (bounds.outWidth / sampleSize > 160 ||
                    bounds.outHeight / sampleSize > 160) {
                sampleSize *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            input = openPreviewStream(context, uri);
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static InputStream openPreviewStream(Context context, Uri uri)
            throws Exception {
        if ("file".equals(uri.getScheme())) {
            return new FileInputStream(uri.getPath());
        }
        return context.getContentResolver().openInputStream(uri);
    }

    private void updateTotalLabel() {
        long total = 0L;
        long transferred = 0L;
        for (Row row : rows) {
            total += row.total;
            transferred += row.complete ? row.total : row.transferred;
        }
        totalProgress.setProgress(total == 0L ?
                (finished && !failed ? 1000 : 0) :
                (int) Math.min(1000L, transferred * 1000L / total));

        long seconds = Math.max(0L,
                (System.currentTimeMillis() - startedAt) / 1000L);
        totalLabel.setText(IncomingOffer.formatSize(transferred) + " / " +
                IncomingOffer.formatSize(total));
        elapsedLabel.setText(rows.size() + " nội dung · " + formatElapsed(seconds));
    }

    private static boolean isMediaFile(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".webp") || lower.endsWith(".mp4") ||
                lower.endsWith(".3gp") || lower.endsWith(".mov");
    }

    private static String formatElapsed(long seconds) {
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, remainder);
    }

    private static ProgressBar progressBar(Context context) {
        ProgressBar progress = new ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressDrawable(MaterialUi.progressDrawable(context));
        progress.setMinimumHeight(dp(context, 6));
        return progress;
    }

    private static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static long clamp(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class Row {
        final int index;
        final IncomingOffer.Item item;
        final long total;
        final View container;
        final ProgressBar progress;
        final TextView state;
        final ImageView thumbnail;
        final MaterialIconView placeholderIcon;
        File receivedFile;
        long transferred;
        boolean complete;

        Row(int index, IncomingOffer.Item item, View container, ProgressBar progress,
            TextView state, ImageView thumbnail, MaterialIconView placeholderIcon) {
            this.index = index;
            this.item = item;
            this.total = item.size;
            this.container = container;
            this.progress = progress;
            this.state = state;
            this.thumbnail = thumbnail;
            this.placeholderIcon = placeholderIcon;
        }

        void update() {
            progress.setMax(1000);
            progress.setProgress(total == 0L ?
                    (complete ? 1000 : 0) :
                    (int) Math.min(1000L, transferred * 1000L / total));
            if (complete) {
                state.setText(receivedFile == null ?
                        "Đã xong" : "Đã xong · Chạm để mở");
                state.setTextColor(MaterialUi.PRIMARY);
                progress.setVisibility(View.GONE);
            } else if (TransferProgressScreen.this.failed) {
                state.setText("Đã dừng");
                state.setTextColor(MaterialUi.ERROR);
                progress.setVisibility(View.VISIBLE);
            } else {
                progress.setVisibility(View.VISIBLE);
                state.setTextColor(MaterialUi.PRIMARY);
                if (transferred > 0L) {
                    state.setText("Đang truyền · " +
                            IncomingOffer.formatSize(transferred) + " / " +
                            IncomingOffer.formatSize(total));
                } else {
                    state.setText("Đang chờ");
                }
            }
        }
    }
}