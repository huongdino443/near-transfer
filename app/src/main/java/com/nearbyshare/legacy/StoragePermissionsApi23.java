package com.nearbyshare.legacy;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

@TargetApi(23)
final class StoragePermissionsApi23 {
    private StoragePermissionsApi23() {
    }

    static boolean granted(Activity activity) {
        return Build.VERSION.SDK_INT < 23 ||
                (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED &&
                        activity.checkSelfPermission(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                                PackageManager.PERMISSION_GRANTED);
    }

    static void request(Activity activity, int requestCode) {
        activity.requestPermissions(new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, requestCode);
    }
}