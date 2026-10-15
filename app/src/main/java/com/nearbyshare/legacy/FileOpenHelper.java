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

    static void openContainingFolder(Context context, File file) {
        if (context == null) {
            return;
        }
        if (file == null || !file.isFile() || !DownloadFolders.contains(file)) {
            Toast.makeText(context, "Không tìm thấy tệp đã nhận.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File folder = file.getParentFile();
        if (folder == null || !folder.isDirectory()) {
            Toast.makeText(context, "Không tìm thấy thư mục chứa tệp.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri;
        try {
            uri = ReceivedFileProvider.uriForReceivedDirectory(folder);
        } catch (IllegalArgumentException e) {
            Toast.makeText(context, "Không tìm thấy thư mục chứa tệp.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Không có ứng dụng tương thích để mở thư mục này.",
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Toast.makeText(context, "Không thể cấp quyền mở thư mục này.",
                    Toast.LENGTH_LONG).show();
        }
    }

}