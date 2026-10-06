package com.nearbyshare.j2me;

import javax.microedition.lcdui.Graphics;

/** Offer, transfer progress, result and received-message screens. */
final class TransferUi {
    private TransferUi() {
    }

    static int draw(NearTransferCanvas canvas, Graphics graphics, int width,
                    int contentTop, int scale) {
        int screen = canvas.getScreen();
        if (screen == NearTransferCanvas.OFFER_FILES ||
                screen == NearTransferCanvas.OFFER_TEXT) {
            return drawOffer(canvas, graphics, width, contentTop, scale);
        }
        if (screen == NearTransferCanvas.PROGRESS) {
            return drawProgress(canvas, graphics, width, contentTop, scale);
        }
        if (screen == NearTransferCanvas.RESULT_SUCCESS ||
                screen == NearTransferCanvas.RESULT_ERROR) {
            return drawResult(canvas, graphics, width, contentTop, scale);
        }
        return drawReceivedText(canvas, graphics, width, contentTop, scale);
    }

    private static int drawOffer(NearTransferCanvas canvas, Graphics graphics,
                                 int width, int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(8, scale);
        boolean textOffer = canvas.getScreen() == NearTransferCanvas.OFFER_TEXT;
        IncomingOffer offer = canvas.getIncomingOffer();

        UiKit.fillCard(graphics, pad, y, cardWidth, canvas.px(74, scale),
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawDeviceBadge(graphics, pad + canvas.px(14, scale),
                y + canvas.px(18, scale), canvas.px(38, scale), scale);
        UiKit.drawText(graphics, offer == null ?
                        canvas.getIncomingSenderName() : offer.senderName,
                pad + canvas.px(64, scale),
                y + canvas.px(16, scale), cardWidth - canvas.px(78, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        UiKit.drawText(graphics, offer == null ? "" :
                        offer.senderAddress,
                pad + canvas.px(64, scale), y + canvas.px(42, scale),
                cardWidth - canvas.px(78, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        y += canvas.px(88, scale);

        String heading = textOffer ?
                "Thiết bị muốn gửi cho bạn một tin nhắn:" :
                "Thiết bị muốn gửi cho bạn các tệp sau:";
        UiKit.drawWrapped(graphics, heading, pad, y, cardWidth,
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY, 3);
        y += canvas.px(48, scale);

        if (textOffer) {
            long bytes = offer == null ? 0 : offer.totalBytes;
            int messageHeight = canvas.px(74, scale);
            UiKit.fillCard(graphics, pad, y, cardWidth, messageHeight,
                    UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawText(graphics,
                    "TIN NHẮN · " + formatSize(bytes),
                    pad + canvas.px(14, scale), y + canvas.px(12, scale),
                    cardWidth - canvas.px(28, scale),
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
            UiKit.drawWrapped(graphics,
                    "Nội dung sẽ được nhận sau khi bạn chấp nhận.",
                    pad + canvas.px(14, scale), y + canvas.px(34, scale),
                    cardWidth - canvas.px(28, scale),
                    UiKit.ON_SURFACE, UiKit.FONT_SMALL, 2);
            y += messageHeight + canvas.px(14, scale);
        } else {
            int i;
            int count = offer == null ? 0 : offer.items.length;
            for (i = 0; i < count; i++) {
                int rowHeight = canvas.px(48, scale);
                UiKit.fillCard(graphics, pad, y, cardWidth, rowHeight,
                        UiKit.SURFACE_CONTAINER, scale);
                UiKit.drawText(graphics, offer.items[i].name + " · " +
                                formatSize(offer.items[i].size),
                        pad + canvas.px(13, scale), y + canvas.px(15, scale),
                        cardWidth - canvas.px(26, scale),
                        UiKit.ON_SURFACE, UiKit.FONT_BODY);
                y += rowHeight + canvas.px(7, scale);
            }
            UiKit.drawText(graphics, count + " mục · " +
                            formatSize(offer == null ? 0 : offer.totalBytes),
                    pad, y + canvas.px(1, scale), cardWidth,
                    UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
            y += canvas.px(30, scale);
        }

        y += canvas.px(12, scale);
        int gap = canvas.px(8, scale);
        int buttonWidth = (cardWidth - gap) / 2;
        canvas.drawButton(graphics, "Từ chối", pad, y, buttonWidth,
                canvas.px(48, scale), NearTransferCanvas.ACTION_DECLINE,
                false, scale);
        canvas.drawButton(graphics, textOffer ? "Nhận văn bản" : "Nhận tệp",
                pad + buttonWidth + gap, y, buttonWidth, canvas.px(48, scale),
                NearTransferCanvas.ACTION_ACCEPT, true, scale);
        return y + canvas.px(64, scale);
    }

    private static int drawProgress(NearTransferCanvas canvas, Graphics graphics,
                                   int width, int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(8, scale);
        boolean textTransfer = canvas.isOutgoing() ?
                canvas.isQueueText() : canvas.isIncomingTextTransfer();
        int rowCount = canvas.getTransferItemCount();
        if (rowCount < 1) {
            rowCount = 1;
        }
        int percent = canvas.getProgressPercent();
        int activeIndex = Math.min(canvas.getProgressItemIndex(),
                rowCount - 1);
        int completedCount = percent >= 100 ? rowCount : activeIndex;
        int statusHeight = canvas.px(108, scale);
        UiKit.fillCard(graphics, pad, y, cardWidth, statusHeight,
                UiKit.SURFACE_CONTAINER, scale);

        int ringSize = canvas.px(72, scale);
        int ringX = pad + canvas.px(12, scale);
        int ringY = y + (statusHeight - ringSize) / 2;
        graphics.setColor(UiKit.SURFACE_HIGH);
        graphics.fillArc(ringX, ringY, ringSize, ringSize, 0, 360);
        graphics.setColor(UiKit.PRIMARY);
        graphics.fillArc(ringX, ringY, ringSize, ringSize, 270,
                canvas.getProgressPercent() * 360 / 100);
        int inner = ringSize - canvas.px(12, scale);
        graphics.setColor(UiKit.SURFACE_CONTAINER);
        graphics.fillArc(ringX + canvas.px(6, scale),
                ringY + canvas.px(6, scale), inner, inner, 0, 360);
        UiKit.drawCentered(graphics, canvas.getProgressPercent() + "%",
                ringX, ringY, ringSize, ringSize,
                UiKit.ON_SURFACE, UiKit.FONT_BOLD, scale);

        int detailsX = ringX + ringSize + canvas.px(13, scale);
        int detailsWidth = cardWidth - (detailsX - pad) - canvas.px(10, scale);
        UiKit.drawText(graphics,
                canvas.isOutgoing() ? "DỮ LIỆU ĐÃ GỬI" : "DỮ LIỆU ĐÃ NHẬN",
                detailsX, y + canvas.px(20, scale), detailsWidth,
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        String dataProgress = canvas.getProgressTotal() > 0 ?
                formatSize(canvas.getProgressTransferred()) + " / " +
                        formatSize(canvas.getProgressTotal()) :
                "Đang chờ xác nhận từ thiết bị nhận";
        UiKit.drawText(graphics, dataProgress,
                detailsX, y + canvas.px(43, scale), detailsWidth,
                UiKit.ON_SURFACE, UiKit.FONT_BOLD);
        UiKit.drawText(graphics, textTransfer ? "1 tin nhắn" :
                        rowCount + " nội dung",
                detailsX, y + canvas.px(67, scale), detailsWidth,
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        UiKit.drawText(graphics,
                canvas.isOutgoing() ? "Đang gửi…" : "Đang nhận…",
                detailsX, y + canvas.px(86, scale), detailsWidth,
                UiKit.PRIMARY, UiKit.FONT_SMALL);
        y += statusHeight + canvas.px(18, scale);

        UiKit.drawText(graphics, "NỘI DUNG", pad, y,
                cardWidth - canvas.px(60, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BOLD);
        UiKit.drawText(graphics,
                (textTransfer ? 1 : rowCount) + " mục",
                width - pad - canvas.px(42, scale),
                y, canvas.px(42, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL);
        y += canvas.px(25, scale);

        int i;
        for (i = 0; i < rowCount; i++) {
            String name = canvas.getTransferItemName(i);
            long itemSize = canvas.getTransferItemSize(i);
            if (itemSize >= 0) {
                name += " · " + formatSize(itemSize);
            }
            int rowHeight = canvas.px(58, scale);
            UiKit.fillCard(graphics, pad, y, cardWidth, rowHeight,
                    UiKit.SURFACE_CONTAINER, scale);
            UiKit.drawText(graphics, name,
                    pad + canvas.px(12, scale), y + canvas.px(8, scale),
                    cardWidth - canvas.px(24, scale),
                    UiKit.ON_SURFACE, UiKit.FONT_BOLD);
            String itemStatus = i < completedCount ? "Đã xong" :
                    (i == activeIndex ? "Đang xử lý · " +
                            (canvas.getProgressItemTotal() > 0 ?
                            formatSize(canvas.getProgressItemTransferred()) +
                            " / " + formatSize(canvas.getProgressItemTotal()) :
                            percent + "%") :
                    "Đang chờ");
            UiKit.drawText(graphics, itemStatus,
                    pad + canvas.px(12, scale), y + canvas.px(29, scale),
                    cardWidth - canvas.px(24, scale),
                    i < completedCount ? UiKit.PRIMARY :
                            UiKit.ON_SURFACE_VARIANT,
                    UiKit.FONT_SMALL);
            int barX = pad + canvas.px(12, scale);
            int barY = y + canvas.px(47, scale);
            int barWidth = cardWidth - canvas.px(24, scale);
            int barHeight = canvas.px(4, scale);
            graphics.setColor(UiKit.SURFACE_HIGH);
            graphics.fillRoundRect(barX, barY, barWidth, barHeight,
                    barHeight, barHeight);
            graphics.setColor(UiKit.PRIMARY);
            int amount = i < completedCount ? barWidth :
                    (i == activeIndex ? barWidth * percent / 100 : 0);
            if (amount > 0) {
                graphics.fillRoundRect(barX, barY, amount, barHeight,
                        barHeight, barHeight);
            }
            y += rowHeight + canvas.px(7, scale);
        }

        y += canvas.px(4, scale);
        if (canvas.isOutgoing()) {
            canvas.drawButton(graphics, "Hủy gửi", pad, y, cardWidth,
                    canvas.px(48, scale),
                    NearTransferCanvas.ACTION_PROGRESS_CANCEL, false, scale);
            return y + canvas.px(62, scale);
        }
        return y + canvas.px(12, scale);
    }

    private static int drawResult(NearTransferCanvas canvas, Graphics graphics,
                                  int width, int contentTop, int scale) {
        int pad = canvas.px(28, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(8, scale);
        boolean failed = canvas.getScreen() == NearTransferCanvas.RESULT_ERROR;
        boolean textTransfer = canvas.isOutgoing() ?
                canvas.isQueueText() : canvas.isIncomingTextTransfer();
        int itemCount = canvas.getTransferItemCount();
        long totalSize = getTotalSize(canvas, itemCount);
        int cardHeight = canvas.px(108, scale);
        UiKit.fillCard(graphics, pad, y, cardWidth, cardHeight,
                UiKit.SURFACE_CONTAINER, scale);

        int badge = canvas.px(46, scale);
        int badgeX = pad + canvas.px(14, scale);
        int badgeY = y + canvas.px(17, scale);
        UiKit.drawResultBadge(graphics, badgeX, badgeY, badge, failed, scale);

        int textX = badgeX + badge + canvas.px(12, scale);
        int textWidth = cardWidth - (textX - pad) - canvas.px(10, scale);
        UiKit.drawWrapped(graphics,
                failed ? "Truyền bị gián đoạn" :
                        (canvas.isOutgoing() ? "Đã gửi xong" : "Đã nhận xong"),
                textX, y + canvas.px(19, scale), textWidth,
                UiKit.ON_SURFACE, UiKit.FONT_BOLD, 2);
        String resultSummary = textTransfer ? "1 tin nhắn" :
                itemCount + " tệp";
        resultSummary += " · " + formatSize(totalSize);
        if (failed) {
            resultSummary += " · đã truyền " +
                    formatSize(canvas.getProgressTransferred());
        }
        UiKit.drawWrapped(graphics, resultSummary,
                textX, y + canvas.px(59, scale), textWidth,
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_SMALL, 2);
        y += cardHeight + canvas.px(18, scale);

        UiKit.drawText(graphics,
                failed ? "NỘI DUNG CHƯA HOÀN TẤT" :
                        (canvas.isOutgoing() ? "NỘI DUNG ĐÃ GỬI" :
                        "NỘI DUNG ĐÃ NHẬN"),
                pad, y, cardWidth, UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BOLD);
        y += canvas.px(27, scale);

        if (canvas.isDetailsExpanded()) {
            int i;
            int detailCount = itemCount;
            for (i = 0; i < detailCount; i++) {
                int rowHeight = canvas.px(42, scale);
                UiKit.fillCard(graphics, pad, y, cardWidth, rowHeight,
                        UiKit.SURFACE_CONTAINER, scale);
                String detail = canvas.getTransferItemName(i);
                long size = canvas.getTransferItemSize(i);
                if (size >= 0) {
                    detail += " · " + formatSize(size);
                }
                UiKit.drawText(graphics, detail,
                        pad + canvas.px(12, scale), y + canvas.px(13, scale),
                        cardWidth - canvas.px(24, scale),
                        UiKit.ON_SURFACE, UiKit.FONT_SMALL);
                y += rowHeight + canvas.px(6, scale);
            }
        } else {
            int rowHeight = canvas.px(54, scale);
            UiKit.fillCard(graphics, pad, y, cardWidth, rowHeight,
                    UiKit.SURFACE_CONTAINER, scale);
            String firstItem = itemCount > 0 ?
                    canvas.getTransferItemName(0) : "Nội dung";
            UiKit.drawText(graphics, firstItem,
                    pad + canvas.px(12, scale), y + canvas.px(8, scale),
                    cardWidth - canvas.px(24, scale),
                    UiKit.ON_SURFACE, UiKit.FONT_BOLD);
            UiKit.drawText(graphics, failed ?
                            "Đã dừng · " +
                            formatSize(canvas.getProgressTransferred()) :
                            "Đã xong",
                    pad + canvas.px(12, scale), y + canvas.px(31, scale),
                    cardWidth - canvas.px(24, scale),
                    failed ? UiKit.ERROR : UiKit.PRIMARY,
                    UiKit.FONT_SMALL);
            y += rowHeight + canvas.px(8, scale);
        }

        canvas.drawButton(graphics,
                canvas.isDetailsExpanded() ? "Ẩn chi tiết" : "Chi tiết",
                pad, y, cardWidth, canvas.px(42, scale),
                NearTransferCanvas.ACTION_DETAILS, false, scale);
        y += canvas.px(52, scale);
        if (failed && canvas.isOutgoing()) {
            int gap = canvas.px(8, scale);
            int half = (cardWidth - gap) / 2;
            canvas.drawButton(graphics, "Đóng", pad, y, half,
                    canvas.px(48, scale), NearTransferCanvas.ACTION_CLOSE,
                    false, scale);
            canvas.drawButton(graphics, "Thử lại", pad + half + gap, y, half,
                    canvas.px(48, scale), NearTransferCanvas.ACTION_RETRY,
                    true, scale);
        } else {
            canvas.drawButton(graphics, "Đóng", pad, y, cardWidth,
                    canvas.px(48, scale), NearTransferCanvas.ACTION_CLOSE,
                    !failed, scale);
        }
        return y + canvas.px(62, scale);
    }

    private static int drawReceivedText(NearTransferCanvas canvas,
                                        Graphics graphics, int width,
                                        int contentTop, int scale) {
        int pad = canvas.px(30, scale);
        int cardWidth = width - pad * 2;
        int y = contentTop + canvas.px(10, scale);
        int badge = canvas.px(56, scale);
        int badgeX = (width - badge) / 2;
        UiKit.drawDeviceBadge(graphics, badgeX, y, badge, scale);
        y += badge + canvas.px(10, scale);

        UiKit.drawCentered(graphics, canvas.getIncomingSenderName(), pad, y,
                cardWidth, canvas.px(34, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BOLD, scale);
        y += canvas.px(44, scale);
        UiKit.drawCentered(graphics, "đã gửi cho bạn một tin nhắn:",
                pad, y, cardWidth, canvas.px(28, scale),
                UiKit.ON_SURFACE_VARIANT, UiKit.FONT_BODY, scale);
        y += canvas.px(37, scale);

        int messageHeight = canvas.px(116, scale);
        UiKit.fillCard(graphics, pad, y, cardWidth, messageHeight,
                UiKit.SURFACE_CONTAINER, scale);
        UiKit.drawWrapped(graphics, canvas.getReceivedMessage(),
                pad + canvas.px(15, scale), y + canvas.px(15, scale),
                cardWidth - canvas.px(30, scale),
                UiKit.ON_SURFACE, UiKit.FONT_BODY, 5);
        y += messageHeight + canvas.px(14, scale);

        String savedPath = canvas.getReceivedTextPath();
        String saveLabel = savedPath.length() > 0 ?
                "Đã lưu tin nhắn" : "Lưu thành tệp tin";
        canvas.drawButton(graphics, saveLabel, pad, y, cardWidth,
                canvas.px(52, scale), NearTransferCanvas.ACTION_SAVE_TEXT,
                savedPath.length() == 0, scale);
        return y + canvas.px(64, scale);
    }

    private static long getTotalSize(NearTransferCanvas canvas, int count) {
        long total = canvas.getProgressTotal();
        if (total > 0) {
            return total;
        }
        long sum = 0;
        int i;
        for (i = 0; i < count; i++) {
            long size = canvas.getTransferItemSize(i);
            if (size > 0) {
                sum += size;
            }
        }
        return sum;
    }

    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        }
        return (size / (1024 * 1024)) + " MB";
    }

}