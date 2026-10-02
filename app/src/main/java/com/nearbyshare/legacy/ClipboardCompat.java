package com.nearbyshare.legacy;

import android.content.Context;
import android.os.Build;

final class ClipboardCompat {
    private ClipboardCompat() {
    }

    static String readText(Context context) {
        if (Build.VERSION.SDK_INT >= 11) {
            return ClipboardApi11.readText(context);
        }
        android.text.ClipboardManager clipboard =
                (android.text.ClipboardManager) context.getSystemService(
                        Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasText()) {
            return null;
        }
        CharSequence text = clipboard.getText();
        return text == null ? null : text.toString();
    }

    static void copyText(Context context, String text) {
        if (Build.VERSION.SDK_INT >= 11) {
            ClipboardApi11.copyText(context, text);
            return;
        }
        android.text.ClipboardManager clipboard =
                (android.text.ClipboardManager) context.getSystemService(
                        Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setText(text);
        }
    }
}