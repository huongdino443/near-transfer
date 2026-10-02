package com.nearbyshare.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class TransferProgressRingView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private int progress;

    TransferProgressRingView(Context context) {
        super(context);
        setContentDescription("Tiến độ truyền: 0 phần trăm");
        textPaint.setColor(MaterialUi.ON_SURFACE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(18f *
                context.getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    void setProgress(int value) {
        progress = Math.max(0, Math.min(1000, value));
        setContentDescription("Tiến độ truyền: " + (progress / 10) + " phần trăm");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = 7f * getResources().getDisplayMetrics().density;
        float inset = stroke / 2f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(MaterialUi.SURFACE_HIGH);
        canvas.drawArc(bounds, 0f, 360f, false, ringPaint);

        if (progress > 0) {
            ringPaint.setColor(MaterialUi.PRIMARY);
            canvas.drawArc(bounds, -90f, 360f * progress / 1000f,
                    false, ringPaint);
        }

        String value = (progress / 10) + "%";
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = getHeight() / 2f -
                (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, getWidth() / 2f, baseline, textPaint);
    }
}