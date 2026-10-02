package com.nearbyshare.legacy;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

@TargetApi(11)
final class ClipboardApi11 {
    private ClipboardApi11() {
    }

    static String readText(Context context) {
        if (Build.VERSION.SDK_INT < 11) {
            return null;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return null;
        }
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) {
            return null;
        }
        CharSequence value = data.getItemAt(0).coerceToText(context);
        return value == null ? null : value.toString();
    }

    static void copyText(Context context, String text) {
        if (Build.VERSION.SDK_INT < 11) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Near Transfer", text));
        }
    }
}