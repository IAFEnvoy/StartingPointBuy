package com.iafenvoy.spb.screen;

import com.iafenvoy.spb.network.model.PresetLine;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import com.iafenvoy.spb.screen.layout.ScreenRectangle;
import com.iafenvoy.spb.screen.widget.ScrollArea;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Sidebar and compact-screen overlay share exactly the same rendering and hit testing.
 */
final class PresetPanel {
    private static final int ROW_HEIGHT = 20;
    private static final int HEADING_HEIGHT = 14;
    private final PlanRequests requests;
    private final ScrollArea scroll = new ScrollArea();
    private boolean overlay;

    PresetPanel(PlanRequests requests) {
        this.requests = requests;
    }

    ScrollArea scroll() {
        return this.scroll;
    }

    boolean overlay() {
        return this.overlay;
    }

    void setOverlay(boolean overlay) {
        this.overlay = overlay;
    }

    void configure(ScreenRectangle bounds) {
        this.scroll.configure(bounds, this.requests.snapshot().presets().size() * ROW_HEIGHT, 16);
    }

    void render(GuiGraphicsExtractor graphics, ScreenText text, int mouseX, int mouseY) {
        ScreenRectangle area = this.scroll.bounds();
        if (area.isEmpty()) return;
        if (this.overlay)
            ScreenDrawing.panel(graphics, new ScreenRectangle(area.x() - 4, area.y() - 4, area.w() + 8, area.h() + 8));
        ScreenDrawing.clip(graphics, area);
        graphics.text(text.font(), Component.translatable("spb.gui.presets"), area.x() + 2, area.y(), ScreenDrawing.MUTED);
        SelectionSnapshot snapshot = this.requests.snapshot();
        if (snapshot.presetKey().isEmpty() && !snapshot.presets().isEmpty()) {
            Component custom = Component.translatable("spb.gui.custom");
            graphics.text(text.font(), custom, area.right() - 2 - text.font().width(custom), area.y(), ScreenDrawing.ACCENT);
        }
        graphics.disableScissor();
        ScreenDrawing.clip(graphics, new ScreenRectangle(area.x(), area.y() + HEADING_HEIGHT,
                area.w(), area.h() - HEADING_HEIGHT));
        for (int index = 0; index < snapshot.presets().size(); index++) {
            PresetLine preset = snapshot.presets().get(index);
            ScreenRectangle row = this.row(index);
            if (row.y() + ROW_HEIGHT < area.y() || row.y() > area.bottom()) continue;
            ScreenDrawing.buttonBackground(graphics, row, true, preset.matches() || row.contains(mouseX, mouseY));
            int iconX = area.x() + 3;
            int textX = iconX;
            if (preset.icon().isPresent()) {
                ScreenDrawing.icon(graphics, preset.icon(), iconX, row.y() + 1);
                textX += 19;
            }
            graphics.text(text.font(), text.trim(preset.name(), row.right() - textX - 4), textX, row.y() + 5,
                    preset.matches() ? ScreenDrawing.HEADER : ScreenDrawing.TEXT);
        }
        graphics.disableScissor();
        ScreenDrawing.scrollbar(graphics, this.scroll);
    }

    boolean click(double mouseX, double mouseY) {
        if (!this.scroll.bounds().contains(mouseX, mouseY)) return false;
        if (this.scroll.startDrag(mouseX, mouseY)) return true;
        PresetLine preset = this.at(mouseX, mouseY);
        if (preset != null) {
            this.requests.applyPreset(preset.key());
            this.overlay = false;
        }
        return true;
    }

    @Nullable PresetLine at(double mouseX, double mouseY) {
        if (!this.scroll.bounds().contains(mouseX, mouseY) || mouseY < this.scroll.bounds().y() + HEADING_HEIGHT)
            return null;
        double y = mouseY - this.scroll.bounds().y() - HEADING_HEIGHT + (int) this.scroll.offset();
        if (y < 0) return null;
        int index = (int) (y / ROW_HEIGHT);
        if (index >= this.requests.snapshot().presets().size() || !this.row(index).contains(mouseX, mouseY))
            return null;
        return this.requests.snapshot().presets().get(index);
    }

    private ScreenRectangle row(int index) {
        ScreenRectangle area = this.scroll.bounds();
        return new ScreenRectangle(area.x(), area.y() + HEADING_HEIGHT + index * ROW_HEIGHT - (int) this.scroll.offset(),
                area.w() - ScrollArea.BAR_WIDTH, ROW_HEIGHT - 2);
    }
}
