package com.iafenvoy.spb.screen;

import com.iafenvoy.spb.network.model.ResourceLine;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import com.iafenvoy.spb.screen.layout.PlanScreenLayout;
import com.iafenvoy.spb.screen.layout.ScreenRectangle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource rows are wrapped once per layout update and reused for drawing and hit testing.
 */
final class PlanFooter {
    private final PlanRequests requests;
    private List<List<ResourceLine>> rows = List.of();

    PlanFooter(PlanRequests requests) {
        this.requests = requests;
    }

    int configure(ScreenText text, int width) {
        this.rows = text.resourceRows(this.requests.snapshot(), width);
        return this.rows.size();
    }

    void render(GuiGraphicsExtractor graphics, ScreenText text, PlanScreenLayout layout, boolean closeLocked,
                int mouseX, int mouseY) {
        ScreenRectangle panel = layout.panel();
        graphics.fill(panel.x() + 8, layout.footerY(), panel.right() - 8, layout.footerY() + 1, ScreenDrawing.SEPARATOR);
        int y = layout.footerY() + 4;
        for (List<ResourceLine> row : this.rows) {
            int x = panel.x() + 8;
            for (ResourceLine resource : row) {
                graphics.text(text.font(), ScreenText.resourceLabel(resource), x, y, resource.valid() ? ScreenDrawing.TEXT : ScreenDrawing.ERROR);
                x += text.resourceWidth(resource);
            }
            y += 11;
        }
        SelectionSnapshot snapshot = this.requests.snapshot();
        Component status = Component.empty();
        int color = ScreenDrawing.MUTED;
        if (this.requests.pending()) status = Component.translatable("spb.gui.waiting");
        else if (!snapshot.editable()) {
            status = Component.translatable("spb.state." + snapshot.state());
            color = snapshot.state().equals("completed") ? ScreenDrawing.OK : ScreenDrawing.ACCENT;
        } else if (snapshot.canComplete()) {
            status = Component.translatable("spb.gui.ready");
            color = ScreenDrawing.OK;
        } else if (!snapshot.failures().isEmpty()) {
            Component first = snapshot.failures().getFirst();
            int more = snapshot.failures().size() - 1;
            status = more > 0 ? Component.empty().append(first).append("  (+" + more + ")") : first;
            color = ScreenDrawing.ERROR;
        } else if (!snapshot.issues().isEmpty()) {
            status = Component.translatable("spb.gui.definition_error");
            color = ScreenDrawing.ERROR;
        }
        if (panel.w() - 16 >= 30) {
            graphics.text(text.font(), text.trim(status, panel.w() - 16), panel.x() + 8, layout.statusY(), color);
        }
        ScreenDrawing.button(graphics, text, layout.close(), Component.translatable("spb.gui.save_and_close"), !closeLocked && !this.requests.pending(), mouseX, mouseY);
        ScreenDrawing.button(graphics, text, layout.reset(), Component.translatable(this.requests.needsRefresh() ? "spb.gui.refresh" : "spb.gui.reset"), this.requests.canResetOrRefresh(), mouseX, mouseY);
        ScreenDrawing.button(graphics, text, layout.confirm(), Component.translatable("spb.gui.confirm"), this.requests.canSubmit(), mouseX, mouseY);
    }

    List<Component> tooltip(ScreenText text, PlanScreenLayout layout, boolean closeLocked, int mouseX, int mouseY) {
        if (layout.close().contains(mouseX, mouseY))
            return List.of(Component.translatable(closeLocked ? "spb.gui.close_locked.tooltip" : "spb.gui.save_and_close.tooltip"));
        if (layout.reset().contains(mouseX, mouseY))
            return List.of(Component.translatable(this.requests.needsRefresh() ? "spb.gui.refresh.tooltip" : "spb.gui.reset.tooltip"));
        if (layout.confirm().contains(mouseX, mouseY))
            return List.of(Component.translatable("spb.gui.confirm.tooltip"));
        int relativeY = mouseY - layout.footerY() - 4;
        if (relativeY >= 0 && relativeY / 11 < this.rows.size()) {
            int x = layout.panel().x() + 8;
            for (ResourceLine resource : this.rows.get(relativeY / 11)) {
                int width = text.resourceWidth(resource);
                if (mouseX >= x && mouseX < x + width)
                    return List.of(Component.translatable("spb.gui.resource.tooltip", resource.base(), resource.min(), resource.max()));
                x += width;
            }
        }
        if (mouseY >= layout.statusY() && mouseY < layout.statusY() + 10) {
            List<Component> lines = new ArrayList<>(this.requests.snapshot().failures());
            lines.addAll(this.requests.snapshot().issues());
            return lines;
        }
        return List.of();
    }
}
