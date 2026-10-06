package com.nearbyshare.j2me;

import java.io.IOException;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

/** Near Transfer MIDlet with NWS1 Wi-Fi discovery and file/text transfer. */
public final class NearTransferMidlet extends MIDlet
        implements CommandListener {
    static final int INPUT_ADDRESS = 1;
    static final int INPUT_MESSAGE = 2;

    private final Command inputDoneCommand = new Command("Xong", Command.OK, 1);
    private final Command inputCancelCommand =
            new Command("Hủy", Command.CANCEL, 2);

    private Display display;
    private NearTransferCanvas canvas;
    private TextBox inputBox;
    private int inputKind;
    private LanPeerDiscovery discovery;
    private LanTransferServer server;
    private volatile String serverLocalAddress;
    private LanTransferClient outgoingTransfer;
    private IncomingOffer currentOffer;

    protected void startApp() {
        display = Display.getDisplay(this);
        Jsr75FileStorage.loadReceiveDirectory();
        if (canvas == null) {
            canvas = new NearTransferCanvas(this);
        }
        display.setCurrent(canvas);
        startNetworkServices();
    }

    protected void pauseApp() {
        // Keep the listener alive so peers can still send an approved offer.
    }

    protected void destroyApp(boolean unconditional) {
        IncomingOffer offer = currentOffer;
        currentOffer = null;
        if (offer != null) {
            offer.decide(false);
        }
        if (discovery != null) {
            discovery.stopDiscovery();
            discovery = null;
        }
        if (server != null) {
            server.stopServer();
            server = null;
        }
        cancelTransfer();
    }

    void requestAddressInput(String initialValue) {
        showInput(INPUT_ADDRESS, "Nhập địa chỉ thiết bị", initialValue,
                TextField.ANY, 80);
    }

    void requestMessageInput(String initialValue) {
        showInput(INPUT_MESSAGE, "Tin nhắn", initialValue, TextField.ANY,
                4096);
    }

    void refreshPeers() {
        if (discovery != null) {
            discovery.scanNow();
        }
    }

    void startTransfer(PeerDevice peer, String[] fileNames,
                       String[] filePaths, String text) {
        if (outgoingTransfer != null) {
            outgoingTransfer.cancel();
        }
        outgoingTransfer = new LanTransferClient(this, peer, deviceName(),
                fileNames, filePaths, text);
        outgoingTransfer.start();
    }

    void cancelTransfer() {
        LanTransferClient transfer = outgoingTransfer;
        outgoingTransfer = null;
        if (transfer != null) {
            transfer.cancel();
        }
    }

    void resolveIncomingOffer(IncomingOffer offer, boolean accepted) {
        offer.decide(accepted);
        if (!accepted) {
            synchronized (this) {
                if (currentOffer == offer) {
                    currentOffer = null;
                }
            }
        }
    }

    void onIncomingOfferExpired(final IncomingOffer offer) {
        synchronized (this) {
            if (currentOffer == offer) {
                currentOffer = null;
            }
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.expireIncomingOffer(offer);
                }
            }
        });
    }

    void onIncomingTransferFailed(final IncomingOffer offer,
                                  final String message) {
        synchronized (this) {
            if (currentOffer == offer) {
                currentOffer = null;
            }
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.failIncomingTransfer(offer, message);
                }
            }
        });
    }

    boolean offerIncomingTransfer(final IncomingOffer offer) {
        synchronized (this) {
            if (currentOffer != null) {
                return false;
            }
            currentOffer = offer;
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.showIncomingOffer(offer);
                } else {
                    resolveIncomingOffer(offer, false);
                }
            }
        });
        return true;
    }

    void onServerStarted(final String address, final int port) {
        serverLocalAddress = address;
        LanPeerDiscovery current = discovery;
        if (current != null) {
            current.updateLocalAddress(address);
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.setServerStatus(address, port);
                }
            }
        });
    }

    void onLocalAddress(final String address) {
        LanPeerDiscovery current = discovery;
        if (current != null) {
            current.updateLocalAddress(address);
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.setLocalAddress(address);
                }
            }
        });
    }

    void onPeersChanged(final PeerDevice[] peers) {
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.setNearbyPeers(peers);
                }
            }
        });
    }

    void onNetworkError(final String message) {
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.setNetworkStatus(message);
                }
            }
        });
    }

    void onTransferProgress(final IncomingOffer offer, final int itemIndex,
                            final long transferred, final long total) {
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.updateIncomingProgress(offer, itemIndex,
                            transferred, total);
                }
            }
        });
    }

    void onFileReceived(final IncomingOffer offer, final int index,
                        final String savedName) {
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.setLastReceivedName(savedName);
                }
            }
        });
    }

    void onIncomingFilesCompleted(final IncomingOffer offer) {
        synchronized (this) {
            if (currentOffer == offer) {
                currentOffer = null;
            }
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.finishIncomingFiles(offer);
                }
            }
        });
    }

    void onTextReceived(final IncomingOffer offer, final String text) {
        synchronized (this) {
            if (currentOffer == offer) {
                currentOffer = null;
            }
        }
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.showReceivedText(offer, text);
                }
            }
        });
    }

    void onOutgoingProgress(final int itemIndex, final String itemName,
                            final long transferred, final long itemTotal,
                            final long totalTransferred,
                            final long transferTotal) {
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.updateOutgoingProgress(itemIndex, itemName,
                            transferred, itemTotal, totalTransferred,
                            transferTotal);
                }
            }
        });
    }

    void onOutgoingCompleted() {
        outgoingTransfer = null;
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.finishOutgoingTransfer(true, "");
                }
            }
        });
    }

    void onOutgoingFailed(final String message) {
        outgoingTransfer = null;
        runOnUi(new Runnable() {
            public void run() {
                if (canvas != null) {
                    canvas.finishOutgoingTransfer(false, message);
                }
            }
        });
    }

    void saveReceivedText(final String text) {
        Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                    final String path = Jsr75FileStorage.saveText(text);
                    runOnUi(new Runnable() {
                        public void run() {
                            if (canvas != null) {
                                canvas.finishSavingReceivedText(path);
                            }
                        }
                    });
                } catch (final Exception exception) {
                    runOnUi(new Runnable() {
                        public void run() {
                            if (canvas != null) {
                                canvas.failSavingReceivedText(
                                        exception.getMessage());
                            }
                        }
                    });
                }
            }
        });
        worker.start();
    }

    public void commandAction(Command command, Displayable displayable) {
        if (displayable == inputBox) {
            if (command == inputDoneCommand) {
                String value = inputBox.getString();
                display.setCurrent(canvas);
                canvas.acceptInput(inputKind, value);
            } else if (command == inputCancelCommand) {
                display.setCurrent(canvas);
            }
        }
    }

    private void startNetworkServices() {
        if (server == null) {
            server = new LanTransferServer(this);
            server.start();
        }
        if (discovery == null) {
            discovery = new LanPeerDiscovery(this, deviceName());
            discovery.updateLocalAddress(serverLocalAddress);
            discovery.start();
        }
    }

    private String deviceName() {
        String platform = System.getProperty("microedition.platform");
        if (platform == null || platform.length() == 0) {
            return "Near Transfer";
        }
        return "Near Transfer " + platform;
    }

    private void runOnUi(Runnable runnable) {
        Display current = display;
        if (current == null) {
            return;
        }
        try {
            current.callSerially(runnable);
        } catch (IllegalStateException ignored) {
        }
    }

    private void showInput(int kind, String title, String initialValue,
                           int constraints, int maxSize) {
        inputKind = kind;
        inputBox = new TextBox(title, initialValue, maxSize, constraints);
        inputBox.addCommand(inputDoneCommand);
        inputBox.addCommand(inputCancelCommand);
        inputBox.setCommandListener(this);
        display.setCurrent(inputBox);
    }
}
