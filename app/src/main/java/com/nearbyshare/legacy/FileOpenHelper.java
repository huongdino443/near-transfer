package com.nearbyshare.legacy;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

final class FileOpenHelper {
    private FileOpenHelper() {
    }

    static void open(Context context, File file) {
        if (context == null) {
            return;
        }
        if (file == null || !file.isFile() || !DownloadFolders.contains(file)) {
            Toast.makeText(context, "Không tìm thấy tệp đã nhận.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        openWithApp(context, file);
    }

    private static void openWithApp(Context context, File file) {
        Uri uri = ReceivedFileProvider.uriForFile(file);
        String mimeType = context.getContentResolver().getType(uri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Mở tệp bằng"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Không có ứng dụng phù hợp để mở tệp này.",
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Toast.makeText(context, "Không thể cấp quyền mở tệp này.",
                    Toast.LENGTH_LONG).show();
        }
    }

}