package com.iafenvoy.spb.screen.layout;

import net.minecraft.client.gui.Font;

public record QuantityControls(ScreenRectangle minus, ScreenRectangle count, ScreenRectangle plus) {
    public static QuantityControls of(ScreenRectangle card, int quantity, Font font) {
        int height = 16;
        int y = card.y() + (card.h() - height) / 2;
        int countWidth = Math.max(12, Math.min(24, font.width(String.valueOf(Math.max(0, quantity))) + 6));
        int x = card.right() - 2 - (28 + countWidth);
        return new QuantityControls(new ScreenRectangle(x, y, 14, height),
                new ScreenRectangle(x + 14, y, countWidth, height),
                new ScreenRectangle(x + 14 + countWidth, y, 14, height));
    }
}
