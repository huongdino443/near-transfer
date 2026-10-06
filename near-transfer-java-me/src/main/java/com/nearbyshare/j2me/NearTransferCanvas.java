package com.nearbyshare.j2me;

import java.io.IOException;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import java.util.Vector;

/**
 * Shared layout and input controller. Touch and keypad modes use the same
 * coordinates; keypad mode only adds focus and softkey/D-pad navigation.
 */
final class NearTransferCanvas extends Canvas {
    static final int HOME = 0;
    static final int FILES = 2;
    static final int TEXT_COMPOSER = 3;
    static final int OFFER_FILES = 4;
    static final int OFFER_TEXT = 5;
    static final int PROGRESS = 6;
    static final int RESULT_SUCCESS = 7;
    static final int RESULT_ERROR = 8;
    static final int RECEIVED_TEXT = 9;
    static final int SAVED_DEVICES = 10;
    static final int CONFIRM_REPLACE = 11;

    static final int ACTION_FILES = 1;
    static final int ACTION_MEDIA = 2;
    static final int ACTION_PASTE = 3;
    static final int ACTION_TEXT = 4;
    static final int ACTION_ADD = 5;
    static final int ACTION_REMOVE_QUEUE = 6;
    static final int ACTION_SEND_QUEUE = 7;
    static final int ACTION_REFRESH = 8;
    static final int ACTION_MANUAL = 11;
    static final int ACTION_ADD_SELECTED = 1200;
    static final int ACTION_EDIT_TEXT = 1202;
    static final int ACTION_QUEUE_TEXT = 1203;
    static final int ACTION_SEND_TEXT = 1204;
    static final int ACTION_ACCEPT = 1205;
    static final int ACTION_DECLINE = 1206;
    static final int ACTION_PROGRESS_CANCEL = 1208;
    static final int ACTION_CLOSE = 1209;
    static final int ACTION_RETRY = 1210;
    static final int ACTION_SAVE_TEXT = 1212;
    static final int ACTION_REMOVE_SAVED = 1214;
    static final int ACTION_DETAILS = 1215;
    static final int ACTION_CONFIRM_REPLACE = 1216;
    static final int ACTION_CANCEL_REPLACE = 1217;
    static final int ACTION_HEADER_BACK = 1218;
    static final int ACTION_BROWSE_RECEIVE = 1219;
    static final int ACTION_RECEIVE_DEFAULT = 1220;
    static final int ACTION_RECEIVE_SAVE_HERE = 1221;
    static final int ACTION_FILE_MENU_ADD = 1222;
    static final int ACTION_FILE_MENU_BACK = 1223;
    static final int ACTION_OPEN_NEAR_PEER_BASE = 3000;
    static final int ACTION_OPEN_SAVED_PEER_BASE = 3040;
    static final int ACTION_PEER_DIALOG_SEND = 3100;
    static final int ACTION_PEER_DIALOG_SAVE = 3101;
    static final int ACTION_PEER_DIALOG_REMOVE = 3102;
    static final int ACTION_SAVE_PEER_BASE = 3200;
    static final int ACTION_REMOVE_SAVED_BASE = 3220;
    static final int ACTION_BROWSER_ENTRY_BASE = 2000;
    static final int ACTION_BROWSER_PARENT = 2200;
    static final int ACTION_BROWSER_PREVIOUS_PAGE = 2201;
    static final int ACTION_BROWSER_NEXT_PAGE = 2202;
    static final int ACTION_BROWSER_RETRY = 2203;
    static final int ACTION_SEND_NEAR_BASE = 3300;
    static final int ACTION_SEND_SAVED_BASE = 3340;

    private static final int MAX_HITS = 64;
    // Nokia MIDP runtimes use different raw codes for the same two softkeys.
    private static final int KEY_SOFT_LEFT_S60V3 = -1;
    private static final int KEY_SOFT_RIGHT_S60V3 = -2;
    private static final int KEY_SOFT_LEFT_NOKIA = -6;
    private static final int KEY_SOFT_RIGHT_NOKIA = -7;
    private static final int SOFTKEY_NONE = 0;
    private static final int SOFTKEY_LEFT = 1;
    private static final int SOFTKEY_RIGHT = 2;
    private static final int UI_DENSITY_PERCENT = 80;
    private static final int MIN_UI_SCALE = 70;
    private static final int MAX_BROWSER_ENTRIES = 200;
    private static final int BROWSER_PAGE_SIZE = 10;
    private static final int MAX_BROWSER_DEPTH = 16;
    private static final int MAX_SAVED_DEVICES = 8;
    private final NearTransferMidlet host;
    private final int[] hitX = new int[MAX_HITS];
    private final int[] hitY = new int[MAX_HITS];
    private final int[] hitWidth = new int[MAX_HITS];
    private final int[] hitHeight = new int[MAX_HITS];
    private final int[] hitAction = new int[MAX_HITS];
    private int hitCount;
    private int focusIndex;
    private int screen = HOME;
    private int scrollY;
    private int maxScrollY;
    private int pointerStartX;
    private int pointerStartY;
    private int pointerStartScroll;
    private boolean pointerMoved;
    private boolean mediaOnly;
    private boolean incomingTextTransfer;
    private boolean outgoing = true;
    private boolean queueIsText;
    private boolean realFileBrowser;
    private boolean receiveLocationMode;
    private boolean fileBrowserLoading;
    private boolean peerActionDialogVisible;
    private boolean peerActionForSavedDevice;
    private boolean fileActionMenuVisible;
    private boolean nokiaSoftkeyAliases;
    private boolean detailsExpanded;
    private boolean replaceWithText;
    private boolean replacementConfirmed;
    private int progressPercent;
    private int selectedFileCount;
    private int queueCount;
    private int savedCount;
    private int browserState = FileBrowserListing.READY;
    private int browserRequestId;
    private int browserDepth;
    private int browserPage;
    private int peerActionIndex;
    private int peerActionOriginFocusIndex;
    private int peerActionFirstHit;
    private int fileActionMenuOriginFocusIndex;
    private int fileActionMenuSelection;
    private String manualAddress = "";
    private String statusMessage = "";
    private String localAddress;
    private String serverStatus = "";
    private String progressName = "";
    private String lastReceivedName = "";
    private String receivedTextPath = "";
    private String transferError = "";
    private String incomingSenderName = "";
    private long progressTransferred;
    private long progressTotal;
    private long progressItemTransferred;
    private long progressItemTotal;
    private int progressItemIndex;
    private IncomingOffer incomingOffer;
    private PeerDevice[] nearbyPeers = new PeerDevice[0];
    private PeerDevice manualPeer;
    private PeerDevice transferPeer;
    private String browserPath;
    private String browserMessage = "";
    private String peerActionName = "";
    private String peerActionEndpoint = "";
    private PeerDevice peerActionDevice;
    private String textDraft = "";
    private String receivedMessage = "";

    private final String[] queueFiles = new String[20];
    private final String[] queueFilePaths = new String[20];
    private final long[] queueFileSizes = new long[20];
    private final String[] selectedBrowserNames = new String[20];
    private final String[] selectedBrowserPaths = new String[20];
    private final long[] selectedBrowserSizes = new long[20];
    private final String[] browserHistory = new String[MAX_BROWSER_DEPTH];
    private FileBrowserEntry[] browserEntries = new FileBrowserEntry[0];
    private final PeerDevice[] savedPeers =
            new PeerDevice[MAX_SAVED_DEVICES];

    NearTransferCanvas(NearTransferMidlet host) {
        this.host = host;
        setFullScreenMode(true);
        nokiaSoftkeyAliases = detectNokiaSoftkeyAliases();
        try {
            PeerDevice[] storedPeers = Jsr75FileStorage.loadSavedPeers();
            savedCount = storedPeers.length;
            System.arraycopy(storedPeers, 0, savedPeers, 0, savedCount);
        } catch (java.io.IOException exception) {
            statusMessage = "Không đọc được danh sách thiết bị đã lưu.";
        }
    }

    private boolean detectNokiaSoftkeyAliases() {
        try {
            String platform = System.getProperty("microedition.platform");
            if (platform == null) {
                return false;
            }
            String normalized = platform.toLowerCase();
            return normalized.indexOf("nokia") >= 0 ||
                    normalized.indexOf("s60") >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int getSoftkeyAction(int keyCode) {
        String keyName = null;
        try {
            keyName = getKeyName(keyCode);
        } catch (IllegalArgumentException ignored) {
        }
        if (keyName != null) {
            String normalized = keyName.toLowerCase();
            if ("lsk".equals(normalized) ||
                    "left softkey".equals(normalized)) {
                return SOFTKEY_LEFT;
            }
            if ("rsk".equals(normalized) ||
                    "right softkey".equals(normalized)) {
                return SOFTKEY_RIGHT;
            }
            if (normalized.indexOf("soft") >= 0) {
                if (normalized.indexOf("left") >= 0 ||
                        normalized.indexOf("1") >= 0) {
                    return SOFTKEY_LEFT;
                }
                if (normalized.indexOf("right") >= 0 ||
                        normalized.indexOf("2") >= 0) {
                    return SOFTKEY_RIGHT;
                }
            }
        }
        if (!nokiaSoftkeyAliases) {
            return SOFTKEY_NONE;
        }
        if (keyCode == KEY_SOFT_LEFT_S60V3 ||
                keyCode == KEY_SOFT_LEFT_NOKIA) {
            return SOFTKEY_LEFT;
        }
        if (keyCode == KEY_SOFT_RIGHT_S60V3 ||
                keyCode == KEY_SOFT_RIGHT_NOKIA) {
            return SOFTKEY_RIGHT;
        }
        return SOFTKEY_NONE;
    }

    void activateFocused() {
        if (fileActionMenuVisible) {
            activateFileActionMenuSelection();
            return;
        }
        if (hitCount == 0) {
            return;
        }
        if (focusIndex < 0) {
            focusIndex = 0;
        }
        if (focusIndex >= hitCount) {
            focusIndex = hitCount - 1;
        }
        activateAction(hitAction[focusIndex]);
    }

    private void openFileActionMenu() {
        if (fileActionMenuVisible || screen != FILES ||
                !realFileBrowser || receiveLocationMode) {
            return;
        }
        fileActionMenuVisible = true;
        fileActionMenuOriginFocusIndex = focusIndex;
        fileActionMenuSelection = selectedFileCount > 0 ? 0 : 1;
        focusIndex = -1;
        repaint();
    }

    private void closeFileActionMenu() {
        if (!fileActionMenuVisible) {
            return;
        }
        fileActionMenuVisible = false;
        focusIndex = fileActionMenuOriginFocusIndex;
        repaint();
    }

    private void activateFileActionMenuSelection() {
        activateAction(fileActionMenuSelection == 0 ?
                ACTION_FILE_MENU_ADD : ACTION_FILE_MENU_BACK);
    }

    private void moveFileActionMenuSelection(int direction) {
        int firstOption = selectedFileCount > 0 ? 0 : 1;
        fileActionMenuSelection = clamp(fileActionMenuSelection + direction,
                firstOption, 1);
        repaint();
    }

    void goBack() {
        if (fileActionMenuVisible) {
            closeFileActionMenu();
            return;
        }
        if (peerActionDialogVisible) {
            peerActionDialogVisible = false;
            focusIndex = peerActionOriginFocusIndex;
            repaint();
            return;
        }
        if (screen == FILES && realFileBrowser && browserPath != null) {
            goToBrowserParent();
            return;
        }
        if ((screen == OFFER_FILES || screen == OFFER_TEXT) &&
                incomingOffer != null) {
            host.resolveIncomingOffer(incomingOffer, false);
            incomingOffer = null;
            statusMessage = "Đã từ chối yêu cầu nhận nội dung.";
            setScreen(HOME);
            return;
        }
        if (screen == HOME) {
            return;
        }
        if (screen == TEXT_COMPOSER) {
            replacementConfirmed = false;
        }
        if (screen == PROGRESS) {
            if (outgoing) {
                host.cancelTransfer();
                finishOutgoingTransfer(false, "Đã hủy truyền.");
            } else {
                statusMessage = "Đang nhận nội dung từ thiết bị.";
                repaint();
            }
            return;
        }
        setScreen(HOME);
    }

    void acceptInput(int kind, String value) {
        if (value == null) {
            value = "";
        }
        if (kind == NearTransferMidlet.INPUT_ADDRESS) {
            String trimmed = value.trim();
            if (trimmed.length() == 0) {
                statusMessage = "Địa chỉ đang trống.";
            } else {
                PeerDevice peer = parseManualPeer(trimmed);
                if (peer == null) {
                    statusMessage = "Nhập địa chỉ IPv4, ví dụ 192.168.1.20.";
                } else {
                    manualPeer = peer;
                    manualAddress = peer.address + ":" + peer.port;
                    setNearbyPeers(nearbyPeers);
                    statusMessage = "Đã thêm " + manualAddress +
                            ". Chọn thiết bị để gửi.";
                }
            }
            screen = HOME;
        } else if (kind == NearTransferMidlet.INPUT_MESSAGE) {
            textDraft = value;
            if (textDraft.length() == 0) {
                statusMessage = "Tin nhắn đang trống.";
            } else if (textDraft.length() > 4096) {
                textDraft = textDraft.substring(0, 4096);
                statusMessage = "Tin nhắn đã được giới hạn ở 4 KB.";
            } else {
                statusMessage = "Nội dung đã cập nhật.";
            }
            screen = TEXT_COMPOSER;
        }
        scrollY = 0;
        focusIndex = 0;
        repaint();
    }

    void addHit(int x, int y, int width, int height, int action) {
        if (hitCount >= MAX_HITS) {
            return;
        }
        hitX[hitCount] = x;
        hitY[hitCount] = y;
        hitWidth[hitCount] = width;
        hitHeight[hitCount] = height;
        hitAction[hitCount] = action;
        hitCount++;
    }

    void drawButton(Graphics graphics, String label, int x, int y, int width,
                    int height, int action, boolean primary, int scale) {
        boolean focused = !isTouchMode() && hitCount == focusIndex;
        UiKit.drawButton(graphics, label, x, y, width, height,
                isTouchMode() && primary, focused, scale);
        addHit(x, y, width, height, action);
    }

    boolean isFocusedAction(int action) {
        return !isTouchMode() && hitCount == focusIndex;
    }

    int px(int value, int scale) {
        return UiKit.px(value, scale);
    }

    boolean isTouchMode() {
        return hasPointerEvents();
    }

    int getScreen() {
        return screen;
    }

    int getScrollY() {
        return scrollY;
    }

    int getQueueCount() {
        return queueCount;
    }

    String getQueueFile(int index) {
        return index >= 0 && index < queueCount ? queueFiles[index] : "";
    }

    String getQueueFileSize(int index) {
        if (index >= 0 && index < queueCount &&
                queueFilePaths[index] != null) {
            return formatFileSize(queueFileSizes[index]);
        }
        return "";
    }

    String getProgressName() {
        return progressName;
    }

    long getProgressTransferred() {
        return progressTransferred;
    }

    long getProgressTotal() {
        return progressTotal;
    }

    long getProgressItemTransferred() {
        return progressItemTransferred;
    }

    long getProgressItemTotal() {
        return progressItemTotal;
    }

    String getTransferError() {
        return transferError;
    }

    String getIncomingSenderName() {
        return incomingSenderName;
    }

    String getLastReceivedName() {
        return lastReceivedName;
    }

    String getReceivedTextPath() {
        return receivedTextPath;
    }

    IncomingOffer getIncomingOffer() {
        return incomingOffer;
    }

    int getTransferItemCount() {
        if ((outgoing && queueIsText) ||
                (!outgoing && incomingTextTransfer)) {
            return 1;
        }
        if (outgoing) {
            return queueCount;
        }
        return incomingOffer == null ? 0 : incomingOffer.items.length;
    }

    String getTransferItemName(int index) {
        if ((outgoing && queueIsText) ||
                (!outgoing && incomingTextTransfer)) {
            return "Tin nhắn văn bản";
        }
        if (outgoing) {
            return getQueueFile(index);
        }
        return incomingOffer != null && index >= 0 &&
                index < incomingOffer.items.length ?
                incomingOffer.items[index].name : "";
    }

    long getTransferItemSize(int index) {
        if ((outgoing && queueIsText) ||
                (!outgoing && incomingTextTransfer)) {
            return incomingOffer == null ? progressTotal :
                    incomingOffer.totalBytes;
        }
        if (outgoing) {
            return index >= 0 && index < queueCount ?
                    queueFileSizes[index] : -1;
        }
        return incomingOffer != null && index >= 0 &&
                index < incomingOffer.items.length ?
                incomingOffer.items[index].size : -1;
    }

    int getProgressItemIndex() {
        return progressItemIndex;
    }

    boolean isQueueText() {
        return queueIsText;
    }

    String getTextDraft() {
        return textDraft;
    }

    String getReceivedMessage() {
        return receivedMessage;
    }

    String getManualAddress() {
        return manualAddress;
    }

    int getProgressPercent() {
        return progressPercent;
    }

    boolean isOutgoing() {
        return outgoing;
    }

    boolean isIncomingTextTransfer() {
        return incomingTextTransfer;
    }

    boolean isDetailsExpanded() {
        return detailsExpanded;
    }

    boolean isRealFileBrowser() {
        return realFileBrowser;
    }

    boolean isReceiveLocationMode() {
        return receiveLocationMode;
    }

    String getReceiveDirectoryLabel() {
        return Jsr75FileStorage.receiveDirectoryLabel();
    }

    boolean isFileBrowserLoading() {
        return fileBrowserLoading;
    }

    int getFileBrowserState() {
        return browserState;
    }

    String getFileBrowserMessage() {
        return browserMessage;
    }

    String getStatusMessage() {
        return statusMessage;
    }

    String getFileBrowserPath() {
        return browserPath;
    }

    int getFileBrowserEntryCount() {
        return browserEntries.length;
    }

    FileBrowserEntry getFileBrowserEntry(int index) {
        return index >= 0 && index < browserEntries.length ?
                browserEntries[index] : null;
    }

    int getFileBrowserPage() {
        return browserPage;
    }

    int getFileBrowserPageCount() {
        int pages = (browserEntries.length + BROWSER_PAGE_SIZE - 1) /
                BROWSER_PAGE_SIZE;
        return pages == 0 ? 1 : pages;
    }

    synchronized void acceptFileBrowserListing(int requestId,
                                               FileBrowserListing listing) {
        if (requestId != browserRequestId || screen != FILES ||
                !realFileBrowser) {
            return;
        }
        fileBrowserLoading = false;
        browserState = listing.state;
        browserMessage = listing.message;
        browserEntries = listing.entries;
        browserPage = 0;
        int i;
        for (i = 0; i < browserEntries.length; i++) {
            browserEntries[i].selected =
                    findSelectedBrowserPath(browserEntries[i].path) >= 0;
        }
        scrollY = 0;
        focusIndex = 0;
        repaint();
    }

    boolean isMediaOnly() {
        return mediaOnly;
    }

    boolean isReplaceWithText() {
        return replaceWithText;
    }

    int getSelectedFileCount() {
        return selectedFileCount;
    }

    int getSavedCount() {
        return savedCount;
    }

    int getNearbyPeerCount() {
        return nearbyPeers.length;
    }

    PeerDevice getNearbyPeer(int index) {
        return index >= 0 && index < nearbyPeers.length ?
                nearbyPeers[index] : null;
    }

    String getLocalAddress() {
        return localAddress;
    }

    String getServerStatus() {
        return serverStatus;
    }

    String getSavedDeviceName(int index) {
        return index >= 0 && index < savedCount ?
                savedPeers[index].name : "";
    }

    String getSavedDeviceEndpoint(int index) {
        return index >= 0 && index < savedCount ?
                savedPeers[index].address + ":" + savedPeers[index].port : "";
    }

    private void openPeerActionDialog(boolean savedDevice, int index,
                                      PeerDevice peer) {
        if (peer == null) {
            return;
        }
        peerActionDialogVisible = true;
        peerActionForSavedDevice = savedDevice;
        peerActionIndex = index;
        peerActionDevice = peer;
        peerActionName = peer.name;
        peerActionEndpoint = peer.address + ":" + peer.port;
        peerActionOriginFocusIndex = focusIndex;
        peerActionFirstHit = hitCount;
        focusIndex = peerActionFirstHit;
        repaint();
    }

    private void closePeerActionDialog() {
        peerActionDialogVisible = false;
        peerActionDevice = null;
        focusIndex = peerActionOriginFocusIndex;
        repaint();
    }

    void setNearbyPeers(PeerDevice[] peers) {
        Vector combined = new Vector();
        int i;
        if (peers != null) {
            for (i = 0; i < peers.length; i++) {
                if (peers[i] != null) {
                    combined.addElement(peers[i]);
                }
            }
        }
        if (manualPeer != null) {
            boolean found = false;
            for (i = 0; i < combined.size(); i++) {
                PeerDevice peer = (PeerDevice) combined.elementAt(i);
                if (peer.matches(manualPeer.address, manualPeer.port)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                combined.addElement(manualPeer);
            }
        }
        nearbyPeers = new PeerDevice[combined.size()];
        for (i = 0; i < nearbyPeers.length; i++) {
            nearbyPeers[i] = (PeerDevice) combined.elementAt(i);
        }
        repaint();
    }

    void setLocalAddress(String address) {
        if (LanHttpProtocol.isIpv4(address) &&
                !"0.0.0.0".equals(address)) {
            localAddress = address;
        }
        repaint();
    }

    void setServerStatus(String address, int port) {
        if (LanHttpProtocol.isIpv4(address) &&
                !"0.0.0.0".equals(address)) {
            localAddress = address;
        }
        serverStatus = "Chờ nhận trên cổng " + port;
        repaint();
    }

    void setNetworkStatus(String message) {
        if (message != null && message.length() > 0) {
            statusMessage = message;
            serverStatus = message;
        }
        repaint();
    }

    void showIncomingOffer(IncomingOffer offer) {
        if (offer == null) {
            return;
        }
        incomingOffer = offer;
        incomingSenderName = offer.senderName;
        incomingTextTransfer = offer.text;
        outgoing = false;
        progressPercent = 0;
        progressTransferred = 0;
        progressTotal = offer.totalBytes;
        transferError = "";
        setScreen(offer.text ? OFFER_TEXT : OFFER_FILES);
    }

    void expireIncomingOffer(IncomingOffer offer) {
        if (offer == incomingOffer) {
            incomingOffer = null;
            if (screen == OFFER_FILES || screen == OFFER_TEXT) {
                statusMessage = "Yêu cầu nhận đã hết hạn.";
                setScreen(HOME);
            } else if (screen == PROGRESS) {
                finishIncomingTransfer(false, "Yêu cầu truyền đã hết hạn.");
            }
        }
    }

    void failIncomingTransfer(IncomingOffer offer, String message) {
        if (offer == incomingOffer) {
            finishIncomingTransfer(false, message);
        }
    }

    private void finishIncomingTransfer(boolean success, String message) {
        transferError = message == null ? "" : message;
        if (success) {
            progressPercent = 100;
            progressTransferred = progressTotal;
            setScreen(RESULT_SUCCESS);
        } else {
            setScreen(RESULT_ERROR);
        }
    }

    void updateIncomingProgress(IncomingOffer offer, int itemIndex,
                                long transferred, long itemTotal) {
        if (offer != incomingOffer || itemIndex < 0 ||
                itemIndex >= offer.items.length) {
            return;
        }
        long completed = 0;
        int i;
        for (i = 0; i < itemIndex; i++) {
            completed += offer.items[i].size;
        }
        progressName = offer.items[itemIndex].name;
        progressItemIndex = itemIndex;
        progressItemTransferred = transferred;
        progressItemTotal = itemTotal;
        progressTransferred = completed + transferred;
        progressTotal = offer.totalBytes;
        progressPercent = progressTotal == 0 ? 0 :
                (int) (progressTransferred * 100 / progressTotal);
        if (screen != PROGRESS) {
            setScreen(PROGRESS);
        } else {
            repaint();
        }
    }

    void finishIncomingFiles(IncomingOffer offer) {
        if (offer != incomingOffer) {
            return;
        }
        progressPercent = 100;
        progressTransferred = offer.totalBytes;
        progressTotal = offer.totalBytes;
        transferError = "";
        detailsExpanded = true;
        setScreen(RESULT_SUCCESS);
    }

    void showReceivedText(IncomingOffer offer, String text) {
        incomingOffer = offer;
        incomingSenderName = offer.senderName;
        receivedMessage = text;
        incomingTextTransfer = true;
        outgoing = false;
        progressPercent = 100;
        progressTransferred = offer.totalBytes;
        progressTotal = offer.totalBytes;
        receivedTextPath = "";
        setScreen(RECEIVED_TEXT);
    }

    void updateOutgoingProgress(int itemIndex, String itemName,
                               long transferred, long itemTotal,
                               long totalTransferred, long transferTotal) {
        progressName = itemName;
        progressItemIndex = itemIndex;
        progressItemTransferred = transferred;
        progressItemTotal = itemTotal;
        progressTransferred = totalTransferred;
        progressTotal = transferTotal;
        progressPercent = transferTotal == 0 ? 0 :
                (int) (totalTransferred * 100 / transferTotal);
        repaint();
    }

    void finishOutgoingTransfer(boolean success, String error) {
        transferError = error == null ? "" : error;
        if (success) {
            progressPercent = 100;
            progressTransferred = progressTotal;
            detailsExpanded = true;
            setScreen(RESULT_SUCCESS);
        } else {
            setScreen(RESULT_ERROR);
        }
    }

    void finishSavingReceivedText(String path) {
        receivedTextPath = path;
        statusMessage = "Đã lưu tin nhắn vào " + path;
        setScreen(HOME);
    }

    void failSavingReceivedText(String error) {
        statusMessage = error == null || error.length() == 0 ?
                "Không lưu được tin nhắn." : error;
        repaint();
    }

    void setLastReceivedName(String name) {
        lastReceivedName = name;
    }

    private void savePeerDevice(PeerDevice peer) {
        if (peer == null) {
            return;
        }
        PeerDevice[] updatedPeers = new PeerDevice[MAX_SAVED_DEVICES];
        System.arraycopy(savedPeers, 0, updatedPeers, 0, savedCount);
        int i;
        for (i = 0; i < savedCount; i++) {
            if (savedPeers[i].matches(peer.address, peer.port)) {
                updatedPeers[i] = peer;
                if (!persistSavedPeers(updatedPeers, savedCount)) {
                    return;
                }
                replaceSavedPeers(updatedPeers, savedCount);
                statusMessage = peer.name + " đã được lưu.";
                return;
            }
        }
        if (savedCount >= MAX_SAVED_DEVICES) {
            statusMessage = "Danh sách thiết bị đã lưu đã đầy.";
            return;
        }
        updatedPeers[savedCount] = peer;
        int updatedCount = savedCount + 1;
        if (!persistSavedPeers(updatedPeers, updatedCount)) {
            return;
        }
        replaceSavedPeers(updatedPeers, updatedCount);
        statusMessage = "Đã lưu " + peer.name + ".";
    }

    private boolean persistSavedPeers(PeerDevice[] peers, int count) {
        try {
            Jsr75FileStorage.saveSavedPeers(peers, count);
            return true;
        } catch (java.io.IOException exception) {
            statusMessage = "Không lưu được danh sách thiết bị đã lưu.";
            return false;
        }
    }

    private void replaceSavedPeers(PeerDevice[] peers, int count) {
        int i;
        for (i = 0; i < MAX_SAVED_DEVICES; i++) {
            savedPeers[i] = i < count ? peers[i] : null;
        }
        savedCount = count;
    }

    private PeerDevice parseManualPeer(String value) {
        String addressPart = value;
        int port = LanHttpProtocol.HTTP_PORT;
        int scheme = addressPart.indexOf("://");
        if (scheme >= 0) {
            addressPart = addressPart.substring(scheme + 3);
        }
        int slash = addressPart.indexOf('/');
        if (slash >= 0) {
            addressPart = addressPart.substring(0, slash);
        }
        int colon = addressPart.lastIndexOf(':');
        if (colon >= 0) {
            port = LanHttpProtocol.parsePort(
                    addressPart.substring(colon + 1), -1);
            if (port < 1) {
                return null;
            }
            addressPart = addressPart.substring(0, colon);
        }
        String address = LanHttpProtocol.parseIpv4Address(addressPart);
        if (address == null) {
            return null;
        }
        return new PeerDevice("Thiết bị " + address, address, port);
    }

    private void sendToPeer(PeerDevice peer) {
        if (peer == null) {
            statusMessage = "Không tìm thấy thiết bị đã chọn.";
            repaint();
            return;
        }
        transferPeer = peer;
        if (queueCount == 0 && !queueIsText) {
            statusMessage = "Chọn tệp hoặc tin nhắn trước khi gửi.";
            requestFilePicker(false);
            return;
        }
        String text = null;
        String[] names = null;
        String[] paths = null;
        if (queueIsText) {
            if (textDraft == null || textDraft.length() == 0) {
                statusMessage = "Tin nhắn đang trống.";
                repaint();
                return;
            }
            text = textDraft;
        } else {
            names = new String[queueCount];
            paths = new String[queueCount];
            int i;
            for (i = 0; i < queueCount; i++) {
                names[i] = queueFiles[i];
                paths[i] = queueFilePaths[i];
                if (paths[i] == null) {
                    statusMessage = "Tệp đã chọn không còn khả dụng.";
                    repaint();
                    return;
                }
            }
        }
        outgoing = true;
        incomingTextTransfer = false;
        progressName = queueIsText ? "Tin nhắn văn bản" :
                (queueCount > 0 ? queueFiles[0] : "");
        progressTransferred = 0;
        progressTotal = 0;
        progressItemTransferred = 0;
        progressItemTotal = 0;
        progressPercent = 0;
        transferError = "";
        setScreen(PROGRESS);
        host.startTransfer(peer, names, paths, text);
    }

    private void removeSavedDevice(int index) {
        if (index < 0 || index >= savedCount) {
            return;
        }
        PeerDevice[] updatedPeers = new PeerDevice[MAX_SAVED_DEVICES];
        int updatedCount = 0;
        int i;
        for (i = 0; i < savedCount; i++) {
            if (i != index) {
                updatedPeers[updatedCount++] = savedPeers[i];
            }
        }
        if (!persistSavedPeers(updatedPeers, updatedCount)) {
            return;
        }
        replaceSavedPeers(updatedPeers, updatedCount);
        statusMessage = "Đã xóa thiết bị khỏi danh sách đã lưu.";
    }

    private void drawPeerActionDialog(Graphics graphics, int width, int height,
                                      int scale) {
        int dialogWidth = width - px(32, scale);
        int dialogHeight = px(136, scale);
        int headerHeight = px(46, scale);
        int dialogY = headerHeight +
                Math.max(px(8, scale),
                (height - headerHeight - dialogHeight) / 2);
        int dialogX = (width - dialogWidth) / 2;
        int inner = px(12, scale);
        int gap = px(8, scale);
        int buttonY = dialogY + px(78, scale);
        int buttonWidth = (dialogWidth - inner * 2 - gap) / 2;
        String secondLabel = peerActionForSavedDevice ? "Xóa" : "Lưu";
        int secondAction = peerActionForSavedDevice ?
                ACTION_PEER_DIALOG_REMOVE : ACTION_PEER_DIALOG_SAVE;

        UiKit.fillCard(graphics, dialogX, dialogY, dialogWidth, dialogHeight,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.strokeCard(graphics, dialogX, dialogY, dialogWidth, dialogHeight,
                UiKit.PRIMARY, scale);
        UiKit.drawText(graphics, "Chọn hành động",
                dialogX + inner, dialogY + px(12, scale),
                dialogWidth - inner * 2, UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        UiKit.drawWrapped(graphics, peerActionName,
                dialogX + inner, dialogY + px(40, scale),
                dialogWidth - inner * 2, UiKit.ON_SURFACE_VARIANT,
                UiKit.FONT_BODY, 2);

        peerActionFirstHit = hitCount;
        drawPeerActionButton(graphics, "Gửi đi",
                dialogX + inner, buttonY, buttonWidth, px(42, scale),
                ACTION_PEER_DIALOG_SEND, scale);
        drawPeerActionButton(graphics, secondLabel,
                dialogX + inner + buttonWidth + gap, buttonY,
                buttonWidth, px(42, scale), secondAction, scale);
        if (focusIndex < peerActionFirstHit || focusIndex >= hitCount) {
            focusIndex = peerActionFirstHit;
        }
    }

    private void drawPeerActionButton(Graphics graphics, String label,
                                      int x, int y, int width, int height,
                                      int action, int scale) {
        boolean highlighted = isTouchMode() ?
                action == ACTION_PEER_DIALOG_SEND : hitCount == focusIndex;
        UiKit.drawButton(graphics, label, x, y, width, height,
                highlighted, false, scale);
        addHit(x, y, width, height, action);
    }

    private void drawFileActionMenu(Graphics graphics, int width, int height,
                                    int scale) {
        int dialogWidth = width - px(32, scale);
        int dialogHeight = px(138, scale);
        int headerHeight = px(46, scale);
        int dialogY = headerHeight +
                Math.max(px(8, scale),
                (height - headerHeight - dialogHeight) / 2);
        int dialogX = (width - dialogWidth) / 2;
        int inner = px(12, scale);
        int buttonHeight = px(38, scale);
        int buttonGap = px(7, scale);
        int buttonY = dialogY + px(46, scale);
        int buttonWidth = dialogWidth - inner * 2;

        UiKit.fillCard(graphics, dialogX, dialogY, dialogWidth, dialogHeight,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.strokeCard(graphics, dialogX, dialogY, dialogWidth, dialogHeight,
                UiKit.PRIMARY, scale);
        UiKit.drawText(graphics, "Hành động",
                dialogX + inner, dialogY + px(12, scale),
                dialogWidth - inner * 2, UiKit.ON_SURFACE, UiKit.FONT_BOLD);

        // Make the overlay modal for pointer input. The action rows are added
        // afterward so their hit areas take precedence over this dismissal
        // area.
        addHit(0, 0, width, height, ACTION_FILE_MENU_BACK);
        drawFileActionMenuButton(graphics,
                "Thêm " + selectedFileCount + " mục",
                dialogX + inner, buttonY, buttonWidth, buttonHeight,
                ACTION_FILE_MENU_ADD, 0, scale);
        drawFileActionMenuButton(graphics, "Trở lại",
                dialogX + inner, buttonY + buttonHeight + buttonGap,
                buttonWidth, buttonHeight, ACTION_FILE_MENU_BACK, 1, scale);
    }

    private void drawFileActionMenuButton(Graphics graphics, String label,
                                          int x, int y, int width, int height,
                                          int action, int option, int scale) {
        boolean primary = isTouchMode() && option == 0 &&
                selectedFileCount > 0;
        boolean focused = !isTouchMode() &&
                fileActionMenuSelection == option &&
                (option != 0 || selectedFileCount > 0);
        UiKit.drawButton(graphics, label, x, y, width, height,
                primary, focused, scale);
        addHit(x, y, width, height, action);
    }

    protected void paint(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        int scale = computeScale(width, height);
        int headerHeight = px(46, scale);
        int footerHeight = screen == HOME ?
                HomeUi.fixedFooterHeight(this, scale) : 0;
        int viewportBottom = Math.max(headerHeight, height - footerHeight);
        boolean scrollClamped = false;

        graphics.setColor(UiKit.BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        hitCount = 0;
        drawHeader(graphics, width, headerHeight, scale);
        graphics.setClip(0, headerHeight, width,
                Math.max(0, viewportBottom - headerHeight));

        int contentBottom;
        if (screen == HOME) {
            contentBottom = HomeUi.draw(this, graphics, width,
                    headerHeight - scrollY, scale);
        } else if (screen == FILES ||
                screen == TEXT_COMPOSER || screen == SAVED_DEVICES ||
                screen == CONFIRM_REPLACE) {
            contentBottom = SelectionUi.draw(this, graphics, width,
                    headerHeight - scrollY, scale);
        } else {
            contentBottom = TransferUi.draw(this, graphics, width,
                    headerHeight - scrollY, scale);
        }
        if (screen == HOME) {
            graphics.setClip(0, 0, width, height);
            HomeUi.drawFixedFooter(this, graphics, width, height, scale);
        }
        if (peerActionDialogVisible) {
            graphics.setClip(0, 0, width, height);
            drawPeerActionDialog(graphics, width, height, scale);
        }
        if (fileActionMenuVisible) {
            graphics.setClip(0, 0, width, height);
            drawFileActionMenu(graphics, width, height, scale);
        }
        maxScrollY = Math.max(0,
                contentBottom + scrollY - viewportBottom + px(8, scale));
        if (scrollY > maxScrollY) {
            scrollY = maxScrollY;
            scrollClamped = true;
        }
        if (scrollY < 0) {
            scrollY = 0;
            scrollClamped = true;
        }
        graphics.setClip(0, 0, width, height);
        if (scrollClamped) {
            repaint();
        }
    }

    protected void keyPressed(int keyCode) {
        int softkeyAction = getSoftkeyAction(keyCode);
        if (softkeyAction == SOFTKEY_LEFT) {
            if (!fileActionMenuVisible && screen == FILES &&
                    realFileBrowser && !receiveLocationMode) {
                openFileActionMenu();
            } else {
                activateFocused();
            }
            return;
        }
        if (softkeyAction == SOFTKEY_RIGHT) {
            goBack();
            return;
        }
        if (keyCode == KEY_NUM0) {
            host.refreshPeers();
            return;
        }
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (IllegalArgumentException ignored) {
        }

        if (gameAction == UP || keyCode == KEY_NUM2) {
            moveVertical(-1);
        } else if (gameAction == DOWN || keyCode == KEY_NUM8) {
            moveVertical(1);
        } else if (gameAction == LEFT || keyCode == KEY_NUM4) {
            moveSideways(-1);
        } else if (gameAction == RIGHT || keyCode == KEY_NUM6) {
            moveSideways(1);
        } else if (gameAction == FIRE || keyCode == KEY_NUM5) {
            activateFocused();
        }
    }

    protected void pointerPressed(int x, int y) {
        if (!isTouchMode()) {
            return;
        }
        pointerStartX = x;
        pointerStartY = y;
        pointerStartScroll = scrollY;
        pointerMoved = false;
    }

    protected void pointerDragged(int x, int y) {
        if (!isTouchMode()) {
            return;
        }
        int scale = computeScale(getWidth(), getHeight());
        if (Math.abs(x - pointerStartX) > px(6, scale) ||
                Math.abs(y - pointerStartY) > px(6, scale)) {
            pointerMoved = true;
        }
        scrollY = clamp(pointerStartScroll + pointerStartY - y,
                0, maxScrollY);
        repaint();
    }

    protected void pointerReleased(int x, int y) {
        if (!isTouchMode()) {
            return;
        }
        if (pointerMoved) {
            return;
        }
        for (int i = hitCount - 1; i >= 0; i--) {
            if (x >= hitX[i] && x <= hitX[i] + hitWidth[i] &&
                    y >= hitY[i] && y <= hitY[i] + hitHeight[i]) {
                focusIndex = i;
                activateAction(hitAction[i]);
                return;
            }
        }
    }

    private void drawHeader(Graphics graphics, int width, int headerHeight,
                            int scale) {
        String title = screenTitle();
        graphics.setColor(UiKit.BACKGROUND);
        graphics.fillRect(0, 0, width, headerHeight);

        int left = px(28, scale);
        int headerTitleY =
                (headerHeight - UiKit.FONT_BOLD.getHeight()) / 2;
        int headerStatusY =
                (headerHeight - UiKit.FONT_SMALL.getHeight()) / 2;
        if (screen != HOME && isTouchMode()) {
            int buttonSize = px(34, scale);
            int buttonY = (headerHeight - buttonSize) / 2;
            graphics.setColor(UiKit.SURFACE_CONTAINER);
            graphics.fillRoundRect(left, buttonY, buttonSize, buttonSize,
                    px(10, scale), px(10, scale));

            UiKit.drawBackArrow(graphics, left, buttonY, buttonSize,
                    UiKit.ON_SURFACE, scale);
            addHit(left, buttonY, buttonSize, buttonSize,
                    ACTION_HEADER_BACK);
            left += buttonSize + px(8, scale);
        }

        String selectedStatus = screen == FILES && realFileBrowser &&
                selectedFileCount > 0 && !receiveLocationMode ?
                selectedFileCount + " mục được chọn" : "";
        int right = width - px(28, scale);
        int titleWidth = right - left;
        if (selectedStatus.length() > 0) {
            int statusWidth = UiKit.FONT_SMALL.stringWidth(selectedStatus);
            int availableTitleWidth = right - left - statusWidth -
                    px(8, scale);
            if (availableTitleWidth <
                    UiKit.FONT_BOLD.stringWidth(title)) {
                selectedStatus = selectedFileCount + " mục";
                statusWidth = UiKit.FONT_SMALL.stringWidth(selectedStatus);
                availableTitleWidth = right - left - statusWidth -
                        px(8, scale);
            }
            titleWidth = availableTitleWidth;
            int statusX = right - statusWidth;
            if (statusX > left) {
                graphics.setColor(UiKit.ON_SURFACE_VARIANT);
                graphics.setFont(UiKit.FONT_SMALL);
                graphics.drawString(selectedStatus, statusX, headerStatusY,
                        Graphics.TOP | Graphics.LEFT);
            }
        }
        UiKit.drawText(graphics, title, left, headerTitleY,
                Math.max(0, titleWidth), UiKit.ON_SURFACE, UiKit.FONT_BOLD);
    }

    private String screenTitle() {
        if (screen == HOME) {
            return "Lựa chọn";
        }
        if (screen == FILES) {
            if (receiveLocationMode) {
                return "Chọn ổ nhận";
            }
            return mediaOnly ? "Chọn ảnh hoặc video" : "Chọn tệp";
        }
        if (screen == TEXT_COMPOSER) {
            return "Nhập văn bản";
        }
        if (screen == OFFER_FILES) {
            return "Yêu cầu nhận tệp";
        }
        if (screen == OFFER_TEXT) {
            return "Yêu cầu nhận văn bản";
        }
        if (screen == PROGRESS) {
            return isOutgoing() ? "Đang gửi nội dung" : "Đang nhận nội dung";
        }
        if (screen == RESULT_SUCCESS || screen == RESULT_ERROR) {
            return "Kết quả truyền";
        }
        if (screen == RECEIVED_TEXT) {
            return "Tin nhắn đã nhận";
        }
        if (screen == CONFIRM_REPLACE) {
            return "Thay nội dung đang chờ?";
        }
        return "Thiết bị đã lưu";
    }

    private int computeScale(int width, int height) {
        int scale = width * 100 / 240;
        if (height * 100 / 320 < scale) {
            scale = height * 100 / 320;
        }
        if (scale < 75) {
            scale = 75;
        }
        if (scale > 150) {
            scale = 150;
        }
        scale = scale * UI_DENSITY_PERCENT / 100;
        if (scale < MIN_UI_SCALE) {
            scale = MIN_UI_SCALE;
        }
        return scale;
    }

    private void moveFocus(int direction) {
        if (hitCount == 0) {
            return;
        }
        if (peerActionDialogVisible) {
            if (focusIndex < peerActionFirstHit ||
                    focusIndex >= hitCount) {
                focusIndex = peerActionFirstHit;
            } else {
                focusIndex = clamp(focusIndex + direction,
                        peerActionFirstHit, hitCount - 1);
            }
            repaint();
            return;
        }
        focusIndex = clamp(focusIndex + direction, 0, hitCount - 1);
        keepFocusVisible();
        repaint();
    }

    private void moveVertical(int direction) {
        if (fileActionMenuVisible) {
            moveFileActionMenuSelection(direction);
            return;
        }
        if (peerActionDialogVisible) {
            moveFocus(direction);
            return;
        }
        if (screen == HOME && queueCount == 0 && !queueIsText &&
                focusIndex >= 0 && focusIndex < 4) {
            int next = focusIndex;
            if (direction < 0 && focusIndex >= 2) {
                next -= 2;
            } else if (direction > 0 && focusIndex < 2) {
                next += 2;
            } else if (direction > 0 && hitCount > 4) {
                next = 4;
            }
            if (next != focusIndex) {
                focusIndex = next;
                keepFocusVisible();
                repaint();
            }
            return;
        }
        moveFocus(direction);
    }

    private void moveSideways(int direction) {
        if (fileActionMenuVisible) {
            return;
        }
        if (peerActionDialogVisible) {
            moveFocus(direction);
            return;
        }
        if (screen == FILES && realFileBrowser && !isTouchMode() &&
                !fileBrowserLoading &&
                browserState == FileBrowserListing.READY &&
                getFileBrowserPageCount() > 1) {
            int targetPage = browserPage + direction;
            if (targetPage >= 0 &&
                    targetPage < getFileBrowserPageCount()) {
                setBrowserPage(targetPage);
            }
            return;
        }
        if (screen == HOME && queueCount == 0 && !queueIsText &&
                focusIndex >= 0 && focusIndex < 4) {
            int column = focusIndex % 2;
            if ((direction < 0 && column > 0) ||
                    (direction > 0 && column == 0)) {
                focusIndex += direction;
                keepFocusVisible();
                repaint();
            }
            return;
        }
        moveFocus(direction);
    }

    private void keepFocusVisible() {
        if (peerActionDialogVisible || focusIndex < 0 ||
                focusIndex >= hitCount) {
            return;
        }
        int scale = computeScale(getWidth(), getHeight());
        int headerHeight = px(46, scale);
        int viewportBottom = getHeight();
        if (screen == HOME) {
            viewportBottom -= HomeUi.fixedFooterHeight(this, scale);
        }
        if (viewportBottom < headerHeight) {
            viewportBottom = headerHeight;
        }
        int itemTop = hitY[focusIndex];
        int itemBottom = itemTop + hitHeight[focusIndex];
        if (itemTop < headerHeight) {
            scrollY = Math.max(0, scrollY - (headerHeight - itemTop));
        } else if (itemBottom > viewportBottom) {
            scrollY = Math.min(maxScrollY,
                    scrollY + (itemBottom - viewportBottom));
        }
    }

    private void activateAction(int action) {
        if (action == ACTION_FILE_MENU_ADD) {
            if (!fileActionMenuVisible) {
                return;
            }
            if (selectedFileCount == 0) {
                statusMessage = "Chưa chọn tệp nào.";
                closeFileActionMenu();
                return;
            }
            appendSelectedFiles();
            setScreen(HOME);
            return;
        }
        if (action == ACTION_FILE_MENU_BACK) {
            closeFileActionMenu();
            return;
        }
        if (action >= ACTION_OPEN_NEAR_PEER_BASE &&
                action < ACTION_OPEN_NEAR_PEER_BASE + nearbyPeers.length) {
            int index = action - ACTION_OPEN_NEAR_PEER_BASE;
            openPeerActionDialog(false, index, nearbyPeers[index]);
            return;
        }
        if (action >= ACTION_OPEN_SAVED_PEER_BASE &&
                action < ACTION_OPEN_SAVED_PEER_BASE + MAX_SAVED_DEVICES) {
            int index = action - ACTION_OPEN_SAVED_PEER_BASE;
            if (index < savedCount) {
                openPeerActionDialog(true, index, savedPeers[index]);
            }
            return;
        }
        if (action == ACTION_PEER_DIALOG_SEND) {
            PeerDevice peer = peerActionDevice;
            closePeerActionDialog();
            sendToPeer(peer);
            return;
        }
        if (action == ACTION_PEER_DIALOG_SAVE) {
            PeerDevice peer = peerActionDevice;
            closePeerActionDialog();
            savePeerDevice(peer);
            repaint();
            return;
        }
        if (action == ACTION_PEER_DIALOG_REMOVE) {
            int index = peerActionIndex;
            closePeerActionDialog();
            removeSavedDevice(index);
            repaint();
            return;
        }
        if (action >= ACTION_SEND_NEAR_BASE &&
                action < ACTION_SEND_NEAR_BASE + nearbyPeers.length) {
            sendToPeer(nearbyPeers[action - ACTION_SEND_NEAR_BASE]);
            return;
        }
        if (action >= ACTION_SEND_SAVED_BASE &&
                action < ACTION_SEND_SAVED_BASE + savedCount) {
            sendToPeer(savedPeers[action - ACTION_SEND_SAVED_BASE]);
            return;
        }
        if (action >= ACTION_SAVE_PEER_BASE &&
                action < ACTION_SAVE_PEER_BASE + nearbyPeers.length) {
            savePeerDevice(nearbyPeers[action - ACTION_SAVE_PEER_BASE]);
            repaint();
            return;
        }
        if (action >= ACTION_REMOVE_SAVED_BASE &&
                action < ACTION_REMOVE_SAVED_BASE + MAX_SAVED_DEVICES) {
            removeSavedDevice(action - ACTION_REMOVE_SAVED_BASE);
            repaint();
            return;
        }
        if (action >= ACTION_BROWSER_ENTRY_BASE &&
                action < ACTION_BROWSER_ENTRY_BASE + MAX_BROWSER_ENTRIES) {
            openBrowserEntry(action - ACTION_BROWSER_ENTRY_BASE);
            return;
        }

        if (action == ACTION_FILES || action == ACTION_ADD) {
            requestFilePicker(false);
        } else if (action == ACTION_MEDIA) {
            requestFilePicker(true);
        } else if (action == ACTION_BROWSE_RECEIVE) {
            beginReceiveLocationPicker();
        } else if (action == ACTION_RECEIVE_DEFAULT) {
            setReceiveLocation(null);
        } else if (action == ACTION_RECEIVE_SAVE_HERE) {
            if (!Jsr75FileStorage.isStorageRoot(browserPath)) {
                statusMessage = "Hãy mở ổ C:/ hoặc E:/ rồi chọn Dùng ổ này.";
                repaint();
            } else {
                setReceiveLocation(browserPath);
            }
        } else if (action == ACTION_PASTE || action == ACTION_TEXT) {
            requestTextComposer();
        } else if (action == ACTION_EDIT_TEXT) {
            host.requestMessageInput(textDraft);
        } else if (action == ACTION_REMOVE_QUEUE) {
            if (queueIsText) {
                queueIsText = false;
                queueCount = 0;
                clearQueueFileData();
            } else if (queueCount > 0) {
                queueCount--;
                queueFiles[queueCount] = null;
                queueFilePaths[queueCount] = null;
                queueFileSizes[queueCount] = -1;
            }
            statusMessage = "Đã cập nhật hàng chờ.";
            repaint();
        } else if (action == ACTION_SEND_QUEUE) {
            statusMessage = "Chọn một thiết bị để gửi nội dung.";
            repaint();
        } else if (action == ACTION_SEND_TEXT) {
            if (textDraft == null || textDraft.length() == 0) {
                statusMessage = "Tin nhắn đang trống.";
                repaint();
            } else if (queueCount > 0 &&
                    !queueIsText && !replacementConfirmed) {
                replaceWithText = true;
                setScreen(CONFIRM_REPLACE);
            } else {
                queueIsText = true;
                queueCount = 0;
                clearQueueFileData();
                replacementConfirmed = false;
                statusMessage = "Đã thêm tin nhắn. Chọn thiết bị để gửi.";
                setScreen(HOME);
            }
        } else if (action == ACTION_REFRESH) {
            statusMessage = "Đang tìm thiết bị qua Wi-Fi…";
            host.refreshPeers();
            repaint();
        } else if (action == ACTION_MANUAL) {
            host.requestAddressInput(manualAddress);
        } else if (action == ACTION_ADD_SELECTED) {
            appendSelectedFiles();
            setScreen(HOME);
        } else if (action == ACTION_HEADER_BACK) {
            goBack();
        } else if (action == ACTION_BROWSER_PARENT) {
            goToBrowserParent();
        } else if (action == ACTION_BROWSER_PREVIOUS_PAGE) {
            setBrowserPage(browserPage - 1);
        } else if (action == ACTION_BROWSER_NEXT_PAGE) {
            setBrowserPage(browserPage + 1);
        } else if (action == ACTION_BROWSER_RETRY) {
            loadBrowserDirectory(browserPath);
        } else if (action == ACTION_QUEUE_TEXT) {
            if (textDraft.length() == 0) {
                statusMessage = "Tin nhắn đang trống.";
            } else if (queueCount > 0 && !queueIsText &&
                    !replacementConfirmed) {
                replaceWithText = true;
                setScreen(CONFIRM_REPLACE);
            } else {
                queueIsText = true;
                queueCount = 0;
                clearQueueFileData();
                replacementConfirmed = false;
                statusMessage = "Đã thêm tin nhắn. Chọn thiết bị để gửi.";
                setScreen(HOME);
            }
        } else if (action == ACTION_ACCEPT) {
            if (incomingOffer == null) {
                statusMessage = "Yêu cầu nhận không còn hoạt động.";
                setScreen(HOME);
                return;
            }
            host.resolveIncomingOffer(incomingOffer, true);
            outgoing = false;
            incomingTextTransfer = incomingOffer.text;
            progressPercent = 0;
            progressTransferred = 0;
            progressTotal = incomingOffer.totalBytes;
            setScreen(PROGRESS);
        } else if (action == ACTION_DECLINE) {
            if (incomingOffer != null) {
                host.resolveIncomingOffer(incomingOffer, false);
                incomingOffer = null;
            }
            statusMessage = "Đã từ chối yêu cầu nhận nội dung.";
            setScreen(HOME);
        } else if (action == ACTION_PROGRESS_CANCEL) {
            if (outgoing) {
                host.cancelTransfer();
                finishOutgoingTransfer(false, "Đã hủy truyền.");
            } else {
                statusMessage = "Đang nhận nội dung từ thiết bị.";
                repaint();
            }
        } else if (action == ACTION_CLOSE) {
            setScreen(HOME);
        } else if (action == ACTION_RETRY) {
            if (transferPeer != null) {
                sendToPeer(transferPeer);
            } else {
                statusMessage = "Chọn lại thiết bị để thử gửi.";
                setScreen(HOME);
            }
        } else if (action == ACTION_DETAILS) {
            detailsExpanded = !detailsExpanded;
            repaint();
        } else if (action == ACTION_SAVE_TEXT) {
            statusMessage = "Đang lưu Tin nhắn.txt…";
            repaint();
            host.saveReceivedText(receivedMessage);
        } else if (action == ACTION_REMOVE_SAVED) {
            removeSavedDevice(savedCount - 1);
            repaint();
        } else if (action == ACTION_CONFIRM_REPLACE) {
            if (replaceWithText) {
                replacementConfirmed = true;
                setScreen(TEXT_COMPOSER);
            } else {
                queueIsText = false;
                queueCount = 0;
                clearQueueFileData();
                beginFilePicker();
            }
        } else if (action == ACTION_CANCEL_REPLACE) {
            replacementConfirmed = false;
            setScreen(HOME);
        }
    }

    private void clearFileSelection() {
        int i;
        for (i = 0; i < selectedBrowserNames.length; i++) {
            selectedBrowserNames[i] = null;
            selectedBrowserPaths[i] = null;
            selectedBrowserSizes[i] = -1;
        }
        selectedFileCount = 0;
    }

    private void appendSelectedFiles() {
        int i;
        int added = 0;
        boolean limitReached = false;
        if (selectedFileCount == 0) {
            return;
        }
        if (queueIsText) {
            queueIsText = false;
            queueCount = 0;
            clearQueueFileData();
        }
        if (realFileBrowser) {
            for (i = 0; i < selectedFileCount; i++) {
                if (selectedBrowserPaths[i] == null ||
                        containsQueuePath(selectedBrowserPaths[i])) {
                    continue;
                }
                if (queueCount >= 20) {
                    limitReached = true;
                    break;
                }
                queueFiles[queueCount] = selectedBrowserNames[i];
                queueFilePaths[queueCount] = selectedBrowserPaths[i];
                queueFileSizes[queueCount] = selectedBrowserSizes[i];
                queueCount++;
                added++;
            }
        }
        queueIsText = false;
        if (limitReached) {
            statusMessage = "Tối đa 20 tệp trong một lần gửi.";
        } else {
            statusMessage = added == 0 ?
                    "Chưa chọn tệp mới để thêm." :
                    "Đã thêm " + added + " tệp vào hàng chờ.";
        }
    }

    private void requestFilePicker(boolean chooseMediaOnly) {
        mediaOnly = chooseMediaOnly;
        if (queueIsText) {
            replaceWithText = false;
            setScreen(CONFIRM_REPLACE);
            return;
        }
        beginFilePicker();
    }

    private void beginFilePicker() {
        receiveLocationMode = false;
        clearFileSelection();
        statusMessage = "";
        realFileBrowser = true;
        browserDepth = 0;
        browserPage = 0;
        browserPath = null;
        int i;
        for (i = 0; i < browserHistory.length; i++) {
            browserHistory[i] = null;
        }
        setScreen(FILES);
        loadBrowserDirectory(null);
    }

    private void beginReceiveLocationPicker() {
        receiveLocationMode = true;
        mediaOnly = false;
        clearFileSelection();
        statusMessage = "";
        realFileBrowser = true;
        browserDepth = 0;
        browserPage = 0;
        browserPath = null;
        int i;
        for (i = 0; i < browserHistory.length; i++) {
            browserHistory[i] = null;
        }
        setScreen(FILES);
        loadBrowserDirectory(null);
    }

    private void setReceiveLocation(String directory) {
        try {
            Jsr75FileStorage.setReceiveDirectory(directory);
            statusMessage = directory == null ?
                    "Đã đặt bộ nhớ nhận tự động." :
                    "Đã chọn " + directory +
                            " và kiểm tra quyền ghi các thư mục phân loại.";
            setScreen(HOME);
        } catch (IOException exception) {
            statusMessage = exception.getMessage();
            repaint();
        }
    }

    private void requestTextComposer() {
        if (queueCount > 0 && !queueIsText && !replacementConfirmed) {
            replaceWithText = true;
            setScreen(CONFIRM_REPLACE);
            return;
        }
        setScreen(TEXT_COMPOSER);
    }

    private boolean containsQueuePath(String path) {
        int i;
        for (i = 0; i < queueCount; i++) {
            if (queueFilePaths[i] != null &&
                    queueFilePaths[i].equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void clearQueueFileData() {
        int i;
        for (i = 0; i < queueFiles.length; i++) {
            queueFiles[i] = null;
            queueFilePaths[i] = null;
            queueFileSizes[i] = -1;
        }
    }

    private void loadBrowserDirectory(String directory) {
        browserPath = directory;
        browserEntries = new FileBrowserEntry[0];
        browserPage = 0;
        browserState = FileBrowserListing.READY;
        browserMessage = "Đang đọc thư mục…";
        fileBrowserLoading = true;
        final int requestId = ++browserRequestId;
        try {
            new FileBrowserWorker(this, requestId, directory, mediaOnly,
                    receiveLocationMode).start();
        } catch (SecurityException exception) {
            fileBrowserLoading = false;
            browserState = FileBrowserListing.ERROR;
            browserMessage =
                    "Thiết bị không cho phép khởi chạy trình đọc bộ nhớ.";
        }
        repaint();
    }

    private void openBrowserEntry(int index) {
        if (!realFileBrowser || fileBrowserLoading ||
                index < 0 || index >= browserEntries.length) {
            return;
        }
        FileBrowserEntry entry = browserEntries[index];
        if (receiveLocationMode && !entry.directory) {
            return;
        }
        if (entry.directory) {
            if (browserDepth >= MAX_BROWSER_DEPTH) {
                statusMessage = "Đã đạt giới hạn độ sâu thư mục.";
                repaint();
                return;
            }
            browserHistory[browserDepth++] = browserPath;
            loadBrowserDirectory(entry.path);
            return;
        }

        int selectedIndex = findSelectedBrowserPath(entry.path);
        if (selectedIndex >= 0) {
            removeBrowserSelection(selectedIndex);
            entry.selected = false;
        } else if (selectedFileCount >= selectedBrowserPaths.length) {
            statusMessage = "Tối đa 20 tệp được chọn.";
        } else {
            selectedBrowserNames[selectedFileCount] = entry.name;
            selectedBrowserPaths[selectedFileCount] = entry.path;
            selectedBrowserSizes[selectedFileCount] = -1;
            selectedFileCount++;
            entry.selected = true;
            statusMessage = selectedFileCount + " mục được chọn.";
        }
        repaint();
    }

    private int findSelectedBrowserPath(String path) {
        int i;
        for (i = 0; i < selectedFileCount; i++) {
            if (selectedBrowserPaths[i] != null &&
                    selectedBrowserPaths[i].equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private void removeBrowserSelection(int index) {
        int i;
        for (i = index; i < selectedFileCount - 1; i++) {
            selectedBrowserNames[i] = selectedBrowserNames[i + 1];
            selectedBrowserPaths[i] = selectedBrowserPaths[i + 1];
            selectedBrowserSizes[i] = selectedBrowserSizes[i + 1];
        }
        selectedFileCount--;
        selectedBrowserNames[selectedFileCount] = null;
        selectedBrowserPaths[selectedFileCount] = null;
        selectedBrowserSizes[selectedFileCount] = -1;
        statusMessage = selectedFileCount == 0 ? "" :
                selectedFileCount + " mục được chọn.";
    }

    private void goToBrowserParent() {
        if (browserDepth > 0) {
            int parentIndex = --browserDepth;
            String parent = browserHistory[parentIndex];
            browserHistory[parentIndex] = null;
            loadBrowserDirectory(parent);
        } else {
            setScreen(HOME);
        }
    }

    private void setBrowserPage(int requestedPage) {
        int pageCount = getFileBrowserPageCount();
        browserPage = clamp(requestedPage, 0, pageCount - 1);
        scrollY = 0;
        focusIndex = 0;
        repaint();
    }

    private String formatFileSize(long size) {
        if (size < 0) {
            return "Tệp";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        }
        return (size / (1024 * 1024)) + " MB";
    }

    private void setScreen(int nextScreen) {
        if (screen == RESULT_SUCCESS && outgoing && nextScreen == HOME) {
            clearCompletedOutgoingSelection();
        }
        if (screen == FILES && nextScreen != FILES) {
            browserRequestId++;
            fileBrowserLoading = false;
            realFileBrowser = false;
            receiveLocationMode = false;
        }
        peerActionDialogVisible = false;
        fileActionMenuVisible = false;
        screen = nextScreen;
        scrollY = 0;
        focusIndex = 0;
        hitCount = 0;
        repaint();
    }

    private void clearCompletedOutgoingSelection() {
        queueCount = 0;
        queueIsText = false;
        clearQueueFileData();
        clearFileSelection();
        textDraft = "";
        replacementConfirmed = false;
        replaceWithText = false;
        transferPeer = null;
        outgoing = false;
        statusMessage = "";
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}