package com.nearbyshare.legacy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_FILE = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;
    private static final long DISCOVERY_SETTLE_MS = 5000L;

    private final HandlerBridge ui = new HandlerBridge();
    private final Handler refreshHandler = new Handler();
    private final Runnable connectionRefresh = new Runnable() {
        public void run() {
            if (addressText != null) {
                refreshConnectionText();
                refreshHandler.postDelayed(this, 3000L);
            }
        }
    };
    private final Runnable discoveryRefreshTimeout = new Runnable() {
        public void run() {
            if (discoveryRefreshPending) {
                discoveryRefreshPending = false;
                renderPeers();
            }
        }
    };
    private final Runnable discoverySettleTimeout = new Runnable() {
        public void run() {
            discoverySettlePending = false;
            if (discovery != null && peers.size() > 0) {
                stopDiscoveryAfterFinding();
            }
        }
    };
    private TextView addressText;
    private TextView statusText;
    private TextView permissionText;
    private LinearLayout peerList;
    private LinearLayout savedPeerList;
    private View sourceRow;
    private LinearLayout queueCard;
    private LinearLayout queueItems;
    private TextView queueSummary;
    private View homeScreen;
    private String pendingText;
    private final HashMap<String, TransferProgressScreen> progressScreens =
            new HashMap<String, TransferProgressScreen>();
    private TransferProgressScreen activeProgressScreen;
    private String activeTransferId;
    private boolean showingTextMessage;
    private ArrayList<Peer> peers = new ArrayList<Peer>();
    private LanShareServer server;
    private PeerDiscovery discovery;
    private int discoveryGeneration;
    private boolean discoveryRefreshPending;
    private boolean discoverySettlePending;
    private ArrayList<Uri> pendingUris = new ArrayList<Uri>();
    private ArrayList<SavedDevice> savedDevices = new ArrayList<SavedDevice>();
    private boolean savedDeviceLoadFailed;
    private final ArrayList<IncomingOfferRequest> incomingOfferQueue =
            new ArrayList<IncomingOfferRequest>();
    private Dialog incomingOfferDialog;
    private IncomingOfferRequest activeOfferRequest;
    private boolean closing;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSavedDevices();
        buildScreen();
        if (savedDeviceLoadFailed) {
            Toast.makeText(this, "Không đọc được danh sách thiết bị đã lưu.",
                    Toast.LENGTH_LONG).show();
        }
        appendSelectedUris(readIncomingSharedUris(getIntent()));
        refreshConnectionText();
        ensureStoragePermissionAndStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ArrayList<Uri> sharedUris = readIncomingSharedUris(intent);
        if (sharedUris.size() > 0) {
            appendSelectedUris(sharedUris);
        }
    }

    private void loadSavedDevices() {
        try {
            savedDevices = SavedDeviceStore.load(this);
        } catch (org.json.JSONException e) {
            savedDevices = new ArrayList<SavedDevice>();
            savedDeviceLoadFailed = true;
        }
    }

    private ArrayList<Uri> readIncomingSharedUris(Intent intent) {
        ArrayList<Uri> uris = ShareIntentReader.read(intent);
        if (intent != null && Build.VERSION.SDK_INT >= 16 &&
                (Intent.ACTION_SEND.equals(intent.getAction()) ||
                        Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()))) {
            ShareIntentReaderApi16.appendClipUris(intent, uris);
        }
        return uris;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionText();
        refreshHandler.removeCallbacks(connectionRefresh);
        refreshHandler.postDelayed(connectionRefresh, 3000L);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(connectionRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closing = true;
        refreshHandler.removeCallbacks(connectionRefresh);
        refreshHandler.removeCallbacks(discoveryRefreshTimeout);
        refreshHandler.removeCallbacks(discoverySettleTimeout);
        if (discovery != null) {
            discovery.stop();
        }
        if (server != null) {
            server.stop();
        }
        denyPendingOffers();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE) {
            return;
        }
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<Uri> chosen;
            if (Build.VERSION.SDK_INT >= 18) {
                chosen = MultiSelectPickerApi18.getSelectedUris(data);
            } else {
                chosen = new ArrayList<Uri>();
            }
            if (Build.VERSION.SDK_INT < 18 && data.getData() != null) {
                chosen.add(data.getData());
            }
            appendSelectedUris(chosen);
        }
        updateQueueUi();
    }

    private void startSendingSelectedFiles(final Peer peer) {
        if (peer == null || pendingUris.size() == 0) {
            return;
        }
        final ArrayList<Uri> files = new ArrayList<Uri>(pendingUris);
        pendingUris.clear();
        updateQueueUi();
        FileSender.send(this, peer, files, deviceName(), createSenderListener(false));
    }

    private void startSendingText(final Peer peer) {
        if (peer == null || pendingText == null || pendingText.length() == 0) {
            return;
        }
        final String text = pendingText;
        pendingText = null;
        updateQueueUi();
        FileSender.sendText(peer, text, deviceName(), createSenderListener(true));
    }

    private FileSender.Listener createSenderListener(final boolean textTransfer) {
        return new FileSender.Listener() {
            public void onStatus(final String transferId, final String message) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.setStatus(message);
                        }
                        setStatus(message);
                    }
                });
            }

            public void onAccepted(final String transferId,
                                   final ArrayList<IncomingOffer.Item> items) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        String title = textTransfer ?
                                "Đang gửi văn bản" : "Đang gửi tập tin";
                        showTransferProgress(transferId, title, items);
                    }
                });
            }

            public void onProgress(final String transferId, final int itemIndex,
                                   final long transferred, final long totalBytes) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.updateProgress(itemIndex, transferred, totalBytes);
                        }
                    }
                });
            }

            public void onItemComplete(final String transferId, final int itemIndex) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.completeItem(itemIndex);
                        }
                    }
                });
            }

            public void onComplete(final String transferId, final String message) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.finish(message);
                        }
                        setStatus(message);
                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_LONG).show();
                        showNextIncomingOffer();
                    }
                });
            }

            public void onFailure(final String transferId, final String message) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.fail(message);
                        }
                        setStatus(message);
                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_LONG).show();
                        showNextIncomingOffer();
                    }
                });
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                permissionText.setVisibility(View.GONE);
                startServices();
            } else {
                permissionText.setVisibility(View.VISIBLE);
                permissionText.setText(
                        "Cần quyền lưu trữ để nhận file. Chạm vào đây để thử lại.");
            }
        }
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setBackgroundColor(MaterialUi.BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(MaterialUi.BACKGROUND);

        LinearLayout page = column();
        page.setPadding(dp(20), dp(26), dp(20), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView title = text("Lựa chọn", 20, MaterialUi.ON_SURFACE, true);
        page.addView(title, fullWidth());

        LinearLayout sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.HORIZONTAL);
        sources.setGravity(Gravity.CENTER_VERTICAL);
        sources.setPadding(dp(1), 0, dp(12), 0);
        HorizontalScrollView sourceScroll = new HorizontalScrollView(this);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.setFillViewport(false);
        sourceRow = sourceScroll;
        LinearLayout.LayoutParams sourceParams = fullWidth();
        sourceParams.topMargin = dp(18);
        page.addView(sourceScroll, sourceParams);
        sourceScroll.addView(sources, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sources.addView(sourceTile(MaterialIconView.FILE, "Tệp", 1),
                sourceTileParams());
        sources.addView(sourceTile(MaterialIconView.MEDIA, "Media", 2),
                sourceTileParams());
        sources.addView(sourceTile(MaterialIconView.CLIPBOARD, "Dán", 3),
                sourceTileParams());
        sources.addView(sourceTile(MaterialIconView.EDIT, "Nhập", 4),
                sourceTileParams());

        queueCard = column();
        queueCard.setPadding(dp(14), dp(13), dp(14), dp(14));
        queueCard.setBackgroundDrawable(darkCardBackground());
        LinearLayout.LayoutParams queueParams = fullWidth();
        queueParams.topMargin = dp(18);
        page.addView(queueCard, queueParams);
        queueCard.setVisibility(View.GONE);

        LinearLayout queueHeader = new LinearLayout(this);
        queueHeader.setOrientation(LinearLayout.HORIZONTAL);
        queueHeader.setGravity(Gravity.CENTER_VERTICAL);
        queueCard.addView(queueHeader, fullWidth());
        TextView queueTitle = text("Đã chọn", 17, MaterialUi.ON_SURFACE, true);
        queueHeader.addView(queueTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button addButton = button("+ Thêm");
        addButton.setTextColor(MaterialUi.PRIMARY);
        addButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        addButton.setPadding(dp(12), dp(8), dp(8), dp(8));
        addButton.setBackgroundDrawable(MaterialUi.textButtonBackground(this));
        queueHeader.addView(addButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        addButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showSourceMenu();
            }
        });

        queueSummary = text("", 12, MaterialUi.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams summaryParams = fullWidth();
        summaryParams.topMargin = dp(3);
        queueCard.addView(queueSummary, summaryParams);
        queueItems = column();
        LinearLayout.LayoutParams itemsParams = fullWidth();
        itemsParams.topMargin = dp(8);
        queueCard.addView(queueItems, itemsParams);

        permissionText = text("", 14, MaterialUi.ERROR, true);
        permissionText.setPadding(dp(16), dp(14), dp(16), dp(14));
        permissionText.setBackgroundDrawable(cardBackground(MaterialUi.ERROR_CONTAINER));
        permissionText.setVisibility(View.GONE);
        LinearLayout.LayoutParams permissionParams = fullWidth();
        permissionParams.topMargin = dp(12);
        page.addView(permissionText, permissionParams);
        permissionText.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                ensureStoragePermissionAndStart();
            }
        });

        LinearLayout nearbyHeader = new LinearLayout(this);
        nearbyHeader.setOrientation(LinearLayout.HORIZONTAL);
        nearbyHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nearbyHeaderParams = fullWidth();
        nearbyHeaderParams.topMargin = dp(23);
        nearbyHeaderParams.bottomMargin = dp(8);
        page.addView(nearbyHeader, nearbyHeaderParams);
        TextView nearbyTitle = text("Thiết bị quanh đây", 20,
                MaterialUi.ON_SURFACE, true);
        nearbyHeader.addView(nearbyTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView refreshButton = text("Làm mới", 13, MaterialUi.PRIMARY, true);
        refreshButton.setGravity(Gravity.CENTER);
        refreshButton.setPadding(dp(10), dp(8), dp(2), dp(8));
        refreshButton.setBackgroundDrawable(MaterialUi.textButtonBackground(this));
        refreshButton.setFocusable(true);
        refreshButton.setClickable(true);
        refreshButton.setContentDescription("Làm mới danh sách thiết bị");
        nearbyHeader.addView(refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                restartPeerDiscovery();
            }
        });

        peerList = column();
        page.addView(peerList, fullWidth());
        renderPeers();

        Button manualButton = button("Nhập địa chỉ thủ công");
        manualButton.setTextColor(MaterialUi.ON_SURFACE);
        manualButton.setBackgroundDrawable(MaterialUi.surfaceButtonBackground(this));
        LinearLayout.LayoutParams manualParams = fullWidth();
        manualParams.topMargin = dp(10);
        page.addView(manualButton, manualParams);
        manualButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showManualAddressDialog();
            }
        });

        TextView savedTitle = text("Đã lưu", 20, MaterialUi.ON_SURFACE, true);
        LinearLayout.LayoutParams savedTitleParams = fullWidth();
        savedTitleParams.topMargin = dp(18);
        savedTitleParams.bottomMargin = dp(8);
        page.addView(savedTitle, savedTitleParams);
        savedPeerList = column();
        page.addView(savedPeerList, fullWidth());
        renderSavedDevices();

        statusText = text("", 12, MaterialUi.ON_SURFACE_VARIANT, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerStatusParams = fullWidth();
        footerStatusParams.topMargin = dp(12);
        page.addView(statusText, footerStatusParams);
        statusText.setVisibility(View.GONE);

        View footerDivider = new View(this);
        footerDivider.setBackgroundColor(MaterialUi.SURFACE_HIGH);
        root.addView(footerDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout ipFooter = column();
        ipFooter.setGravity(Gravity.CENTER);
        ipFooter.setPadding(dp(20), dp(9), dp(20), dp(10));
        ipFooter.setBackgroundColor(MaterialUi.SURFACE);

        TextView addressLabel = text("Địa chỉ IP của máy này", 12,
                MaterialUi.ON_SURFACE_VARIANT, false);
        addressLabel.setGravity(Gravity.CENTER);
        ipFooter.addView(addressLabel, fullWidth());

        addressText = text("Đang kiểm tra Wi-Fi…", 15,
                MaterialUi.ON_SURFACE, true);
        addressText.setGravity(Gravity.CENTER);
        addressText.setSingleLine(true);
        ipFooter.addView(addressText, fullWidth());
        root.addView(ipFooter, fullWidth());

        homeScreen = root;
        setContentView(homeScreen);
        updateQueueUi();
    }

    private LinearLayout.LayoutParams sourceTileParams() {
        int size = homeSourceTileSize();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                size, size);
        params.rightMargin = dp(10);
        return params;
    }

    private int homeSourceTileSize() {
        int viewportWidth = getResources().getDisplayMetrics().widthPixels -
                dp(40) - dp(13);
        return Math.max(dp(80), (viewportWidth - dp(50)) / 3);
    }

    private View sourceTile(int iconKind, String label, final int source) {
        return sourceTile(iconKind, label, new View.OnClickListener() {
            public void onClick(View view) {
                handleSourceChoice(source);
            }
        });
    }

    private View sourceTile(int iconKind, String label,
                            View.OnClickListener clickListener) {
        return sourceTile(iconKind, label, clickListener, false);
    }

    private View sourceTile(int iconKind, String label,
                            View.OnClickListener clickListener, boolean compact) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER);
        int verticalPadding = compact ? 4 : 10;
        tile.setPadding(dp(4), dp(verticalPadding), dp(4), dp(verticalPadding - 1));
        tile.setBackgroundDrawable(MaterialUi.stateBackground(this,
                MaterialUi.SURFACE_CONTAINER, MaterialUi.SURFACE_HIGH,
                MaterialUi.SURFACE_CONTAINER, 12));
        MaterialIconView icon = new MaterialIconView(this, iconKind);
        icon.setTint(MaterialUi.PRIMARY);
        int iconSize = compact ? 28 : 34;
        tile.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        TextView name = text(label, compact ? 12 : 13,
                MaterialUi.ON_SURFACE, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        LinearLayout.LayoutParams nameParams = fullWidth();
        nameParams.topMargin = dp(compact ? 4 : 7);
        tile.addView(name, nameParams);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(clickListener);
        return tile;
    }

    private void ensureStoragePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 23 &&
                !StoragePermissionsApi23.granted(this)) {
            requestStoragePermission();
            return;
        }
        permissionText.setVisibility(View.GONE);
        startServices();
    }

    private void requestStoragePermission() {
        permissionText.setVisibility(View.VISIBLE);
        permissionText.setText("Cần quyền lưu trữ để nhận file. Chạm vào đây để cho phép.");
        StoragePermissionsApi23.request(this, REQUEST_STORAGE_PERMISSION);
    }

    private void startServices() {
        if (server != null) {
            return;
        }

        server = new LanShareServer(new LanShareServer.Listener() {
            public void onListening() {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        setStatus("");
                    }
                });
            }

            public void onTransferOffered(final IncomingOffer offer,
                                          final LanShareServer.DecisionCallback decision) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        queueIncomingOffer(offer, decision);
                    }
                });
            }

            public void onTransferProgress(final String transferId, final int itemIndex,
                                           final long transferred, final long totalBytes) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.updateProgress(itemIndex, transferred, totalBytes);
                        }
                    }
                });
            }

            public void onTransferFailed(final String transferId, final String message) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.fail(message);
                        }
                        setStatus(message);
                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_LONG).show();
                        showNextIncomingOffer();
                    }
                });
            }

            public void onFileSaved(final String transferId, final String name, final File file,
                                    final int fileNumber, final int fileCount) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        String message = fileCount > 1 ?
                                "Đã nhận " + fileNumber + "/" + fileCount + ": " + name :
                                "Đã nhận " + name;
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.completeItem(fileNumber - 1, file);
                            if (fileNumber == fileCount) {
                                screen.finish("Đã nhận " + fileCount + " tập tin.");
                            }
                        }
                        setStatus(message + " — Download/Near Transfer/");
                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_LONG).show();
                        if (fileNumber == fileCount) {
                            showNextIncomingOffer();
                        }
                    }
                });
            }

            public void onTextReceived(final String transferId, final String senderName,
                                       final String text) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        TransferProgressScreen screen = progressScreens.get(transferId);
                        if (screen != null) {
                            screen.completeItem(0);
                            screen.finish("Đã nhận văn bản.");
                        }
                        showTextMessage(senderName, text);
                    }
                });
            }

            public void onError(final String message) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        setStatus(message);
                    }
                });
            }
        });
        server.start();

        startPeerDiscovery();
        refreshConnectionText();
    }

    private void startPeerDiscovery() {
        final int generation = ++discoveryGeneration;
        PeerDiscovery nextDiscovery = new PeerDiscovery(this, new PeerDiscovery.Listener() {
            public void onPeersChanged(final ArrayList<Peer> updatedPeers) {
                ui.post(MainActivity.this, new Runnable() {
                    public void run() {
                        if (generation != discoveryGeneration) {
                            return;
                        }
                        peers = updatedPeers;
                        discoveryRefreshPending = false;
                        refreshHandler.removeCallbacks(discoveryRefreshTimeout);
                        renderPeers();
                        if (updatedPeers.size() > 0 && !discoverySettlePending) {
                            discoverySettlePending = true;
                            refreshHandler.postDelayed(discoverySettleTimeout,
                                    DISCOVERY_SETTLE_MS);
                        }
                    }
                });
            }
        });
        discovery = nextDiscovery;
        nextDiscovery.start();
    }

    private void stopDiscoveryAfterFinding() {
        // Allow late peers to answer before freezing the list until manual refresh.
        refreshHandler.removeCallbacks(discoverySettleTimeout);
        discoverySettlePending = false;
        PeerDiscovery foundDiscovery = discovery;
        discovery = null;
        discoveryGeneration++;
        if (foundDiscovery != null) {
            foundDiscovery.stop();
        }
    }

    private void restartPeerDiscovery() {
        if (server == null) {
            ensureStoragePermissionAndStart();
            return;
        }
        PeerDiscovery previous = discovery;
        discovery = null;
        discoveryGeneration++;
        refreshHandler.removeCallbacks(discoverySettleTimeout);
        discoverySettlePending = false;
        if (previous != null) {
            previous.stop();
        }
        peers.clear();
        discoveryRefreshPending = true;
        refreshHandler.removeCallbacks(discoveryRefreshTimeout);
        refreshHandler.postDelayed(discoveryRefreshTimeout, 4000L);
        renderPeers();
        startPeerDiscovery();
    }

    private void queueIncomingOffer(IncomingOffer offer,
                                   LanShareServer.DecisionCallback decision) {
        if (closing || isFinishing()) {
            decision.decide(false);
            return;
        }
        incomingOfferQueue.add(new IncomingOfferRequest(offer, decision));
        showNextIncomingOffer();
    }

    private void showNextIncomingOffer() {
        if (closing || isFinishing() || incomingOfferDialog != null ||
                showingTextMessage || incomingOfferQueue.size() == 0 ||
                (activeProgressScreen != null && !activeProgressScreen.isFinished())) {
            return;
        }
        activeOfferRequest = incomingOfferQueue.remove(0);
        final IncomingOfferRequest request = activeOfferRequest;
        final Dialog dialog = MaterialDialog.show(this,
                request.offer.textTransfer ?
                        "Yêu cầu nhận văn bản" : "Yêu cầu nhận tập tin",
                request.offer.summary(), null, "Từ chối",
                new MaterialDialog.Action() {
                    public boolean onClick(Dialog ignored) {
                        request.decision.decide(false);
                        setStatus("Đã từ chối yêu cầu nhận nội dung.");
                        return true;
                    }
                }, request.offer.textTransfer ?
                        "Nhận văn bản" : "Nhận tập tin",
                new MaterialDialog.Action() {
                    public boolean onClick(Dialog ignored) {
                        request.decision.decide(true);
                        String title = request.offer.textTransfer ?
                                "Đang nhận văn bản" : "Đang nhận tập tin";
                        showTransferProgress(request.offer.transferId, title,
                                request.offer.files);
                        setStatus("Đã chấp thuận. Đang nhận nội dung…");
                        return true;
                    }
                });
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface ignored) {
                request.decision.decide(false);
                setStatus("Đã từ chối yêu cầu nhận nội dung.");
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface ignored) {
                if (incomingOfferDialog == ignored) {
                    incomingOfferDialog = null;
                    activeOfferRequest = null;
                }
                showNextIncomingOffer();
            }
        });
        incomingOfferDialog = dialog;
    }

    private void denyPendingOffers() {
        if (activeOfferRequest != null) {
            activeOfferRequest.decision.decide(false);
            activeOfferRequest = null;
        }
        for (IncomingOfferRequest request : incomingOfferQueue) {
            request.decision.decide(false);
        }
        incomingOfferQueue.clear();
        if (incomingOfferDialog != null) {
            Dialog dialog = incomingOfferDialog;
            incomingOfferDialog = null;
            dialog.setOnCancelListener(null);
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
    }

    private void refreshConnectionText() {
        String ip = getWifiAddress();
        if (ip == null) {
            addressText.setText("Chưa kết nối Wi-Fi");
        } else {
            addressText.setText(ip + ":" + LanShareServer.HTTP_PORT);
        }
    }

    private String getWifiAddress() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) {
                return null;
            }
            int address = wifi.getConnectionInfo().getIpAddress();
            if (address == 0) {
                return null;
            }
            return String.format(Locale.US, "%d.%d.%d.%d",
                    address & 0xff, (address >> 8) & 0xff,
                    (address >> 16) & 0xff, (address >> 24) & 0xff);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderPeers() {
        if (peerList == null) {
            return;
        }
        peerList.removeAllViews();
        ArrayList<Peer> sorted = new ArrayList<Peer>(peers);
        Collections.sort(sorted, new Comparator<Peer>() {
            public int compare(Peer left, Peer right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        if (sorted.size() == 0) {
            String emptyMessage = discoveryRefreshPending ?
                    "Đang làm mới tìm kiếm thiết bị…" :
                    "Chưa thấy thiết bị nào. Mở Near Transfer trên máy còn lại hoặc nhập địa chỉ trực tiếp.";
            TextView empty = text(emptyMessage, 14,
                    MaterialUi.ON_SURFACE_VARIANT, false);
            empty.setPadding(dp(14), dp(15), dp(14), dp(15));
            empty.setBackgroundDrawable(darkCardBackground());
            peerList.addView(empty, fullWidth());
            return;
        }

        for (final Peer peer : sorted) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(10), dp(10), dp(10));
            row.setBackgroundDrawable(MaterialUi.stateBackground(this,
                    MaterialUi.SURFACE_CONTAINER, MaterialUi.SURFACE_HIGH,
                    MaterialUi.SURFACE_CONTAINER, 10));
            row.setFocusable(true);
            LinearLayout.LayoutParams rowParams = fullWidth();
            rowParams.bottomMargin = dp(8);
            peerList.addView(row, rowParams);

            LinearLayout details = column();
            row.addView(details, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            details.addView(text(peer.name, 16, MaterialUi.ON_SURFACE, true));
            TextView endpoint = text(peer.endpoint(), 12,
                    MaterialUi.ON_SURFACE_VARIANT, false);
            LinearLayout.LayoutParams endpointParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            endpointParams.topMargin = dp(2);
            details.addView(endpoint, endpointParams);

            View saveAction = iconAction(MaterialIconView.BOOKMARK,
                    findSavedDevice(peer) == null ?
                            "Lưu thiết bị" : "Cập nhật thiết bị đã lưu",
                    new View.OnClickListener() {
                        public void onClick(View view) {
                            showSavePeerDialog(peer);
                        }
                    });
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                    dp(42), dp(44));
            saveParams.leftMargin = dp(4);
            saveParams.rightMargin = dp(10);
            row.addView(saveAction, saveParams);

            Button sendButton = button("Gửi");
            sendButton.setTextColor(MaterialUi.ON_PRIMARY);
            sendButton.setBackgroundDrawable(MaterialUi.primaryButtonBackground(this));
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                    dp(68), dp(44));
            sendParams.leftMargin = dp(4);
            row.addView(sendButton, sendParams);
            sendButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    beginTransferTo(peer);
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    beginTransferTo(peer);
                }
            });
        }
    }

    private void renderSavedDevices() {
        if (savedPeerList == null) {
            return;
        }
        savedPeerList.removeAllViews();
        ArrayList<SavedDevice> sorted = new ArrayList<SavedDevice>(savedDevices);
        Collections.sort(sorted, new Comparator<SavedDevice>() {
            public int compare(SavedDevice left, SavedDevice right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        if (sorted.size() == 0) {
            TextView empty = text(
                    "Chưa có thiết bị. Chạm biểu tượng lưu ở danh sách phía trên.",
                    14, MaterialUi.ON_SURFACE_VARIANT, false);
            empty.setPadding(dp(14), dp(15), dp(14), dp(15));
            empty.setBackgroundDrawable(darkCardBackground());
            savedPeerList.addView(empty, fullWidth());
            return;
        }

        for (final SavedDevice device : sorted) {
            final Peer peer = device.toPeer();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(10), dp(10), dp(10));
            row.setBackgroundDrawable(MaterialUi.stateBackground(this,
                    MaterialUi.SURFACE_CONTAINER, MaterialUi.SURFACE_HIGH,
                    MaterialUi.SURFACE_CONTAINER, 10));
            row.setFocusable(true);
            LinearLayout.LayoutParams rowParams = fullWidth();
            rowParams.bottomMargin = dp(8);
            savedPeerList.addView(row, rowParams);

            LinearLayout details = column();
            row.addView(details, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            details.addView(text(device.name, 16, MaterialUi.ON_SURFACE, true));
            TextView endpoint = text(peer.endpoint(), 12,
                    MaterialUi.ON_SURFACE_VARIANT, false);
            LinearLayout.LayoutParams endpointParams = fullWidth();
            endpointParams.topMargin = dp(2);
            details.addView(endpoint, endpointParams);

            View removeAction = iconAction(MaterialIconView.REMOVE,
                    "Xóa thiết bị đã lưu",
                    new View.OnClickListener() {
                        public void onClick(View view) {
                            confirmRemoveSavedDevice(device);
                        }
                    });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    dp(42), dp(44));
            removeParams.leftMargin = dp(4);
            removeParams.rightMargin = dp(10);
            row.addView(removeAction, removeParams);

            Button sendButton = button("Gửi");
            sendButton.setTextColor(MaterialUi.ON_PRIMARY);
            sendButton.setBackgroundDrawable(MaterialUi.primaryButtonBackground(this));
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                    dp(68), dp(44));
            sendParams.leftMargin = dp(4);
            row.addView(sendButton, sendParams);
            sendButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    beginTransferTo(peer);
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    beginTransferTo(peer);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View view) {
                    showSavePeerDialog(peer);
                    return true;
                }
            });
        }
    }

    private View iconAction(int icon, String description,
                            View.OnClickListener listener) {
        LinearLayout action = new LinearLayout(this);
        action.setGravity(Gravity.CENTER);
        action.setBackgroundDrawable(MaterialUi.stateBackground(this,
                MaterialUi.SURFACE_HIGH, MaterialUi.SURFACE_CONTAINER,
                MaterialUi.SURFACE_HIGH, 10));
        action.setContentDescription(description);
        action.setFocusable(true);
        action.setClickable(true);
        MaterialIconView image = new MaterialIconView(this, icon);
        image.setTint(MaterialUi.PRIMARY);
        action.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
        action.setOnClickListener(listener);
        return action;
    }

    private SavedDevice findSavedDevice(Peer peer) {
        if (peer == null) {
            return null;
        }
        String key = peer.address + ":" + peer.port;
        for (SavedDevice device : savedDevices) {
            if (device.key().equals(key)) {
                return device;
            }
        }
        return null;
    }

    private void showSavePeerDialog(final Peer peer) {
        final SavedDevice existing = findSavedDevice(peer);
        final EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(existing == null ? peer.name : existing.name);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        MaterialUi.styleEditText(this, nameInput);

        final EditText addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setText(peer.address);
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI);
        MaterialUi.styleEditText(this, addressInput);

        LinearLayout fields = column();
        fields.addView(text("Tên máy", 13, MaterialUi.ON_SURFACE_VARIANT, true),
                fullWidth());
        fields.addView(nameInput, fullWidth());
        TextView addressLabel = text("Địa chỉ IP", 13,
                MaterialUi.ON_SURFACE_VARIANT, true);
        LinearLayout.LayoutParams addressLabelParams = fullWidth();
        addressLabelParams.topMargin = dp(12);
        fields.addView(addressLabel, addressLabelParams);
        fields.addView(addressInput, fullWidth());

        Dialog dialog = MaterialDialog.show(this,
                existing == null ? "Lưu thiết bị" : "Sửa thiết bị đã lưu",
                "Tên và địa chỉ IP của thiết bị.", fields,
                "Hủy", null, "Lưu", new MaterialDialog.Action() {
                    public boolean onClick(Dialog dialog) {
                        String name = nameInput.getText().toString().trim();
                        String address = addressInput.getText().toString().trim();
                        if (name.length() == 0 || !isIPv4Address(address)) {
                            Toast.makeText(MainActivity.this,
                                    "Hãy nhập tên máy và địa chỉ IPv4 hợp lệ.",
                                    Toast.LENGTH_LONG).show();
                            return false;
                        }

                        ArrayList<SavedDevice> updated =
                                new ArrayList<SavedDevice>(savedDevices);
                        int replaceIndex = -1;
                        if (existing != null) {
                            for (int i = 0; i < updated.size(); i++) {
                                if (updated.get(i).key().equals(existing.key())) {
                                    replaceIndex = i;
                                    break;
                                }
                            }
                        } else {
                            for (int i = 0; i < updated.size(); i++) {
                                if (updated.get(i).key().equals(
                                        address + ":" + peer.port)) {
                                    replaceIndex = i;
                                    break;
                                }
                            }
                        }
                        String newKey = address + ":" + peer.port;
                        for (int i = 0; i < updated.size(); i++) {
                            if (i != replaceIndex &&
                                    updated.get(i).key().equals(newKey)) {
                                Toast.makeText(MainActivity.this,
                                        "Địa chỉ này đã có trong danh sách đã lưu.",
                                        Toast.LENGTH_LONG).show();
                                return false;
                            }
                        }
                        SavedDevice replacement =
                                new SavedDevice(name, address, peer.port);
                        if (replaceIndex >= 0) {
                            updated.set(replaceIndex, replacement);
                        } else {
                            updated.add(replacement);
                        }
                        if (!persistSavedDevices(updated)) {
                            return false;
                        }
                        Toast.makeText(MainActivity.this,
                                existing == null ? "Đã lưu thiết bị." :
                                        "Đã cập nhật thiết bị đã lưu.",
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
        MaterialDialog.focusInput(dialog, nameInput);
    }

    private boolean persistSavedDevices(ArrayList<SavedDevice> updated) {
        try {
            if (!SavedDeviceStore.save(this, updated)) {
                Toast.makeText(this, "Không lưu được danh sách thiết bị.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (org.json.JSONException e) {
            Toast.makeText(this, "Không lưu được danh sách thiết bị.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        savedDevices = updated;
        renderSavedDevices();
        renderPeers();
        return true;
    }

    private void confirmRemoveSavedDevice(final SavedDevice device) {
        MaterialDialog.show(this, "Xóa thiết bị đã lưu?",
                "“" + device.name + "” sẽ bị xóa khỏi danh sách trên máy này.",
                null, "Hủy", null, "Xóa", new MaterialDialog.Action() {
                    public boolean onClick(Dialog dialog) {
                        ArrayList<SavedDevice> updated =
                                new ArrayList<SavedDevice>(savedDevices);
                        for (int i = 0; i < updated.size(); i++) {
                            if (updated.get(i).key().equals(device.key())) {
                                updated.remove(i);
                                break;
                            }
                        }
                        return persistSavedDevices(updated);
                    }
                });
    }

    private void beginTransferTo(Peer peer) {
        if (pendingText != null && pendingText.length() > 0) {
            startSendingText(peer);
        } else if (pendingUris.size() > 0) {
            startSendingSelectedFiles(peer);
        } else {
            Toast.makeText(this, "Hãy chọn tệp, ảnh, video hoặc văn bản trước.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleSourceChoice(int source) {
        if (source == 1) {
            beginPicker(false);
        } else if (source == 2) {
            beginPicker(true);
        } else if (source == 3) {
            beginPasteToQueue();
        } else if (source == 4) {
            beginTextComposer();
        }
    }

    private void showSourceMenu() {
        final Dialog[] dialogRef = new Dialog[1];
        final int[] sources = new int[] {1, 2, 3, 4};
        final int[] icons = new int[] {
                MaterialIconView.FILE, MaterialIconView.MEDIA,
                MaterialIconView.CLIPBOARD, MaterialIconView.EDIT
        };
        final String[] labels = new String[] {"Tệp", "Media", "Dán", "Nhập"};

        LinearLayout grid = column();
        int tileSize = sourceMenuTileSize();
        grid.addView(sourceChoiceRow(dialogRef, sources, icons, labels, 0, tileSize),
                fullWidth());
        LinearLayout.LayoutParams secondRowParams = fullWidth();
        secondRowParams.topMargin = dp(10);
        grid.addView(sourceChoiceRow(dialogRef, sources, icons, labels, 2, tileSize),
                secondRowParams);
        dialogRef[0] = MaterialDialog.show(this, "Thêm nội dung", null, grid,
                "Hủy", null, null, null);
    }

    private LinearLayout sourceChoiceRow(final Dialog[] dialogRef,
                                         int[] sources, int[] icons,
                                         String[] labels, int start, int tileSize) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int offset = 0; offset < 2; offset++) {
            final int source = sources[start + offset];
            View tile = sourceTile(icons[start + offset], labels[start + offset],
                    new View.OnClickListener() {
                        public void onClick(View view) {
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                            handleSourceChoice(source);
                        }
                    }, tileSize < dp(84));
            tile.setBackgroundDrawable(MaterialUi.stateBackground(this,
                    MaterialUi.SURFACE_HIGH, MaterialUi.SURFACE_CONTAINER,
                    MaterialUi.SURFACE_HIGH, 12));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                    0, tileSize, 1);
            tileParams.leftMargin = dp(4);
            tileParams.rightMargin = dp(4);
            row.addView(tile, tileParams);
        }
        return row;
    }

    private int sourceMenuTileSize() {
        int dialogWidth = Math.min(dp(500),
                getResources().getDisplayMetrics().widthPixels - dp(40));
        int contentWidth = dialogWidth - dp(44);
        return Math.max(dp(64), (contentWidth - dp(16)) / 2);
    }

    private void beginPicker(final boolean mediaOnly) {
        if (pendingText != null) {
            MaterialDialog.show(this, "Thay văn bản bằng nội dung mới?",
                    mediaOnly ?
                            "Tin nhắn đang chờ sẽ được thay nếu bạn chọn ảnh hoặc video." :
                            "Tin nhắn đang chờ sẽ được thay nếu bạn chọn tệp.",
                    null, "Hủy", null, "Tiếp tục", new MaterialDialog.Action() {
                        public boolean onClick(Dialog dialog) {
                            openPicker(mediaOnly);
                            return true;
                        }
                    });
            return;
        }
        openPicker(mediaOnly);
    }

    private void openPicker(boolean mediaOnly) {
        if (Build.VERSION.SDK_INT >= 18) {
            Intent picker = MultiSelectPickerApi18.createIntent("*/*");
            if (mediaOnly && Build.VERSION.SDK_INT >= 19) {
                MixedMediaPickerApi19.setImageAndVideoTypes(picker);
            }
            try {
                startActivityForResult(Intent.createChooser(picker,
                        mediaOnly ? "Chọn ảnh hoặc video" : "Chọn tệp"), REQUEST_FILE);
            } catch (Exception e) {
                Toast.makeText(this, "Thiết bị không có trình chọn phù hợp.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        Intent picker = findLegacyPickerIntent();
        if (picker == null) {
            Toast.makeText(this, "Không tìm thấy trình quản lý tệp phù hợp.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivityForResult(Intent.createChooser(picker,
                    mediaOnly ? "Chọn ảnh hoặc video" : "Chọn tệp"), REQUEST_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "Thiết bị không có trình quản lý nội dung phù hợp.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private Intent findLegacyPickerIntent() {
        Intent[] candidates = new Intent[] {
                createLegacyPickerIntent(Intent.ACTION_GET_CONTENT, true),
                createLegacyPickerIntent(Intent.ACTION_GET_CONTENT, false),
                createLegacyPickerIntent(Intent.ACTION_PICK, false)
        };
        PackageManager packageManager = getPackageManager();
        for (Intent candidate : candidates) {
            List<ResolveInfo> handlers = packageManager.queryIntentActivities(
                    candidate, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers != null && handlers.size() > 0) {
                return candidate;
            }
        }
        return null;
    }

    private Intent createLegacyPickerIntent(String action, boolean openable) {
        Intent picker = new Intent(action);
        picker.setType("*/*");
        if (openable) {
            picker.addCategory(Intent.CATEGORY_OPENABLE);
        }
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return picker;
    }

    private void appendSelectedUris(ArrayList<Uri> chosen) {
        if (chosen == null || chosen.size() == 0) {
            return;
        }
        ArrayList<Uri> additions = new ArrayList<Uri>();
        for (Uri uri : chosen) {
            if (uri == null || containsUri(pendingUris, uri) ||
                    containsUri(additions, uri)) {
                continue;
            }
            additions.add(uri);
        }
        if (additions.size() == 0) {
            return;
        }
        if (pendingUris.size() + additions.size() > 20) {
            Toast.makeText(this, "Có thể gửi tối đa 20 file mỗi lần.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        pendingUris.addAll(additions);
        pendingText = null;
        updateQueueUi();
    }

    private boolean containsUri(ArrayList<Uri> uris, Uri candidate) {
        for (Uri uri : uris) {
            if (uri.toString().equals(candidate.toString())) {
                return true;
            }
        }
        return false;
    }

    private void updateQueueUi() {
        if (sourceRow == null || queueCard == null || queueItems == null) {
            return;
        }
        queueItems.removeAllViews();
        boolean hasText = pendingText != null;
        int count = hasText ? 1 : pendingUris.size();
        sourceRow.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        queueCard.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        if (count == 0) {
            return;
        }
        queueSummary.setText(hasText ? "1 tin nhắn · không gộp chung với tập tin" :
                count + " tập tin đã chọn");
        if (hasText) {
            addQueueRow("Tin nhắn văn bản", pendingText, -1);
        } else {
            for (int i = 0; i < pendingUris.size(); i++) {
                addQueueRow(FileSender.displayName(this, pendingUris.get(i)),
                        null, i);
            }
        }
    }

    private void addQueueRow(String label, String preview, final int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = fullWidth();
        rowParams.topMargin = dp(5);
        queueItems.addView(row, rowParams);

        LinearLayout details = column();
        row.addView(details, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        details.addView(text(label, 14, MaterialUi.ON_SURFACE, true));
        if (preview != null) {
            String snippet = preview.replace('\n', ' ').replace('\r', ' ').trim();
            if (snippet.length() > 90) {
                snippet = snippet.substring(0, 87) + "…";
            }
            TextView previewView = text(snippet, 12,
                    MaterialUi.ON_SURFACE_VARIANT, false);
            previewView.setLines(2);
            details.addView(previewView, fullWidth());
        }

        Button remove = button("Gỡ");
        remove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        remove.setTextColor(MaterialUi.PRIMARY);
        remove.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        remove.setPadding(dp(12), dp(8), dp(8), dp(8));
        remove.setBackgroundDrawable(MaterialUi.textButtonBackground(this));
        remove.setContentDescription("Gỡ " + label + " khỏi hàng chờ");
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        removeParams.leftMargin = dp(6);
        row.addView(remove, removeParams);
        remove.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (index < 0) {
                    pendingText = null;
                } else if (index < pendingUris.size()) {
                    pendingUris.remove(index);
                }
                updateQueueUi();
            }
        });
    }

    private void beginTextComposer() {
        if (pendingUris.size() > 0) {
            MaterialDialog.show(this, "Thay tập tin bằng văn bản?",
                    "Các tập tin đang chờ chỉ bị thay khi bạn thêm văn bản.",
                    null, "Hủy", null, "Tiếp tục", new MaterialDialog.Action() {
                        public boolean onClick(Dialog dialog) {
                            showTextComposer();
                            return true;
                        }
                    });
            return;
        }
        showTextComposer();
    }

    private void showTextComposer() {
        String initialText = pendingText;
        final EditText input = new EditText(this);
        input.setText(initialText == null ? "" : initialText);
        input.setHint("Nhập hoặc dán văn bản tại đây");
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setMinLines(5);
        input.setMaxLines(10);
        input.setMinHeight(dp(150));
        MaterialUi.styleEditText(this, input);

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(0, 0, 0, 0);
        holder.addView(input, fullWidth());
        Dialog dialog = MaterialDialog.show(this,
                "Nhập văn bản", null, holder,
                "Hủy", null, "Thêm vào hàng chờ",
                new MaterialDialog.Action() {
                    public boolean onClick(Dialog dialog) {
                        String value = input.getText().toString();
                        if (value.length() == 0) {
                            Toast.makeText(MainActivity.this,
                                    "Văn bản đang trống.", Toast.LENGTH_LONG).show();
                            return false;
                        }
                        if (!isTextWithinLimit(value)) {
                            Toast.makeText(MainActivity.this,
                                    "Văn bản không được vượt quá 256 KB.",
                                    Toast.LENGTH_LONG).show();
                            return false;
                        }
                        queueText(value);
                        return true;
                    }
                });
        MaterialDialog.focusInput(dialog, input);
    }

    private void beginPasteToQueue() {
        final String value = ClipboardCompat.readText(this);
        if (value == null || value.length() == 0) {
            Toast.makeText(this, "Clipboard không có văn bản.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!isTextWithinLimit(value)) {
            Toast.makeText(this, "Văn bản không được vượt quá 256 KB.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (pendingUris.size() > 0) {
            MaterialDialog.show(this, "Thay tập tin bằng văn bản?",
                    "Các tập tin đang chờ chỉ bị thay khi bạn dán văn bản.",
                    null, "Hủy", null, "Tiếp tục", new MaterialDialog.Action() {
                        public boolean onClick(Dialog dialog) {
                            queueText(value);
                            return true;
                        }
                    });
            return;
        }
        queueText(value);
    }

    private void queueText(String value) {
        pendingUris.clear();
        pendingText = value;
        updateQueueUi();
    }

    private boolean isTextWithinLimit(String value) {
        try {
            return value.getBytes("UTF-8").length <= 262144;
        } catch (Exception e) {
            return false;
        }
    }

    private String deviceName() {
        String model = Build.MODEL;
        return model == null || model.length() == 0 ? "Android" : model;
    }

    private void showManualAddressDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Ví dụ: 192.168.1.20 hoặc 192.168.1.20:45321");
        input.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI);
        MaterialUi.styleEditText(this, input);
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(input, fullWidth());

        Dialog dialog = MaterialDialog.show(this, "Địa chỉ máy nhận",
                "Nhập IP trên màn hình của thiết bị kia.", holder,
                "Hủy", null, "Tiếp tục", new MaterialDialog.Action() {
                    public boolean onClick(Dialog dialog) {
                        Peer peer = parseManualPeer(input.getText().toString());
                        if (peer == null) {
                            Toast.makeText(MainActivity.this,
                                    "Địa chỉ chưa hợp lệ.", Toast.LENGTH_LONG).show();
                            return false;
                        }
                        beginTransferTo(peer);
                        return true;
                    }
                });
        MaterialDialog.focusInput(dialog, input);
    }

    private Peer parseManualPeer(String value) {
        if (value == null) {
            return null;
        }
        String input = value.trim();
        if (input.startsWith("http://")) {
            input = input.substring("http://".length());
        }
        int port = LanShareServer.HTTP_PORT;
        int colon = input.lastIndexOf(':');
        if (colon > 0 && input.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(input.substring(colon + 1));
                input = input.substring(0, colon);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (!isIPv4Address(input) || port < 1 || port > 65535) {
            return null;
        }
        return new Peer("Thiết bị", input, port);
    }

    private boolean isIPv4Address(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() == 0 || parts[i].length() > 3) {
                return false;
            }
            for (int j = 0; j < parts[i].length(); j++) {
                if (parts[i].charAt(j) < '0' || parts[i].charAt(j) > '9') {
                    return false;
                }
            }
            try {
                int octet = Integer.parseInt(parts[i]);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private void showTransferProgress(String transferId, String title,
                                     ArrayList<IncomingOffer.Item> items) {
        final String id = transferId;
        TransferProgressScreen screen = new TransferProgressScreen(
                this, title, items, new TransferProgressScreen.Listener() {
            public void onClose() {
                progressScreens.remove(id);
                showHomeScreen();
                showNextIncomingOffer();
            }
        });
        screen.setStatus("Đã chấp thuận · đang truyền…");
        progressScreens.put(transferId, screen);
        activeProgressScreen = screen;
        activeTransferId = transferId;
        showingTextMessage = false;
        setContentView(screen);
    }

    private void showTextMessage(String senderName, final String message) {
        showingTextMessage = true;
        TextMessageScreen screen = new TextMessageScreen(
                this, senderName, message, new TextMessageScreen.Listener() {
            public void onSaveText(String text) {
                if (saveTextToDownloads(text)) {
                    closeTextMessage();
                }
            }

            public void onCopyText(String text) {
                ClipboardCompat.copyText(MainActivity.this, text);
                Toast.makeText(MainActivity.this, "Đã sao chép văn bản.",
                        Toast.LENGTH_SHORT).show();
                closeTextMessage();
            }
        });
        setContentView(screen);
    }

    private boolean saveTextToDownloads(String text) {
        File directory;
        try {
            directory = DownloadFolders.ensureReceivedDirectory();
        } catch (Exception e) {
            Toast.makeText(this, "Không tạo được thư mục Download/Near Transfer/.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        File destination = null;
        FileOutputStream output = null;
        try {
            destination = ShareFiles.uniqueFile(directory, "Tin nhắn.txt");
            output = new FileOutputStream(destination);
            output.write(text.getBytes("UTF-8"));
            output.flush();
            output.close();
            output = null;
            Toast.makeText(this, "Đã lưu tại Download/Near Transfer/" +
                    destination.getName(), Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            if (destination != null) {
                destination.delete();
            }
            Toast.makeText(this, "Không lưu được văn bản.",
                    Toast.LENGTH_LONG).show();
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void closeTextMessage() {
        String completedTransferId = activeTransferId;
        showingTextMessage = false;
        showHomeScreen();
        if (completedTransferId != null) {
            progressScreens.remove(completedTransferId);
        }
        activeProgressScreen = null;
        activeTransferId = null;
        showNextIncomingOffer();
    }

    private void showHomeScreen() {
        if (homeScreen != null) {
            setContentView(homeScreen);
        }
        showingTextMessage = false;
        activeProgressScreen = null;
        activeTransferId = null;
    }

    @Override
    public void onBackPressed() {
        if (showingTextMessage) {
            closeTextMessage();
            return;
        }
        if (activeProgressScreen != null && !activeProgressScreen.isFinished()) {
            return;
        }
        if (activeProgressScreen != null) {
            if (activeTransferId != null) {
                progressScreens.remove(activeTransferId);
            }
            showHomeScreen();
            showNextIncomingOffer();
            return;
        }
        super.onBackPressed();
    }

    private void setStatus(String message) {
        if (statusText != null) {
            if (message == null || message.length() == 0) {
                statusText.setVisibility(View.GONE);
            } else {
                statusText.setText(message);
                statusText.setVisibility(View.VISIBLE);
            }
        }
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        MaterialUi.stylePrimaryButton(this, button);
        return button;
    }

    private GradientDrawable cardBackground(int color) {
        return MaterialUi.container(this, color, 12);
    }

    private GradientDrawable darkCardBackground() {
        return MaterialUi.container(this, MaterialUi.SURFACE_CONTAINER, 12);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class HandlerBridge {
        void post(Activity activity, Runnable runnable) {
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(runnable);
            }
        }
    }

    private static final class IncomingOfferRequest {
        final IncomingOffer offer;
        final LanShareServer.DecisionCallback decision;

        IncomingOfferRequest(IncomingOffer offer,
                             LanShareServer.DecisionCallback decision) {
            this.offer = offer;
            this.decision = decision;
        }
    }
}