package com.nearbyshare.j2me;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/** Shared Android-derived colors and low-resolution drawing helpers. */
final class UiKit {
    static final int BACKGROUND = 0x191A1F;
    static final int SURFACE = 0x1D1E24;
    static final int SURFACE_CONTAINER = 0x22242B;
    static final int SURFACE_HIGH = 0x2D3039;
    static final int PRIMARY = 0xB8C8FF;
    static final int PRIMARY_PRESSED = 0xA0B3F0;
    static final int ON_PRIMARY = 0x1B253C;
    static final int ON_SURFACE = 0xE6E4EC;
    static final int ON_SURFACE_VARIANT = 0xBFC3CF;
    static final int ERROR = 0xFFB4AB;
    static final int ERROR_CONTAINER = 0x4D272A;
    static final int SUCCESS = 0xB8C8FF;

    static final Font FONT_SMALL =
            Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    static final Font FONT_BODY =
            Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
    static final Font FONT_BOLD =
            Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);

    private UiKit() {
    }

    static void fillCard(Graphics graphics, int x, int y, int width, int height,
                         int color, int scale) {
        graphics.setColor(color);
        graphics.fillRoundRect(x, y, width, height, px(14, scale), px(14, scale));
    }

    static void strokeCard(Graphics graphics, int x, int y, int width, int height,
                           int color, int scale) {
        graphics.setColor(color);
        graphics.drawRoundRect(x, y, width, height, px(14, scale), px(14, scale));
    }

    static void drawBackArrow(Graphics graphics, int x, int y, int size,
                              int color, int scale) {
        int left = x + size / 4;
        int centerY = y + size / 2;
        int right = x + size * 3 / 4;
        int headX = x + size / 2;
        int headTop = y + size / 4;
        int headBottom = y + size * 3 / 4;
        int stroke = px(2, scale);
        if (stroke < 1) {
            stroke = 1;
        }

        graphics.setColor(color);
        int offset;
        for (offset = 0; offset < stroke; offset++) {
            graphics.drawLine(right, centerY + offset, left, centerY + offset);
            graphics.drawLine(left, centerY + offset,
                    headX, headTop + offset);
            graphics.drawLine(left, centerY + offset,
                    headX, headBottom + offset);
        }
    }

    static void drawCheckbox(Graphics graphics, int x, int y, int size,
                             boolean checked, int fillColor, int outlineColor,
                             int checkColor, int scale) {
        int radius = px(5, scale);
        if (radius < 1) {
            radius = 1;
        }
        graphics.setColor(fillColor);
        graphics.fillRoundRect(x, y, size, size, radius, radius);
        if (checked) {
            drawCheckMark(graphics, x + size / 6, y + size / 6,
                    size * 2 / 3, checkColor, scale);
        } else {
            graphics.setColor(outlineColor);
            graphics.drawRoundRect(x, y, size, size, radius, radius);
        }
    }

    static void drawFolderIcon(Graphics graphics, int x, int y, int size,
                               int color, int seamColor, int scale) {
        int radius = px(4, scale);
        if (radius < 1) {
            radius = 1;
        }
        int tabX = x + size / 10;
        int tabY = y + size / 10;
        int tabWidth = size * 9 / 20;
        int tabHeight = size / 4;
        int bodyX = x + size / 12;
        int bodyY = y + size / 4;
        int bodyWidth = size * 5 / 6;
        int bodyHeight = size * 2 / 3;

        graphics.setColor(color);
        graphics.fillRoundRect(tabX, tabY, tabWidth, tabHeight + 1,
                radius, radius);
        graphics.fillRoundRect(bodyX, bodyY, bodyWidth, bodyHeight,
                radius, radius);
        graphics.setColor(seamColor);
        int seamY = bodyY + bodyHeight / 3;
        graphics.drawLine(bodyX + size / 7, seamY,
                bodyX + bodyWidth - size / 7, seamY);
    }

    static void drawDeviceBadge(Graphics graphics, int x, int y, int size,
                                int scale) {
        int badgeRadius = px(8, scale);
        int phoneRadius = px(4, scale);
        if (badgeRadius < 1) {
            badgeRadius = 1;
        }
        if (phoneRadius < 1) {
            phoneRadius = 1;
        }
        graphics.setColor(SURFACE_HIGH);
        graphics.fillRoundRect(x, y, size, size, badgeRadius, badgeRadius);

        int phoneWidth = size * 42 / 100;
        int phoneHeight = size * 76 / 100;
        int phoneX = x + (size - phoneWidth) / 2;
        int phoneY = y + (size - phoneHeight) / 2;
        int bezel = size / 13;
        if (bezel < 1) {
            bezel = 1;
        }
        graphics.setColor(PRIMARY);
        graphics.fillRoundRect(phoneX, phoneY, phoneWidth, phoneHeight,
                phoneRadius, phoneRadius);

        int screenX = phoneX + bezel;
        int screenY = phoneY + bezel * 2;
        int screenWidth = phoneWidth - bezel * 2;
        int screenHeight = phoneHeight - bezel * 4 - 1;
        if (screenWidth > 0 && screenHeight > 0) {
            graphics.setColor(SURFACE_HIGH);
            graphics.fillRoundRect(screenX, screenY, screenWidth, screenHeight,
                    phoneRadius / 2, phoneRadius / 2);
        }

        graphics.setColor(PRIMARY);
        int speakerWidth = phoneWidth / 4;
        if (speakerWidth < 2) {
            speakerWidth = 2;
        }
        int speakerY = phoneY + bezel;
        graphics.drawLine(phoneX + (phoneWidth - speakerWidth) / 2, speakerY,
                phoneX + (phoneWidth + speakerWidth) / 2, speakerY);
        int homeSize = size / 16;
        if (homeSize < 2) {
            homeSize = 2;
        }
        graphics.fillArc(phoneX + (phoneWidth - homeSize) / 2,
                phoneY + phoneHeight - bezel - homeSize,
                homeSize, homeSize, 0, 360);
    }

    static void drawResultBadge(Graphics graphics, int x, int y, int size,
                                boolean failed, int scale) {
        graphics.setColor(failed ? ERROR_CONTAINER : SURFACE_HIGH);
        graphics.fillArc(x, y, size, size, 0, 360);
        if (failed) {
            int markWidth = px(3, scale);
            if (markWidth < 2) {
                markWidth = 2;
            }
            int markHeight = size / 3;
            int markX = x + (size - markWidth) / 2;
            int markY = y + size / 4;
            int radius = markWidth / 2;
            graphics.setColor(ERROR);
            graphics.fillRoundRect(markX, markY, markWidth, markHeight,
                    radius, radius);
            int dotSize = size / 12;
            if (dotSize < 2) {
                dotSize = 2;
            }
            graphics.fillArc(x + (size - dotSize) / 2,
                    y + size * 3 / 4, dotSize, dotSize, 0, 360);
        } else {
            drawCheckMark(graphics, x + size / 4, y + size / 4,
                    size / 2, PRIMARY, scale);
        }
    }

    private static void drawCheckMark(Graphics graphics, int x, int y,
                                      int size, int color, int scale) {
        int stroke = px(2, scale);
        if (stroke < 1) {
            stroke = 1;
        }
        int startX = x + size / 5;
        int startY = y + size / 2;
        int bendX = x + size * 2 / 5;
        int bendY = y + size * 7 / 10;
        int endX = x + size * 4 / 5;
        int endY = y + size / 4;

        graphics.setColor(color);
        int offset;
        for (offset = 0; offset < stroke; offset++) {
            graphics.drawLine(startX, startY + offset,
                    bendX, bendY + offset);
            graphics.drawLine(bendX, bendY + offset,
                    endX, endY + offset);
        }
    }

    static void drawButton(Graphics graphics, String label, int x, int y,
                           int width, int height, boolean primary,
                           boolean focused, int scale) {
        int fill = primary || focused ? PRIMARY : SURFACE_CONTAINER;
        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, width, height, px(10, scale), px(10, scale));
        int textColor = primary || focused ? ON_PRIMARY : ON_SURFACE;
        Font buttonFont = FONT_BOLD;
        if (label != null &&
                buttonFont.stringWidth(label) > width - px(12, scale)) {
            buttonFont = FONT_SMALL;
        }
        drawCentered(graphics, label, x + px(6, scale), y, width - px(12, scale),
                height, textColor, buttonFont, scale);
    }

    static void drawText(Graphics graphics, String value, int x, int y,
                         int maxWidth, int color, Font font) {
        if (value == null || value.length() == 0) {
            return;
        }
        graphics.setColor(color);
        graphics.setFont(font);
        String line = ellipsize(value, maxWidth, font);
        graphics.drawString(line, x, y, Graphics.TOP | Graphics.LEFT);
    }

    static int drawWrapped(Graphics graphics, String value, int x, int y,
                           int maxWidth, int color, Font font, int maxLines) {
        if (value == null || value.length() == 0) {
            return y;
        }

        graphics.setColor(color);
        graphics.setFont(font);
        int lineHeight = font.getHeight() + 1;
        int lineCount = 0;
        int cursor = 0;
        int length = value.length();

        while (cursor < length && lineCount < maxLines) {
            while (cursor < length &&
                    (value.charAt(cursor) == ' ' || value.charAt(cursor) == '\t')) {
                cursor++;
            }
            if (cursor >= length) {
                break;
            }

            int end = cursor;
            int lastSpace = -1;
            int lastNewline = -1;
            while (end < length) {
                char character = value.charAt(end);
                if (character == '\n') {
                    lastNewline = end;
                    break;
                }
                if (character == ' ' || character == '\t') {
                    lastSpace = end;
                }
                String candidate = value.substring(cursor, end + 1);
                if (font.stringWidth(candidate) > maxWidth) {
                    break;
                }
                end++;
            }

            if (lastNewline >= 0) {
                end = lastNewline;
            } else if (end < length && lastSpace > cursor) {
                end = lastSpace;
            } else if (end == cursor) {
                end = cursor + 1;
            }

            String line = value.substring(cursor, end);
            if (line.length() > 0) {
                graphics.drawString(line, x, y + lineCount * lineHeight,
                        Graphics.TOP | Graphics.LEFT);
                lineCount++;
            }

            cursor = end;
            if (cursor < length && value.charAt(cursor) == '\n') {
                cursor++;
            } else if (cursor < length && value.charAt(cursor) == ' ') {
                cursor++;
            }
        }
        return y + lineCount * lineHeight;
    }

    static void drawCentered(Graphics graphics, String value, int x, int y,
                             int width, int height, int color, Font font, int scale) {
        graphics.setColor(color);
        graphics.setFont(font);
        String text = ellipsize(value, width, font);
        int drawX = x + (width - font.stringWidth(text)) / 2;
        int drawY = y + (height - font.getHeight()) / 2;
        graphics.drawString(text, drawX, drawY, Graphics.TOP | Graphics.LEFT);
    }

    static String ellipsize(String value, int maxWidth, Font font) {
        if (value == null || font.stringWidth(value) <= maxWidth) {
            return value == null ? "" : value;
        }
        String suffix = "...";
        int suffixWidth = font.stringWidth(suffix);
        int end = value.length();
        while (end > 0 &&
                font.stringWidth(value.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    static int px(int value, int scale) {
        return (value * scale + 50) / 100;
    }
}