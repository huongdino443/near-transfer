package com.nearbyshare.legacy;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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

        final File receivedFile = file;
        MaterialDialog.showChoices(context, "Mở tệp đã nhận",
                Build.VERSION.SDK_INT < 24 ?
                        new String[] {"Mở bằng ứng dụng", "Mở thư mục chứa"} :
                        new String[] {"Mở bằng ứng dụng"},
                new MaterialDialog.ChoiceListener() {
                    public void onChoice(int index) {
                        if (index == 0) {
                            openWithApp(context, receivedFile);
                        } else if (index == 1) {
                            openContainingFolder(context, receivedFile);
                        }
                    }
                });
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

    @SuppressWarnings("deprecation")
    private static void openContainingFolder(Context context, File file) {
        if (Build.VERSION.SDK_INT >= 24) {
            Toast.makeText(context, "Thư mục lưu: Download/Near Transfer",
                    Toast.LENGTH_LONG).show();
            return;
        }
        File directory = file.getParentFile();
        if (directory == null || !directory.isDirectory() ||
                !DownloadFolders.contains(file)) {
            Toast.makeText(context, "Không tìm thấy thư mục chứa tệp.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] mimeTypes = new String[] {
                "resource/folder",
                "vnd.android.document/directory",
                "*/*"
        };
        for (String mimeType : mimeTypes) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(directory), mimeType);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            try {
                if (context.getPackageManager().queryIntentActivities(
                        intent, 0).size() > 0) {
                    context.startActivity(Intent.createChooser(intent,
                            "Mở thư mục bằng"));
                    return;
                }
            } catch (ActivityNotFoundException e) {
                // Try the next directory MIME type supported by older file managers.
            } catch (SecurityException e) {
                Toast.makeText(context, "Không thể mở thư mục chứa tệp.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        Toast.makeText(context, "Không có trình quản lý tệp hỗ trợ mở thư mục này.",
                Toast.LENGTH_LONG).show();
    }
}