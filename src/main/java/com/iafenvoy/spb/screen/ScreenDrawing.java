package com.iafenvoy.spb.screen;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.screen.layout.ScreenRectangle;
import com.iafenvoy.spb.screen.widget.ScrollArea;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Texture-backed panels, vanilla widgets and drawing primitives shared by every screen region.
 */
final class ScreenDrawing {
    static final int HEADER = 0xFFFFFFFF;
    static final int TEXT = 0xFFE6E6E6;
    static final int MUTED = 0xFFA8A8A8;
    static final int DISABLED = 0xFF6E6E6E;
    static final int ACCENT = 0xFFFFD700;
    static final int ERROR = 0xFFFF5555;
    static final int OK = 0xFF55FF55;
    static final int SEPARATOR = 0x30FFFFFF;
    // The sprite metadata preserves the frame and tiles the stone interior at its native scale.
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, "panel/stone");
    private static final Identifier BUTTON = sprite("widget/button");
    private static final Identifier HIGHLIGHTED = sprite("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = sprite("widget/button_disabled");
    private static final Identifier CHECKBOX = sprite("widget/checkbox");
    private static final Identifier CHECKBOX_HIGHLIGHTED = sprite("widget/checkbox_highlighted");
    private static final Identifier CHECKBOX_SELECTED = sprite("widget/checkbox_selected");
    private static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED = sprite("widget/checkbox_selected_highlighted");
    private static final Identifier SLOT = sprite("container/slot");
    private static final Identifier SCROLLER = sprite("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND = sprite("widget/scroller_background");

    private ScreenDrawing() {
    }

    private static Identifier sprite(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    static void panel(GuiGraphicsExtractor graphics, ScreenRectangle rect) {
        blit(graphics, PANEL, rect);
    }

    static void buttonBackground(GuiGraphicsExtractor graphics, ScreenRectangle rect, boolean active, boolean highlighted) {
        blit(graphics, !active ? BUTTON_DISABLED : highlighted ? HIGHLIGHTED : BUTTON, rect);
    }

    static void button(GuiGraphicsExtractor graphics, ScreenText text, ScreenRectangle rect, Component label, boolean active, int mouseX, int mouseY) {
        buttonBackground(graphics, rect, active, active && rect.contains(mouseX, mouseY));
        graphics.centeredText(text.font(), text.trim(label, rect.w() - 8), rect.x() + rect.w() / 2, rect.y() + (rect.h() - text.font().lineHeight) / 2, active ? HEADER : 0xFFA0A0A0);
    }

    static void checkbox(GuiGraphicsExtractor graphics, boolean selected, boolean hover, int x, int y) {
        Identifier sprite = selected ? (hover ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED) : hover ? CHECKBOX_HIGHLIGHTED : CHECKBOX;
        blit(graphics, sprite, new ScreenRectangle(x, y, 17, 17));
    }

    static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        blit(graphics, SLOT, new ScreenRectangle(x, y, 18, 18));
    }

    static void icon(GuiGraphicsExtractor graphics, Optional<IconReference> icon, int x, int y) {
        if (icon.isEmpty()) return;
        IconReference reference = icon.get();
        if (reference.texture().isPresent())
            graphics.blit(RenderPipelines.GUI_TEXTURED, reference.texture().get(), x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        else if (reference.stack().isPresent())
            graphics.item(reference.stack().get(), x, y);
    }

    static void clip(GuiGraphicsExtractor graphics, ScreenRectangle rect) {
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    static void scrollbar(GuiGraphicsExtractor graphics, ScrollArea scroll) {
        if (scroll.maxScroll() <= 0 || scroll.contentHeight() <= 0) return;
        ScreenRectangle area = scroll.bounds();
        int x = area.right() - ScrollArea.BAR_WIDTH;
        blit(graphics, SCROLLER_BACKGROUND, new ScreenRectangle(x, area.y(), ScrollArea.BAR_WIDTH, area.h()));
        int thumbHeight = Math.max(14, area.h() * area.h() / Math.max(area.h(), scroll.contentHeight()));
        int thumbY = area.y() + (int) ((area.h() - thumbHeight) * scroll.offset() / scroll.maxScroll());
        blit(graphics, SCROLLER, new ScreenRectangle(x, thumbY, ScrollArea.BAR_WIDTH, thumbHeight));
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, ScreenRectangle rect) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, rect.x(), rect.y(), rect.w(), rect.h());
    }
}
