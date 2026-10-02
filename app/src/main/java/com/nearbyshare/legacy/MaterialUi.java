package com.nearbyshare.legacy;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;

final class MaterialUi {
    static final int BACKGROUND = Color.rgb(25, 26, 31);
    static final int SURFACE = Color.rgb(29, 30, 36);
    static final int SURFACE_CONTAINER = Color.rgb(34, 36, 43);
    static final int SURFACE_HIGH = Color.rgb(45, 48, 57);
    static final int PRIMARY = Color.rgb(184, 200, 255);
    static final int PRIMARY_PRESSED = Color.rgb(160, 179, 240);
    static final int ON_PRIMARY = Color.rgb(27, 37, 60);
    static final int ON_SURFACE = Color.rgb(230, 228, 236);
    static final int ON_SURFACE_VARIANT = Color.rgb(191, 195, 207);
    static final int ERROR = Color.rgb(255, 180, 171);
    static final int ERROR_CONTAINER = Color.rgb(77, 39, 42);

    private MaterialUi() {
    }

    static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    static GradientDrawable container(Context context, int color, int radiusDp) {
        return rounded(context, color, radiusDp);
    }

    static Drawable primaryButtonBackground(Context context) {
        return stateBackground(context, PRIMARY, PRIMARY_PRESSED,
                Color.rgb(63, 68, 79), 12);
    }

    static Drawable surfaceButtonBackground(Context context) {
        return stateBackground(context, SURFACE_CONTAINER, SURFACE_HIGH,
                SURFACE_CONTAINER, 10);
    }

    static Drawable textButtonBackground(Context context) {
        return stateBackground(context, Color.TRANSPARENT,
                Color.rgb(48, 54, 68), Color.TRANSPARENT, 10);
    }

    static Drawable stateBackground(Context context, int normal, int pressed,
                                    int disabled, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {-android.R.attr.state_enabled},
                rounded(context, disabled, radiusDp));
        states.addState(new int[] {android.R.attr.state_pressed},
                rounded(context, pressed, radiusDp));
        states.addState(new int[0], rounded(context, normal, radiusDp));
        return states;
    }

    static void stylePrimaryButton(Context context, Button button) {
        button.setTextColor(ON_PRIMARY);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 18), dp(context, 8),
                dp(context, 18), dp(context, 8));
        button.setTransformationMethod(null);
        button.setBackgroundDrawable(primaryButtonBackground(context));
    }

    static void styleEditText(Context context, EditText input) {
        input.setTextColor(ON_SURFACE);
        input.setHintTextColor(ON_SURFACE_VARIANT);
        input.setTextSize(16);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setMinHeight(dp(context, 48));
        input.setPadding(dp(context, 14), dp(context, 12),
                dp(context, 14), dp(context, 12));
        StateListDrawable backgrounds = new StateListDrawable();
        GradientDrawable focused = rounded(context, SURFACE_HIGH, 10);
        focused.setStroke(dp(context, 1), PRIMARY);
        backgrounds.addState(new int[] {android.R.attr.state_focused}, focused);
        backgrounds.addState(new int[0], rounded(context, SURFACE_HIGH, 10));
        input.setBackgroundDrawable(backgrounds);
    }

    static Drawable progressDrawable(Context context) {
        GradientDrawable track = rounded(context, SURFACE_HIGH, 8);
        GradientDrawable fill = rounded(context, PRIMARY, 8);
        ClipDrawable clippedFill = new ClipDrawable(fill, Gravity.LEFT,
                ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {track, clippedFill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        layers.setLayerInset(1, dp(context, 1), dp(context, 1),
                dp(context, 1), dp(context, 1));
        return layers;
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}