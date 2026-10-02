package com.nearbyshare.legacy;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;

final class ShareIntentReaderApi16 {
    private ShareIntentReaderApi16() {
    }

    @TargetApi(16)
    static void appendClipUris(Intent intent, ArrayList<Uri> uris) {
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            return;
        }
        for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            ShareIntentReader.addUnique(uris, uri);
        }
    }
}