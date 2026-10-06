package com.nearbyshare.j2me;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;

final class Jsr75FileStorage {
    private static final String SETTINGS_STORE = "NearTransferSettings";
    private static final String SAVED_PEERS_STORE = "NearTransferPeers";
    private static final int SAVED_PEERS_VERSION = 1;
    private static final int MAX_SAVED_PEERS = 8;
    private static boolean receiveDirectoryLoaded;
    private static String receiveDirectory;
    private static String receiveDirectoryError;

    static final class SavedFile {
        final FileConnection connection;
        final OutputStream output;
        final String name;
        final String path;
        private boolean outputClosed;

        SavedFile(FileConnection connection, OutputStream output,
                  String name, String path) {
            this.connection = connection;
            this.output = output;
            this.name = name;
            this.path = path;
        }

        void close() {
            try {
                output.close();
            } catch (IOException ignored) {
            } catch (SecurityException ignored) {
            }
            try {
                connection.close();
            } catch (IOException ignored) {
            } catch (SecurityException ignored) {
            }
        }

        void finish() throws IOException {
            if (!outputClosed) {
                output.flush();
                output.close();
                outputClosed = true;
            }
            connection.close();
        }

        void delete() {
            try {
                if (!outputClosed) {
                    output.close();
                    outputClosed = true;
                }
            } catch (IOException ignored) {
            } catch (SecurityException ignored) {
            }
            try {
                if (connection.exists()) {
                    connection.delete();
                }
            } catch (IOException ignored) {
            } catch (SecurityException ignored) {
            }
            try {
                connection.close();
            } catch (IOException ignored) {
            } catch (SecurityException ignored) {
            }
        }
    }

    private Jsr75FileStorage() {
    }

    static synchronized void loadReceiveDirectory() {
        if (receiveDirectoryLoaded) {
            return;
        }
        receiveDirectoryLoaded = true;
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(SETTINGS_STORE, false);
            if (store.getNumRecords() > 0) {
                byte[] data = store.getRecord(1);
                if (data.length > 0) {
                    receiveDirectory = new String(data, "UTF-8");
                }
            }
        } catch (RecordStoreNotFoundException ignored) {
        } catch (Exception exception) {
            receiveDirectoryError = "Không đọc được cài đặt vị trí nhận.";
        } finally {
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static synchronized String receiveDirectory() {
        loadReceiveDirectory();
        return receiveDirectory;
    }

    static synchronized String receiveDirectoryLabel() {
        loadReceiveDirectory();
        if (receiveDirectoryError != null) {
            return receiveDirectoryError;
        }
        if (receiveDirectory == null) {
            return "Tự chọn bộ nhớ · phân loại theo loại tệp";
        }
        if (isStorageRoot(receiveDirectory)) {
            return "Ổ " + receiveDirectory.substring(0, 2) +
                    " · phân loại theo loại tệp";
        }
        return receiveDirectory + " · phân loại theo loại tệp";
    }

    static synchronized void setReceiveDirectory(String directory)
            throws IOException {
        loadReceiveDirectory();
        if (receiveDirectoryError != null) {
            throw new IOException(receiveDirectoryError);
        }
        if (directory != null) {
            if (!isStorageRoot(directory)) {
                throw new IOException("Hãy chọn ổ nhớ gốc như C:/ hoặc E:/.");
            }
            try {
                verifyReceiveRoot(directory);
            } catch (SecurityException exception) {
                throw writePermissionDenied();
            }
        }

        byte[] data;
        try {
            data = directory == null ? new byte[0] :
                    directory.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 is unavailable.");
        }
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(SETTINGS_STORE, true);
            if (store.getNumRecords() == 0) {
                store.addRecord(data, 0, data.length);
            } else {
                store.setRecord(1, data, 0, data.length);
            }
        } catch (Exception exception) {
            throw new IOException("Không lưu được vị trí nhận.");
        } finally {
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Exception ignored) {
                }
            }
        }
        receiveDirectory = directory;
        receiveDirectoryError = null;
        receiveDirectoryLoaded = true;
    }

    static boolean isStorageRoot(String path) {
        if (path == null || path.length() != 3 ||
                path.charAt(1) != ':' ||
                (path.charAt(2) != '/' && path.charAt(2) != '\\')) {
            return false;
        }
        char drive = path.charAt(0);
        return (drive >= 'A' && drive <= 'Z') ||
                (drive >= 'a' && drive <= 'z');
    }

    private static void verifyReceiveRoot(String root) throws IOException {
        FileConnection folder = null;
        try {
            folder = (FileConnection) Connector.open(
                    Jsr75FileBrowser.toFileUrl(root), Connector.READ_WRITE);
            if (!folder.exists() || !folder.isDirectory()) {
                throw new IOException("Ổ nhớ đã chọn không còn khả dụng.");
            }
        } finally {
            if (folder != null) {
                try {
                    folder.close();
                } catch (IOException ignored) {
                }
            }
        }

        String[] categories = receiveCategories();
        int i;
        for (i = 0; i < categories.length; i++) {
            SavedFile probe = createInCategory(root, categories[i],
                    "NearTransferPermissionCheck.tmp");
            try {
                probe.output.write(0);
                probe.output.flush();
            } finally {
                probe.delete();
            }
        }
    }

    static synchronized PeerDevice[] loadSavedPeers() throws IOException {
        RecordStore store = null;
        DataInputStream input = null;
        try {
            store = RecordStore.openRecordStore(SAVED_PEERS_STORE, false);
            if (store.getNumRecords() == 0) {
                return new PeerDevice[0];
            }
            byte[] data = store.getRecord(1);
            input = new DataInputStream(new ByteArrayInputStream(data));
            if (input.readInt() != SAVED_PEERS_VERSION) {
                throw new IOException("Phiên bản danh sách đã lưu không hợp lệ.");
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_SAVED_PEERS) {
                throw new IOException("Số thiết bị đã lưu không hợp lệ.");
            }
            PeerDevice[] peers = new PeerDevice[count];
            int i;
            for (i = 0; i < count; i++) {
                String name = input.readUTF();
                String address = input.readUTF();
                int port = input.readInt();
                if (name.length() == 0 || !LanHttpProtocol.isIpv4(address) ||
                        port < 1 || port > 65535) {
                    throw new IOException("Thông tin thiết bị đã lưu không hợp lệ.");
                }
                peers[i] = new PeerDevice(name, address, port);
            }
            if (input.available() != 0) {
                throw new IOException("Dữ liệu thiết bị đã lưu bị lỗi.");
            }
            return peers;
        } catch (RecordStoreNotFoundException ignored) {
            return new PeerDevice[0];
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Không đọc được danh sách thiết bị đã lưu.");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static synchronized void saveSavedPeers(PeerDevice[] peers, int count)
            throws IOException {
        if (peers == null || count < 0 || count > MAX_SAVED_PEERS ||
                count > peers.length) {
            throw new IOException("Danh sách thiết bị đã lưu không hợp lệ.");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        try {
            output.writeInt(SAVED_PEERS_VERSION);
            output.writeInt(count);
            int i;
            for (i = 0; i < count; i++) {
                PeerDevice peer = peers[i];
                if (peer == null || peer.name == null ||
                        peer.name.length() == 0 ||
                        !LanHttpProtocol.isIpv4(peer.address) ||
                        peer.port < 1 || peer.port > 65535) {
                    throw new IOException(
                            "Thông tin thiết bị đã lưu không hợp lệ.");
                }
                output.writeUTF(peer.name);
                output.writeUTF(peer.address);
                output.writeInt(peer.port);
            }
            output.flush();
        } catch (IOException exception) {
            throw exception;
        } finally {
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }

        byte[] data = bytes.toByteArray();
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(SAVED_PEERS_STORE, true);
            if (store.getNumRecords() == 0) {
                store.addRecord(data, 0, data.length);
            } else {
                store.setRecord(1, data, 0, data.length);
            }
        } catch (Exception exception) {
            throw new IOException("Không lưu được danh sách thiết bị đã lưu.");
        } finally {
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static FileConnection openForRead(String path) throws IOException {
        return (FileConnection) Connector.open(
                Jsr75FileBrowser.toFileUrl(path), Connector.READ);
    }

    static long fileSize(String path) throws IOException {
        FileConnection file = openForRead(path);
        try {
            return file.fileSize();
        } finally {
            file.close();
        }
    }

    static SavedFile createUnique(String requestedName) throws IOException {
        loadReceiveDirectory();
        if (receiveDirectoryError != null) {
            throw new IOException(receiveDirectoryError);
        }
        String safeName = LanHttpProtocol.sanitizeFileName(requestedName);
        String category = categoryForName(safeName);
        if (receiveDirectory != null) {
            return createInCategory(receiveDirectory, category, safeName);
        }
        Enumeration roots;
        try {
            roots = FileSystemRegistry.listRoots();
        } catch (SecurityException exception) {
            throw writePermissionDenied();
        }
        if (roots == null) {
            throw new IOException("Không tìm thấy bộ nhớ có thể ghi.");
        }
        Vector rootPaths = new Vector();
        while (roots.hasMoreElements()) {
            String root = (String) roots.nextElement();
            if (root != null && root.length() > 0) {
                rootPaths.addElement(root);
            }
        }
        IOException lastFailure = null;
        int i;
        for (i = 0; i < rootPaths.size(); i++) {
            String root = (String) rootPaths.elementAt(i);
            try {
                return createInCategory(root, category, safeName);
            } catch (IOException exception) {
                lastFailure = exception;
            } catch (SecurityException exception) {
                lastFailure = writePermissionDenied();
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IOException("Không tìm thấy bộ nhớ có thể ghi.");
    }

    private static SavedFile createInCategory(String base, String category,
                                              String safeName)
            throws IOException {
        boolean denied = false;
        String[] candidates = categoryPaths(base, category);
        int i;
        for (i = 0; i < candidates.length; i++) {
            String candidate = candidates[i];
            try {
                if (candidate.equals(join(base,
                        "Near Transfer/" + category + "/"))) {
                    ensureDirectory(join(base, "Near Transfer/"));
                }
                ensureDirectory(candidate);
                return createUniqueAt(candidate, safeName);
            } catch (SecurityException exception) {
                denied = true;
            } catch (IOException exception) {
            }
        }

        if (denied) {
            throw writePermissionDenied();
        }
        throw new IOException("Không tạo được thư mục " + category +
                " trên ổ đã chọn.");
    }

    private static String[] categoryPaths(String base, String category) {
        String standardPath = join(base, category + "/");
        String dataPath = join(base, "Data/" + category + "/");
        String nearTransferPath = join(base,
                "Near Transfer/" + category + "/");
        if (isPhoneMemoryRoot(base)) {
            return new String[] {
                dataPath, standardPath, nearTransferPath
            };
        }
        return new String[] {
            standardPath, dataPath, nearTransferPath
        };
    }

    private static boolean isPhoneMemoryRoot(String path) {
        return isStorageRoot(path) &&
                (path.charAt(0) == 'C' || path.charAt(0) == 'c');
    }

    private static String[] receiveCategories() {
        return new String[] {
            "Images", "Videos", "Sounds", "Documents", "Others"
        };
    }

    private static String categoryForName(String name) {
        String lower = name.toLowerCase();
        if (endsWithAny(lower, new String[] {
                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
        })) {
            return "Images";
        }
        if (endsWithAny(lower, new String[] {
                ".3gp", ".3g2", ".mp4", ".m4v", ".avi", ".mov",
                ".mpg", ".mpeg", ".wmv"
        })) {
            return "Videos";
        }
        if (endsWithAny(lower, new String[] {
                ".mp3", ".wav", ".aac", ".amr", ".mid", ".midi",
                ".m4a", ".wma", ".ogg", ".oga", ".flac"
        })) {
            return "Sounds";
        }
        if (lower.endsWith(".txt")) {
            return "Documents";
        }
        return "Others";
    }

    private static boolean endsWithAny(String value, String[] endings) {
        int i;
        for (i = 0; i < endings.length; i++) {
            if (value.endsWith(endings[i])) {
                return true;
            }
        }
        return false;
    }

    static String writePermissionMessage() {
        return "Điện thoại từ chối quyền ghi tệp. Hãy bật quyền Ghi dữ liệu/File access cho Near Transfer trong cài đặt ứng dụng.";
    }

    private static IOException writePermissionDenied() {
        return new IOException(writePermissionMessage());
    }

    static String saveText(String text) throws IOException {
        byte[] bytes;
        try {
            bytes = text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IOException("UTF-8 is unavailable.");
        }
        SavedFile file = createUnique("Tin nhắn.txt");
        try {
            file.output.write(bytes);
            file.finish();
            return file.path;
        } finally {
            file.close();
        }
    }

    private static void ensureDirectory(String path) throws IOException {
        FileConnection directory = (FileConnection) Connector.open(
                Jsr75FileBrowser.toFileUrl(path), Connector.READ_WRITE);
        try {
            if (!directory.exists()) {
                directory.mkdir();
            } else if (!directory.isDirectory()) {
                throw new IOException("Tên thư mục lưu đã tồn tại dưới dạng tệp.");
            }
        } finally {
            directory.close();
        }
    }

    private static SavedFile createUniqueAt(String directory, String safeName)
            throws IOException {
        int dot = safeName.lastIndexOf('.');
        String base = dot > 0 ? safeName.substring(0, dot) : safeName;
        String extension = dot > 0 ? safeName.substring(dot) : "";
        int suffix;
        for (suffix = 0; suffix < 10000; suffix++) {
            String name = suffix == 0 ? safeName :
                    base + " (" + suffix + ")" + extension;
            String path = join(directory, name);
            FileConnection file = (FileConnection) Connector.open(
                    Jsr75FileBrowser.toFileUrl(path), Connector.READ_WRITE);
            boolean created = false;
            try {
                if (file.exists()) {
                    file.close();
                    continue;
                }
                file.create();
                created = true;
                OutputStream output = file.openOutputStream();
                return new SavedFile(file, output, name, path);
            } catch (IOException exception) {
                if (created) {
                    try {
                        file.delete();
                    } catch (IOException ignored) {
                    } catch (SecurityException ignored) {
                    }
                }
                try {
                    file.close();
                } catch (IOException ignored) {
                } catch (SecurityException ignored) {
                }
                throw exception;
            } catch (SecurityException exception) {
                if (created) {
                    try {
                        file.delete();
                    } catch (IOException ignored) {
                    } catch (SecurityException ignored) {
                    }
                }
                try {
                    file.close();
                } catch (IOException ignored) {
                } catch (SecurityException ignored) {
                }
                throw exception;
            }
        }
        throw new IOException("Không tạo được tên tệp nhận duy nhất.");
    }

    private static String join(String directory, String name) {
        if (directory.endsWith("/") || directory.endsWith("\\")) {
            return directory + name;
        }
        return directory + "/" + name;
    }
}
