package com.nearbyshare.j2me;

import javax.microedition.lcdui.Graphics;

/** Home screen: Android-derived source tiles, queue, peers and saved devices. */
final class HomeUi {
    private HomeUi() {
    }

    static int draw(NearTransferCanvas canvas, Graphics graphics, int width,
                    int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int containerWidth = width - pad * 2;
        int columnGap = canvas.px(8, scale);
        int rowGap = canvas.px(8, scale);
        int tileWidth = (containerWidth - columnGap) / 2;
        int tileHeight = canvas.px(48, scale);
        int sourceTop = contentTop + canvas.px(16, scale);
        int i;
        boolean queueVisible =
                canvas.getQueueCount() > 0 || canvas.isQueueText();
        int y;

        if (queueVisible) {
            y = drawQueue(canvas, graphics, width, pad, sourceTop, scale);
            y += canvas.px(8, scale);
        } else {
            for (i = 0; i < 4; i++) {
                int column = i % 2;
                int row = i / 2;
                int x = pad + column * (tileWidth + columnGap);
                int tileY = sourceTop + row * (tileHeight + rowGap);
                int action = sourceAction(i);
                boolean focused = canvas.isFocusedAction(action);
                UiKit.fillCard(graphics, x, tileY, tileWidth, tileHeight,
                        focused ? UiKit.PRIMARY : UiKit.SURFACE_CONTAINER,
                        scale);
                UiKit.drawCentered(graphics, sourceLabel(i),
                        x + canvas.px(4, scale), tileY,
                        tileWidth - canvas.px(8, scale), tileHeight,
                        focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE,
                        UiKit.FONT_BODY, scale);
                canvas.addHit(x, tileY, tileWidth, tileHeight, action);
            }
            y = sourceTop + tileHeight * 2 + rowGap + canvas.px(18, scale);
        }

        y = drawReceiveLocation(canvas, graphics, width, pad, y, scale);

        int headingY = y;
        UiKit.drawText(graphics, "Thiết bị quanh đây", pad, headingY,
                width - pad * 2 - canvas.px(76, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        int refreshWidth = canvas.px(72, scale);
        int refreshHeight = canvas.px(36, scale);
        int refreshX = width - pad - refreshWidth;
        int refreshY = headingY +
                (UiKit.FONT_BOLD.getHeight() - refreshHeight) / 2;
        UiKit.drawCentered(graphics, "Refresh", refreshX, refreshY,
                refreshWidth, refreshHeight, UiKit.PRIMARY,
                UiKit.FONT_BODY, scale);
        if (canvas.isFocusedAction(NearTransferCanvas.ACTION_REFRESH)) {
            int labelWidth = UiKit.FONT_BODY.stringWidth("Refresh");
            int labelX = refreshX + (refreshWidth - labelWidth) / 2;
            int underlineY = refreshY +
                    (refreshHeight + UiKit.FONT_BODY.getHeight()) / 2;
            graphics.setColor(UiKit.PRIMARY_PRESSED);
            graphics.drawLine(labelX, underlineY,
                    labelX + labelWidth - 1, underlineY);
        }
        canvas.addHit(refreshX, refreshY, refreshWidth, refreshHeight,
                NearTransferCanvas.ACTION_REFRESH);
        y += canvas.px(34, scale);

        int peerCount = canvas.getNearbyPeerCount();
        if (peerCount == 0) {
            y = drawEmptyPeers(canvas, graphics, width, pad, y, scale);
        } else {
            for (i = 0; i < peerCount; i++) {
                PeerDevice peer = canvas.getNearbyPeer(i);
                if (i > 0) {
                    y += canvas.px(8, scale);
                }
                y = drawPeer(canvas, graphics, width, pad, y, scale,
                        i, peer.name,
                        peer.address + ":" + peer.port);
            }
        }

        canvas.drawButton(graphics, "Nhập địa chỉ thủ công",
                pad, y + canvas.px(10, scale), width - pad * 2,
                canvas.px(48, scale), NearTransferCanvas.ACTION_MANUAL,
                false, scale);
        y += canvas.px(70, scale);

        UiKit.drawText(graphics, "Đã lưu", pad, y,
                width - pad * 2, UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        y += canvas.px(34, scale);

        int saved = canvas.getSavedCount();
        if (saved == 0) {
            UiKit.fillCard(graphics, pad, y, width - pad * 2,
                    canvas.px(60, scale), UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawCentered(graphics, "Chưa có thiết bị đã lưu.",
                    pad + canvas.px(14, scale), y,
                    width - pad * 2 - canvas.px(28, scale),
                    canvas.px(60, scale),
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY, scale);
            y += canvas.px(68, scale);
        } else {
            for (i = 0; i < saved; i++) {
                if (i > 0) {
                    y += canvas.px(8, scale);
                }
                y = drawSavedRow(canvas, graphics, width, pad, y, scale,
                        i, canvas.getSavedDeviceName(i),
                        canvas.getSavedDeviceEndpoint(i));
            }
        }

        return y + canvas.px(16, scale);
    }

    static int fixedFooterHeight(NearTransferCanvas canvas, int scale) {
        return canvas.px(36, scale);
    }

    static void drawFixedFooter(NearTransferCanvas canvas, Graphics graphics,
                                int width, int height, int scale) {
        int footerHeight = fixedFooterHeight(canvas, scale);
        int footerY = height - footerHeight;
        String address = canvas.getLocalAddress();
        String label = "Đang dò IP...";
        if (address != null && address.length() > 0 &&
                !"0.0.0.0".equals(address) &&
                !address.startsWith("127.")) {
            label = address;
        }

        graphics.setColor(UiKit.SURFACE);
        graphics.fillRect(0, footerY, width, footerHeight);
        graphics.setColor(UiKit.SURFACE_CONTAINER);
        graphics.drawLine(0, footerY, width - 1, footerY);
        UiKit.drawCentered(graphics, label, canvas.px(8, scale),
                footerY + 1, width - canvas.px(16, scale),
                footerHeight - 2, UiKit.ON_SURFACE_VARIANT,
                UiKit.FONT_SMALL, scale);
    }

    private static int drawReceiveLocation(NearTransferCanvas canvas,
                                           Graphics graphics, int width,
                                           int pad, int y, int scale) {
        int cardWidth = width - pad * 2;
        int cardHeight = canvas.px(82, scale);
        int buttonWidth = canvas.px(68, scale);
        int buttonHeight = canvas.px(40, scale);
        int buttonInset = canvas.px(12, scale);
        int buttonX = width - pad - buttonWidth - buttonInset;
        int buttonY = y + (cardHeight - buttonHeight) / 2;
        int textWidth = cardWidth - buttonWidth - buttonInset -
                canvas.px(32, scale);
        int textGap = canvas.px(4, scale);
        int textHeight = UiKit.FONT_BOLD.getHeight() + textGap +
                UiKit.FONT_SMALL.getHeight();
        int textY = buttonY + (buttonHeight - textHeight) / 2;

        UiKit.fillCard(graphics, pad, y, cardWidth, cardHeight,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawText(graphics, "Vị trí nhận",
                pad + canvas.px(12, scale), textY,
                textWidth,
                UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        UiKit.drawWrapped(graphics, canvas.getReceiveDirectoryLabel(),
                pad + canvas.px(12, scale),
                textY + UiKit.FONT_BOLD.getHeight() + textGap,
                textWidth,
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL, 2);
        canvas.drawButton(graphics, "Duyệt", buttonX, buttonY,
                buttonWidth, buttonHeight,
                NearTransferCanvas.ACTION_BROWSE_RECEIVE,
                false, scale);
        return y + cardHeight + canvas.px(10, scale);
    }

    private static int drawQueue(NearTransferCanvas canvas, Graphics graphics,
                                 int width, int pad, int y, int scale) {
        int cardWidth = width - pad * 2;
        int itemCount = canvas.isQueueText() ? 1 : canvas.getQueueCount();
        int rowSpacing = canvas.px(42, scale);
        int cardHeight = canvas.px(66 + itemCount * 42, scale);
        UiKit.fillCard(graphics, pad, y, cardWidth, cardHeight,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawText(graphics, "Đã chọn", pad + canvas.px(14, scale),
                y + canvas.px(12, scale), cardWidth - canvas.px(100, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        canvas.drawButton(graphics, "+ Thêm",
                width - pad - canvas.px(78, scale), y + canvas.px(7, scale),
                canvas.px(66, scale), canvas.px(38, scale),
                NearTransferCanvas.ACTION_ADD, false, scale);

        String summary = canvas.isQueueText() ?
                "1 tin nhắn · không gộp chung với tập tin" :
                itemCount + " tập tin đã chọn";
        UiKit.drawText(graphics, summary, pad + canvas.px(14, scale),
                y + canvas.px(40, scale), cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);

        int i;
        int rowY = y + canvas.px(68, scale);
        if (canvas.isQueueText()) {
            String preview = canvas.getTextDraft();
            if (preview.length() > 64) {
                preview = preview.substring(0, 61) + "...";
            }
            UiKit.drawText(graphics, "Tin nhắn: " + preview,
                    pad + canvas.px(14, scale), rowY,
                    cardWidth - canvas.px(86, scale),
                    UiKit.ON_SURFACE, UiKit.FONT_SMALL);
            canvas.drawButton(graphics, "Gỡ",
                    width - pad - canvas.px(58, scale), rowY - canvas.px(7, scale),
                    canvas.px(46, scale), canvas.px(34, scale),
                    NearTransferCanvas.ACTION_REMOVE_QUEUE, false, scale);
        } else {
            for (i = 0; i < itemCount; i++) {
                UiKit.drawText(graphics, canvas.getQueueFile(i),
                        pad + canvas.px(14, scale), rowY,
                        cardWidth - canvas.px(86, scale),
                        UiKit.ON_SURFACE, UiKit.FONT_SMALL);
                canvas.drawButton(graphics, "Gỡ",
                        width - pad - canvas.px(58, scale), rowY - canvas.px(7, scale),
                        canvas.px(46, scale), canvas.px(34, scale),
                        NearTransferCanvas.ACTION_REMOVE_QUEUE, false, scale);
                rowY += rowSpacing;
            }
        }

        return y + cardHeight;
    }

    private static int drawNoWifi(NearTransferCanvas canvas, Graphics graphics,
                                  int width, int pad, int y, int scale) {
        int height = canvas.px(56, scale);
        UiKit.fillCard(graphics, pad, y, width - pad * 2, height,
                UiKit.ERROR_CONTAINER, scale);
        UiKit.drawText(graphics, "Không có Wi-Fi", pad + canvas.px(14, scale),
                y + canvas.px(11, scale), width - pad * 2 - canvas.px(28, scale),
                UiKit.ERROR, UiKit.FONT_BOLD);
        return y + height;
    }

    private static int drawEmptyPeers(NearTransferCanvas canvas, Graphics graphics,
                                      int width, int pad, int y, int scale) {
        int height = canvas.px(60, scale);
        UiKit.fillCard(graphics, pad, y, width - pad * 2, height,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawCentered(graphics, "Chưa thấy thiết bị quanh đây.",
                pad + canvas.px(12, scale), y,
                width - pad * 2 - canvas.px(24, scale), height,
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY, scale);
        return y + height;
    }

    private static int drawPeer(NearTransferCanvas canvas, Graphics graphics,
                                int width, int pad, int y, int scale,
                                int peerIndex, String name, String endpoint) {
        int rowWidth = width - pad * 2;
        int rowHeight = canvas.px(70, scale);
        int sendWidth = canvas.px(58, scale);
        int saveWidth = canvas.px(42, scale);
        boolean touchMode = canvas.isTouchMode();
        int rowAction = touchMode ?
                NearTransferCanvas.ACTION_SEND_NEAR_BASE + peerIndex :
                NearTransferCanvas.ACTION_OPEN_NEAR_PEER_BASE + peerIndex;
        boolean focused = canvas.isFocusedAction(rowAction);

        UiKit.fillCard(graphics, pad, y, rowWidth, rowHeight,
                focused ? UiKit.PRIMARY : UiKit.SURFACE_CONTAINER, scale);
        canvas.addHit(pad, y, rowWidth, rowHeight, rowAction);

        int rightInset = touchMode ? canvas.px(10, scale) : 0;
        int controlGap = canvas.px(8, scale);
        int controlsWidth = touchMode ?
                sendWidth + saveWidth + controlGap + rightInset : 0;
        int textWidth = rowWidth - controlsWidth - canvas.px(24, scale);
        UiKit.drawText(graphics, name, pad + canvas.px(12, scale),
                y + canvas.px(14, scale), textWidth,
                focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE,
                UiKit.FONT_BOLD);
        UiKit.drawText(graphics, endpoint, pad + canvas.px(12, scale),
                y + canvas.px(39, scale), textWidth,
                focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE_VARIANT,
                UiKit.FONT_SMALL);

        if (touchMode) {
            int sendX = width - pad - rightInset - sendWidth;
            int saveX = sendX - saveWidth - controlGap;
            canvas.drawButton(graphics, "Lưu", saveX, y + canvas.px(13, scale),
                    saveWidth, canvas.px(44, scale),
                    NearTransferCanvas.ACTION_SAVE_PEER_BASE + peerIndex,
                    false, scale);
            canvas.drawButton(graphics, "Gửi",
                    sendX, y + canvas.px(13, scale),
                    sendWidth, canvas.px(44, scale),
                    NearTransferCanvas.ACTION_SEND_NEAR_BASE + peerIndex,
                    true, scale);
        }
        return y + rowHeight;
    }

    private static int drawSavedRow(NearTransferCanvas canvas, Graphics graphics,
                                    int width, int pad, int y, int scale,
                                    int savedIndex, String name,
                                    String endpoint) {
        int rowWidth = width - pad * 2;
        int rowHeight = canvas.px(70, scale);
        int sendWidth = canvas.px(58, scale);
        int removeWidth = canvas.px(42, scale);
        boolean touchMode = canvas.isTouchMode();
        int rowAction = touchMode ?
                NearTransferCanvas.ACTION_SEND_SAVED_BASE + savedIndex :
                NearTransferCanvas.ACTION_OPEN_SAVED_PEER_BASE + savedIndex;
        boolean focused = canvas.isFocusedAction(rowAction);
        UiKit.fillCard(graphics, pad, y, rowWidth, rowHeight,
                focused ? UiKit.PRIMARY : UiKit.SURFACE_CONTAINER, scale);
        canvas.addHit(pad, y, rowWidth, rowHeight, rowAction);

        int rightInset = touchMode ? canvas.px(10, scale) : 0;
        int controlGap = canvas.px(8, scale);
        int controlsWidth = touchMode ?
                sendWidth + removeWidth + controlGap + rightInset : 0;
        int textWidth = rowWidth - controlsWidth - canvas.px(24, scale);
        UiKit.drawText(graphics, name, pad + canvas.px(12, scale),
                y + canvas.px(14, scale), textWidth,
                focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE,
                UiKit.FONT_BOLD);
        UiKit.drawText(graphics, endpoint, pad + canvas.px(12, scale),
                y + canvas.px(39, scale), textWidth,
                focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE_VARIANT,
                UiKit.FONT_SMALL);

        if (touchMode) {
            int sendX = width - pad - rightInset - sendWidth;
            int removeX = sendX - removeWidth - controlGap;
            canvas.drawButton(graphics, "Xóa",
                    removeX, y + canvas.px(13, scale),
                    removeWidth, canvas.px(44, scale),
                    NearTransferCanvas.ACTION_REMOVE_SAVED_BASE + savedIndex,
                    false, scale);
            canvas.drawButton(graphics, "Gửi",
                    sendX, y + canvas.px(13, scale),
                    sendWidth, canvas.px(44, scale),
                    NearTransferCanvas.ACTION_SEND_SAVED_BASE + savedIndex,
                    true, scale);
        }
        return y + rowHeight;
    }

    private static int sourceAction(int index) {
        if (index == 0) {
            return NearTransferCanvas.ACTION_FILES;
        }
        if (index == 1) {
            return NearTransferCanvas.ACTION_MEDIA;
        }
        if (index == 2) {
            return NearTransferCanvas.ACTION_PASTE;
        }
        return NearTransferCanvas.ACTION_TEXT;
    }

    private static String sourceLabel(int index) {
        if (index == 0) {
            return "Tệp";
        }
        if (index == 1) {
            return "Media";
        }
        if (index == 2) {
            return "Dán";
        }
        return "Nhập";
    }

}