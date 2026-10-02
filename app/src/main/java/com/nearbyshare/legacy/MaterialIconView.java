package com.nearbyshare.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class MaterialIconView extends View {
    static final int FILE = 1;
    static final int MEDIA = 2;
    static final int CLIPBOARD = 3;
    static final int EDIT = 4;
    static final int DEVICE = 5;
    static final int INFO = 6;
    static final int CHECK = 7;
    static final int BOOKMARK = 8;
    static final int REMOVE = 9;

    private int icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int tint = MaterialUi.PRIMARY;

    MaterialIconView(Context context, int icon) {
        super(context);
        this.icon = icon;
        paint.setColor(tint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setTint(int color) {
        tint = color;
        invalidate();
    }

    void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) * 0.72f;
        if (size <= 0f) {
            return;
        }
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(tint);
        paint.setStrokeWidth(1.8f);

        if (icon == FILE) {
            drawFile(canvas);
        } else if (icon == MEDIA) {
            drawMedia(canvas);
        } else if (icon == CLIPBOARD) {
            drawClipboard(canvas);
        } else if (icon == EDIT) {
            drawEdit(canvas);
        } else if (icon == DEVICE) {
            drawDevice(canvas);
        } else if (icon == INFO) {
            drawInfo(canvas);
        } else if (icon == CHECK) {
            drawCheck(canvas);
        } else if (icon == BOOKMARK) {
            drawBookmark(canvas);
        } else if (icon == REMOVE) {
            drawRemove(canvas);
        }
        canvas.restore();
    }

    private void drawFile(Canvas canvas) {
        path.reset();
        path.moveTo(7f, 3.5f);
        path.lineTo(14.5f, 3.5f);
        path.lineTo(19.5f, 8.5f);
        path.lineTo(19.5f, 20.5f);
        path.quadTo(19.5f, 21.5f, 18.5f, 21.5f);
        path.lineTo(6f, 21.5f);
        path.quadTo(5f, 21.5f, 5f, 20.5f);
        path.lineTo(5f, 5.5f);
        path.quadTo(5f, 3.5f, 7f, 3.5f);
        path.close();
        canvas.drawPath(path, paint);
        canvas.drawLine(14.5f, 3.8f, 14.5f, 8.8f, paint);
        canvas.drawLine(14.5f, 8.8f, 19.2f, 8.8f, paint);
        canvas.drawLine(8f, 13f, 16f, 13f, paint);
        canvas.drawLine(8f, 16.5f, 16f, 16.5f, paint);
    }

    private void drawMedia(Canvas canvas) {
        canvas.drawRoundRect(new RectF(3f, 4f, 21f, 20f), 2f, 2f, paint);
        canvas.drawCircle(9f, 9f, 1.7f, paint);
        path.reset();
        path.moveTo(4.5f, 17.5f);
        path.lineTo(9.5f, 12.5f);
        path.lineTo(13f, 16f);
        path.lineTo(15.5f, 13.5f);
        path.lineTo(20f, 18f);
        canvas.drawPath(path, paint);
    }

    private void drawClipboard(Canvas canvas) {
        canvas.drawRoundRect(new RectF(5f, 5f, 19f, 21f), 2f, 2f, paint);
        canvas.drawRoundRect(new RectF(8f, 3f, 16f, 7f), 1.5f, 1.5f, paint);
        canvas.drawLine(8f, 11f, 16f, 11f, paint);
        canvas.drawLine(8f, 14.5f, 16f, 14.5f, paint);
        canvas.drawLine(8f, 18f, 13f, 18f, paint);
    }

    private void drawEdit(Canvas canvas) {
        path.reset();
        path.moveTo(4f, 17.2f);
        path.lineTo(16.8f, 4.4f);
        path.quadTo(17.6f, 3.6f, 18.4f, 4.4f);
        path.lineTo(20f, 6f);
        path.quadTo(20.8f, 6.8f, 20f, 7.6f);
        path.lineTo(7.2f, 20.4f);
        path.lineTo(3.5f, 21f);
        path.close();
        canvas.drawPath(path, paint);
        canvas.drawLine(14.5f, 6.7f, 17.8f, 10f, paint);
    }

    private void drawDevice(Canvas canvas) {
        canvas.drawRoundRect(new RectF(6f, 2.5f, 18f, 21.5f), 2.5f, 2.5f, paint);
        canvas.drawLine(10f, 5f, 14f, 5f, paint);
        canvas.drawCircle(12f, 18.5f, 0.8f, paint);
    }

    private void drawInfo(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tint);
        canvas.drawCircle(12f, 12f, 10f, paint);
        paint.setColor(MaterialUi.ON_PRIMARY);
        canvas.drawCircle(12f, 7.3f, 1.1f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.1f);
        canvas.drawLine(12f, 10.5f, 12f, 17f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(tint);
        paint.setStrokeWidth(1.8f);
    }

    private void drawCheck(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tint);
        canvas.drawCircle(12f, 12f, 10f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(MaterialUi.ON_PRIMARY);
        paint.setStrokeWidth(2.2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        path.moveTo(6.8f, 12.4f);
        path.lineTo(10.4f, 16f);
        path.lineTo(17.4f, 8.5f);
        canvas.drawPath(path, paint);
        paint.setColor(tint);
        paint.setStrokeWidth(1.8f);
    }

    private void drawBookmark(Canvas canvas) {
        path.reset();
        path.moveTo(7f, 3.5f);
        path.lineTo(17f, 3.5f);
        path.quadTo(18.5f, 3.5f, 18.5f, 5f);
        path.lineTo(18.5f, 21f);
        path.lineTo(12f, 16.5f);
        path.lineTo(5.5f, 21f);
        path.lineTo(5.5f, 5f);
        path.quadTo(5.5f, 3.5f, 7f, 3.5f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawRemove(Canvas canvas) {
        canvas.drawRoundRect(new RectF(5f, 7f, 19f, 21f), 1.5f, 1.5f, paint);
        canvas.drawLine(3.5f, 5.5f, 20.5f, 5.5f, paint);
        canvas.drawLine(9f, 3.5f, 15f, 3.5f, paint);
        canvas.drawLine(9.5f, 10f, 9.5f, 18f, paint);
        canvas.drawLine(14.5f, 10f, 14.5f, 18f, paint);
    }
}