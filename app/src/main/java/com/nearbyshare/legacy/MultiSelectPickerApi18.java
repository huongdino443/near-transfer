package com.nearbyshare.legacy;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;

@TargetApi(18)
final class MultiSelectPickerApi18 {
    private MultiSelectPickerApi18() {
    }

    static Intent createIntent() {
        return createIntent("*/*");
    }

    static Intent createIntent(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    static ArrayList<Uri> getSelectedUris(Intent result) {
        ArrayList<Uri> uris = new ArrayList<Uri>();
        if (result == null) {
            return uris;
        }

        ClipData clipData = result.getClipData();
        if (Build.VERSION.SDK_INT >= 18 && clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                addUnique(uris, uri);
            }
        }
        addUnique(uris, result.getData());
        return uris;
    }

    private static void addUnique(ArrayList<Uri> uris, Uri uri) {
        if (uri == null) {
            return;
        }
        for (Uri existing : uris) {
            if (existing.toString().equals(uri.toString())) {
                return;
            }
        }
        uris.add(uri);
    }
}