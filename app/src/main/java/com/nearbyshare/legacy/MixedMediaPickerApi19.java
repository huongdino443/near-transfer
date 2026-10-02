package com.nearbyshare.legacy;

import android.annotation.TargetApi;
import android.content.Intent;

@TargetApi(19)
final class MixedMediaPickerApi19 {
    private MixedMediaPickerApi19() {
    }

    static void setImageAndVideoTypes(Intent intent) {
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"image/*", "video/*"});
    }
}