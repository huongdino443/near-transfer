package com.nearbyshare.legacy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class ReceivedFileProvider extends ContentProvider {
    static final String AUTHORITY = "com.nearbyshare.legacy.receivedfiles";
    private static final String COLUMN_DISPLAY_NAME = "_display_name";
    private static final String COLUMN_SIZE = "_size";
    private static final String[] DEFAULT_PROJECTION = new String[] {
            COLUMN_DISPLAY_NAME, COLUMN_SIZE
    };

    static Uri uriForFile(File file) {
        if (file == null || !file.isFile() || !DownloadFolders.contains(file)) {
            throw new IllegalArgumentException("File is not a received Near Transfer file.");
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file.getName())
                .build();
    }

    static Uri uriForReceivedDirectory(File directory) {
        if (directory == null || !directory.isDirectory() ||
                !isReceivedDirectory(directory)) {
            throw new IllegalArgumentException(
                    "Directory is not the received Near Transfer folder.");
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("folder")
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        if (isReceivedDirectoryUri(uri)) {
            try {
                resolveReceivedDirectory();
                return "vnd.android.document/directory";
            } catch (FileNotFoundException e) {
                return "application/octet-stream";
            }
        }
        File file;
        try {
            file = resolveFile(uri);
        } catch (FileNotFoundException e) {
            return "application/octet-stream";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    name.substring(dot + 1).toLowerCase(java.util.Locale.US));
            if (type != null) {
                return type;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (isReceivedDirectoryUri(uri)) {
            try {
                return new FileMetadataCursor(
                        projection == null ? DEFAULT_PROJECTION : projection,
                        resolveReceivedDirectory());
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(
                        "Received-file directory does not exist.", e);
            }
        }
        File file;
        try {
            file = resolveFile(uri);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Invalid received-file URI.", e);
        }
        return new FileMetadataCursor(
                projection == null ? DEFAULT_PROJECTION : projection, file);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new SecurityException("Received files are read-only.");
        }
        if (isReceivedDirectoryUri(uri)) {
            return ParcelFileDescriptor.open(resolveReceivedDirectory(),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        }
        return ParcelFileDescriptor.open(resolveFile(uri),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider.");
    }

    private File resolveFile(Uri uri) throws FileNotFoundException {
        if (uri == null || !"content".equals(uri.getScheme()) ||
                !AUTHORITY.equals(uri.getAuthority()) ||
                uri.getPathSegments().size() != 1 ||
                isReceivedDirectoryUri(uri)) {
            throw new FileNotFoundException("Invalid received-file URI.");
        }
        String name = uri.getLastPathSegment();
        File file = new File(DownloadFolders.receivedDirectory(), name);
        try {
            File canonicalFile = file.getCanonicalFile();
            if (!canonicalFile.isFile() || !DownloadFolders.contains(canonicalFile)) {
                throw new FileNotFoundException("Received file does not exist.");
            }
            return canonicalFile;
        } catch (IOException e) {
            throw new FileNotFoundException("Cannot resolve received file.");
        }
    }

    private static boolean isReceivedDirectoryUri(Uri uri) {
        return uri != null && "content".equals(uri.getScheme()) &&
                AUTHORITY.equals(uri.getAuthority()) &&
                uri.getPathSegments().size() == 1 &&
                "folder".equals(uri.getLastPathSegment());
    }

    private File resolveReceivedDirectory() throws FileNotFoundException {
        File directory = DownloadFolders.receivedDirectory();
        try {
            File canonicalDirectory = directory.getCanonicalFile();
            if (!canonicalDirectory.isDirectory() ||
                    !isReceivedDirectory(canonicalDirectory)) {
                throw new FileNotFoundException(
                        "Received-file directory does not exist.");
            }
            return canonicalDirectory;
        } catch (IOException e) {
            throw new FileNotFoundException(
                    "Cannot resolve received-file directory.");
        }
    }

    private static boolean isReceivedDirectory(File directory) {
        try {
            return directory.getCanonicalPath().equals(
                    DownloadFolders.receivedDirectory().getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static final class FileMetadataCursor extends AbstractCursor {
        private final String[] columns;
        private final File file;

        FileMetadataCursor(String[] columns, File file) {
            this.columns = columns.clone();
            this.file = file;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public String[] getColumnNames() {
            return columns.clone();
        }

        @Override
        public String getString(int column) {
            String name = columns[column];
            if (COLUMN_DISPLAY_NAME.equals(name)) {
                return file.getName();
            }
            if (COLUMN_SIZE.equals(name)) {
                return Long.toString(file.length());
            }
            return null;
        }

        @Override
        public short getShort(int column) {
            String value = getString(column);
            return value == null ? 0 : (short) Long.parseLong(value);
        }

        @Override
        public int getInt(int column) {
            String value = getString(column);
            return value == null ? 0 : (int) Long.parseLong(value);
        }

        @Override
        public long getLong(int column) {
            String value = getString(column);
            return value == null ? 0L : Long.parseLong(value);
        }

        @Override
        public float getFloat(int column) {
            String value = getString(column);
            return value == null ? 0f : Float.parseFloat(value);
        }

        @Override
        public double getDouble(int column) {
            String value = getString(column);
            return value == null ? 0d : Double.parseDouble(value);
        }

        @Override
        public boolean isNull(int column) {
            return getString(column) == null;
        }
    }
}