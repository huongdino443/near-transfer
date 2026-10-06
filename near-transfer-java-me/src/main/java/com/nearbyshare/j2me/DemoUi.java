package com.nearbyshare.j2me;

import javax.microedition.lcdui.Graphics;

/** File selection, message entry, saved peers, and queue replacement UI. */
final class SelectionUi {
    private SelectionUi() {
    }

    static int draw(NearTransferCanvas canvas, Graphics graphics, int width,
                    int contentTop, int scale) {
        if (canvas.getScreen() == NearTransferCanvas.FILES) {
            return drawFiles(canvas, graphics, width, contentTop, scale);
        }
        if (canvas.getScreen() == NearTransferCanvas.TEXT_COMPOSER) {
            return drawTextComposer(canvas, graphics, width, contentTop, scale);
        }
        if (canvas.getScreen() == NearTransferCanvas.CONFIRM_REPLACE) {
            return drawReplaceConfirmation(canvas, graphics, width,
                    contentTop, scale);
        }
        return drawSavedDevices(canvas, graphics, width, contentTop, scale);
    }

    private static int drawFiles(NearTransferCanvas canvas, Graphics graphics,
                                 int width, int contentTop, int scale) {
        return drawRealFileBrowser(canvas, graphics, width, contentTop, scale);
    }

    private static int drawRealFileBrowser(NearTransferCanvas canvas,
                                           Graphics graphics, int width,
                                           int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(8, scale);

        if (canvas.isFileBrowserLoading()) {
            int cardHeight = canvas.px(82, scale);
            UiKit.fillCard(graphics, pad, y, cardWidth, cardHeight,
                    UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawText(graphics, "Đang đọc bộ nhớ…",
                    pad + canvas.px(14, scale),
                    y + canvas.px(28, scale), cardWidth -
                            canvas.px(28, scale),
                    UiKit.ON_SURFACE, UiKit.FONT_BOLD);
            y += cardHeight + canvas.px(14, scale);
            if (canvas.isReceiveLocationMode()) {
                y = drawReceiveLocationActions(canvas, graphics, pad,
                        cardWidth, y, scale);
            }
            return y;
        }

        if (canvas.getFileBrowserState() != FileBrowserListing.READY) {
            int cardHeight = canvas.px(108, scale);
            UiKit.fillCard(graphics, pad, y, cardWidth, cardHeight,
                    UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawWrapped(graphics, canvas.getFileBrowserMessage(),
                    pad + canvas.px(14, scale), y + canvas.px(16, scale),
                    cardWidth - canvas.px(28, scale),
                    UiKit.ON_SURFACE, UiKit.FONT_BODY, 3);
            y += cardHeight + canvas.px(10, scale);
            if (canvas.getFileBrowserState() == FileBrowserListing.ERROR) {
                canvas.drawButton(graphics, "Thử lại", pad, y, cardWidth,
                        canvas.px(44, scale),
                        NearTransferCanvas.ACTION_BROWSER_RETRY,
                        true, scale);
                y += canvas.px(54, scale);
            }
            if (canvas.isReceiveLocationMode()) {
                y = drawReceiveLocationActions(canvas, graphics, pad,
                        cardWidth, y, scale);
            }
            return y + canvas.px(8, scale);
        }

        String path = canvas.getFileBrowserPath();
        if (path == null) {
            UiKit.drawText(graphics, "BỘ NHỚ",
                    pad, y, cardWidth, UiKit.ON_SURFACE_VARIANT,
                    UiKit.FONT_SMALL);
        } else {
            UiKit.drawText(graphics, path, pad, y, cardWidth,
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        }
        y += canvas.px(28, scale);

        if (path != null && !canvas.isTouchMode()) {
            canvas.drawButton(graphics, "‹  Thư mục trước", pad, y,
                    cardWidth, canvas.px(40, scale),
                    NearTransferCanvas.ACTION_BROWSER_PARENT,
                    false, scale);
            y += canvas.px(48, scale);
        }

        String listingMessage = canvas.getFileBrowserMessage();
        if (listingMessage.length() > 0) {
            UiKit.drawWrapped(graphics, listingMessage, pad, y, cardWidth,
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL, 2);
            y += canvas.px(34, scale);
        }
        String status = canvas.getStatusMessage();
        if (status.length() > 0 &&
                !status.endsWith(" mục được chọn.") &&
                !status.startsWith("Đang dùng điều khiển") &&
                !status.startsWith("Đang dùng D-pad")) {
            UiKit.drawWrapped(graphics, status, pad, y, cardWidth,
                    UiKit.PRIMARY, UiKit.FONT_SMALL, 2);
            y += canvas.px(30, scale);
        }

        int count = canvas.getFileBrowserEntryCount();
        int page = canvas.getFileBrowserPage();
        int first = page * 10;
        int last = Math.min(count, first + 10);
        int i;
        if (count == 0) {
            int emptyHeight = canvas.px(74, scale);
            UiKit.fillCard(graphics, pad, y, cardWidth, emptyHeight,
                    UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawWrapped(graphics,
                    path == null ? "Không tìm thấy bộ nhớ được cấp quyền." :
                            "Thư mục trống hoặc không có mục phù hợp.",
                    pad + canvas.px(14, scale), y + canvas.px(15, scale),
                    cardWidth - canvas.px(28, scale),
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY, 2);
            y += emptyHeight + canvas.px(8, scale);
        } else {
            for (i = first; i < last; i++) {
                FileBrowserEntry entry = canvas.getFileBrowserEntry(i);
                if (entry == null) {
                    continue;
                }
                int rowHeight = canvas.px(56, scale);
                int action = NearTransferCanvas.ACTION_BROWSER_ENTRY_BASE + i;
                boolean focused = canvas.isFocusedAction(action);
                UiKit.fillCard(graphics, pad, y, cardWidth, rowHeight,
                        focused ? UiKit.PRIMARY :
                                (entry.selected ? UiKit.SURFACE_HIGH :
                                        UiKit.SURFACE_CONTAINER), scale);

                int iconSize = canvas.px(22, scale);
                int iconX = pad + canvas.px(12, scale);
                int iconY = y + (rowHeight - iconSize) / 2;
                if (entry.directory) {
                    UiKit.drawFolderIcon(graphics, iconX, iconY, iconSize,
                            focused ? UiKit.ON_PRIMARY : UiKit.PRIMARY,
                            focused ? UiKit.PRIMARY :
                                    UiKit.SURFACE_CONTAINER, scale);
                } else {
                    int boxSize = canvas.px(22, scale);
                    UiKit.drawCheckbox(graphics, iconX, iconY, boxSize,
                            entry.selected,
                            entry.selected ?
                                    (focused ? UiKit.ON_PRIMARY : UiKit.PRIMARY) :
                                    UiKit.SURFACE_HIGH,
                            focused ? UiKit.ON_PRIMARY :
                                    UiKit.ON_SURFACE_VARIANT,
                            focused ? UiKit.PRIMARY : UiKit.ON_PRIMARY,
                            scale);
                }
                UiKit.drawText(graphics, entry.name,
                        iconX + iconSize + canvas.px(10, scale),
                        y + canvas.px(11, scale), cardWidth -
                                iconSize - canvas.px(54, scale),
                         focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE,
                         UiKit.FONT_BOLD);
                UiKit.drawText(graphics,
                        entry.directory ? "Thư mục" :
                                (entry.selected ? "Đã chọn · Tệp" : "Tệp"),
                        iconX + iconSize + canvas.px(10, scale),
                        y + canvas.px(33, scale), cardWidth -
                                iconSize - canvas.px(54, scale),
                        focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE_VARIANT,
                        UiKit.FONT_SMALL);
                canvas.addHit(pad, y, cardWidth, rowHeight, action);
                y += rowHeight + canvas.px(7, scale);
            }
        }

        if (canvas.getFileBrowserPageCount() > 1) {
            int gap = canvas.px(8, scale);
            int buttonWidth = (cardWidth - gap) / 2;
            canvas.drawButton(graphics, "‹ Trước", pad, y, buttonWidth,
                    canvas.px(40, scale),
                    NearTransferCanvas.ACTION_BROWSER_PREVIOUS_PAGE,
                    false, scale);
            canvas.drawButton(graphics, "Tiếp ›", pad + buttonWidth + gap, y,
                    buttonWidth, canvas.px(40, scale),
                    NearTransferCanvas.ACTION_BROWSER_NEXT_PAGE,
                    false, scale);
            y += canvas.px(50, scale);
        }

        if (canvas.isReceiveLocationMode()) {
            y = drawReceiveLocationActions(canvas, graphics, pad,
                    cardWidth, y, scale);
            return y + canvas.px(8, scale);
        }

        int selectedCount = canvas.getSelectedFileCount();
        if (selectedCount > 0) {
            canvas.drawButton(graphics, "Thêm " + selectedCount + " mục",
                    pad, y, cardWidth, canvas.px(48, scale),
                    NearTransferCanvas.ACTION_ADD_SELECTED, true, scale);
            y += canvas.px(56, scale);
        }
        return y + canvas.px(8, scale);
    }

    private static int drawReceiveLocationActions(
            NearTransferCanvas canvas, Graphics graphics, int pad,
            int cardWidth, int y, int scale) {
        String path = canvas.getFileBrowserPath();
        boolean storageRoot = Jsr75FileStorage.isStorageRoot(path);
        int gap = canvas.px(8, scale);
        int buttonWidth = (cardWidth - gap) / 2;
        int buttonHeight = canvas.px(46, scale);
        if (storageRoot) {
            canvas.drawButton(graphics, "Mặc định", pad, y, buttonWidth,
                    buttonHeight, NearTransferCanvas.ACTION_RECEIVE_DEFAULT,
                    false, scale);
            canvas.drawButton(graphics, "Dùng ổ này",
                    pad + buttonWidth + gap, y, buttonWidth, buttonHeight,
                    NearTransferCanvas.ACTION_RECEIVE_SAVE_HERE,
                    true, scale);
        } else {
            String hint = path == null ?
                    "Mở ổ C:/ hoặc E:/, sau đó chọn Dùng ổ này." :
                    "Chỉ chọn ổ gốc C:/ hoặc E:/. Nhấn Lùi để quay lại.";
            UiKit.drawWrapped(graphics, hint, pad, y, cardWidth,
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL, 2);
            y += canvas.px(34, scale);
            canvas.drawButton(graphics, "Mặc định", pad, y, cardWidth,
                    buttonHeight, NearTransferCanvas.ACTION_RECEIVE_DEFAULT,
                    false, scale);
        }
        return y + buttonHeight + canvas.px(10, scale);
    }

    private static int drawTextComposer(NearTransferCanvas canvas,
                                        Graphics graphics, int width,
                                        int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(8, scale);

        int previewHeight = canvas.px(150, scale);
        UiKit.fillCard(graphics, pad, y, cardWidth, previewHeight,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawText(graphics, "TIN NHẮN", pad + canvas.px(14, scale),
                y + canvas.px(12, scale), cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        String text = canvas.getTextDraft();
        if (text.length() == 0) {
            text = "Chưa nhập nội dung.";
        }
        UiKit.drawWrapped(graphics, text,
                pad + canvas.px(14, scale), y + canvas.px(36, scale),
                cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BODY, 7);
        int charCount = canvas.getTextDraft().length();
        UiKit.drawText(graphics, charCount + " ký tự",
                pad + canvas.px(14, scale), y + previewHeight -
                        canvas.px(24, scale),
                cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        y += previewHeight + canvas.px(14, scale);

        canvas.drawButton(graphics, "Chỉnh sửa tin nhắn",
                pad, y, cardWidth, canvas.px(48, scale),
                NearTransferCanvas.ACTION_EDIT_TEXT, false, scale);
        y += canvas.px(58, scale);
        canvas.drawButton(graphics, "Thêm vào hàng chờ",
                pad, y, cardWidth, canvas.px(48, scale),
                NearTransferCanvas.ACTION_QUEUE_TEXT, false, scale);
        y += canvas.px(58, scale);
        canvas.drawButton(graphics, "Thêm tin nhắn và chọn thiết bị",
                pad, y, cardWidth, canvas.px(48, scale),
                NearTransferCanvas.ACTION_SEND_TEXT, true, scale);
        return y + canvas.px(60, scale);
    }

    private static int drawSavedDevices(NearTransferCanvas canvas,
                                        Graphics graphics, int width,
                                        int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int y = contentTop + canvas.px(8, scale);

        int count = canvas.getSavedCount();
        if (count == 0) {
            UiKit.fillCard(graphics, pad, y, width - pad * 2,
                    canvas.px(66, scale), UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawText(graphics, "Chưa có thiết bị đã lưu.",
                    pad + canvas.px(14, scale), y + canvas.px(22, scale),
                    width - pad * 2 - canvas.px(28, scale),
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY);
            return y + canvas.px(76, scale);
        }

        int row = 0;
        while (row < count) {
            int rowHeight = canvas.px(72, scale);
            int rowAction = canvas.isTouchMode() ?
                    NearTransferCanvas.ACTION_SEND_SAVED_BASE + row :
                    NearTransferCanvas.ACTION_OPEN_SAVED_PEER_BASE + row;
            boolean focused = canvas.isFocusedAction(rowAction);
            UiKit.fillCard(graphics, pad, y, width - pad * 2, rowHeight,
                    focused ? UiKit.PRIMARY : UiKit.SURFACE_CONTAINER, scale);
            if (!canvas.isTouchMode()) {
                canvas.addHit(pad, y, width - pad * 2, rowHeight, rowAction);
            }
            UiKit.drawText(graphics, canvas.getSavedDeviceName(row),
                    pad + canvas.px(14, scale), y + canvas.px(15, scale),
                    width - pad * 2 - canvas.px(
                            canvas.isTouchMode() ? 82 : 28, scale),
                    focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE,
                    UiKit.FONT_BOLD);
            UiKit.drawText(graphics, canvas.getSavedDeviceEndpoint(row),
                    pad + canvas.px(14, scale), y + canvas.px(42, scale),
                    width - pad * 2 - canvas.px(
                            canvas.isTouchMode() ? 82 : 28, scale),
                    focused ? UiKit.ON_PRIMARY : UiKit.ON_SURFACE_VARIANT,
                    UiKit.FONT_SMALL);
            if (canvas.isTouchMode()) {
                canvas.drawButton(graphics, "Xóa",
                        width - pad - canvas.px(60, scale),
                        y + canvas.px(14, scale),
                        canvas.px(48, scale), canvas.px(44, scale),
                        NearTransferCanvas.ACTION_REMOVE_SAVED_BASE + row,
                        false, scale);
            }
            y += rowHeight + canvas.px(8, scale);
            row++;
        }
        return y + canvas.px(12, scale);
    }

    private static int drawReplaceConfirmation(NearTransferCanvas canvas,
                                               Graphics graphics, int width,
                                               int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(10, scale);
        boolean replacingFiles = canvas.isReplaceWithText();

        UiKit.fillCard(graphics, pad, y, cardWidth, canvas.px(166, scale),
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawText(graphics,
                replacingFiles ? "Thay tập tin bằng văn bản?" :
                        "Thay văn bản bằng nội dung mới?",
                pad + canvas.px(14, scale), y + canvas.px(16, scale),
                cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        String message = replacingFiles ?
                "Các tập tin đang chờ chỉ bị thay khi bạn thêm tin nhắn vào hàng chờ." :
                "Tin nhắn đang chờ chỉ bị thay nếu bạn thêm tệp hoặc media.";
        UiKit.drawWrapped(graphics, message,
                pad + canvas.px(14, scale), y + canvas.px(50, scale),
                cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY, 4);
        UiKit.drawText(graphics, "Nội dung hiện tại vẫn được giữ nếu bạn hủy.",
                pad + canvas.px(14, scale), y + canvas.px(122, scale),
                cardWidth - canvas.px(28, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        y += canvas.px(184, scale);

        int gap = canvas.px(8, scale);
        int buttonWidth = (cardWidth - gap) / 2;
        canvas.drawButton(graphics, "Hủy", pad, y, buttonWidth,
                canvas.px(48, scale), NearTransferCanvas.ACTION_CANCEL_REPLACE,
                false, scale);
        canvas.drawButton(graphics, "Tiếp tục",
                pad + buttonWidth + gap, y, buttonWidth, canvas.px(48, scale),
                NearTransferCanvas.ACTION_CONFIRM_REPLACE, true, scale);
        return y + canvas.px(62, scale);
    }
}