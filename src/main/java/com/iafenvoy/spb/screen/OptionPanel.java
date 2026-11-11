package com.iafenvoy.spb.screen;

import com.iafenvoy.spb.network.model.GroupLine;
import com.iafenvoy.spb.network.model.OptionLine;
import com.iafenvoy.spb.screen.layout.LayoutEntry;
import com.iafenvoy.spb.screen.layout.OptionGrid;
import com.iafenvoy.spb.screen.layout.QuantityControls;
import com.iafenvoy.spb.screen.layout.ScreenRectangle;
import com.iafenvoy.spb.screen.widget.ScrollArea;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Option cards, keyboard focus and scroll anchoring use the same immutable grid geometry.
 */
final class OptionPanel {
    private final PlanRequests requests;
    private final ScrollArea scroll = new ScrollArea();
    private OptionGrid grid = OptionGrid.EMPTY;
    private int focusIndex = -1;

    OptionPanel(PlanRequests requests) {
        this.requests = requests;
    }

    ScrollArea scroll() {
        return this.scroll;
    }

    void configure(ScreenRectangle bounds, boolean resetFocus) {
        String anchor = "";
        int anchorOffset = 0;
        if (!this.scroll.bounds().isEmpty()) {
            for (LayoutEntry entry : this.grid.entries()) {
                if (entry.isOption() && entry.bounds().bottom() > (int) this.scroll.offset()) {
                    anchor = entry.option().key();
                    anchorOffset = entry.bounds().y() - (int) this.scroll.offset();
                    break;
                }
            }
        }
        this.grid = OptionGrid.build(this.requests.snapshot(), bounds.w());
        this.scroll.configure(bounds, this.grid.height(), 4);
        if (resetFocus) this.focusIndex = -1;
        if (anchor.isEmpty()) this.scroll.setOffset(0);
        else {
            for (LayoutEntry entry : this.grid.entries()) {
                if (entry.isOption() && entry.option().key().equals(anchor)) {
                    this.scroll.setOffset(entry.bounds().y() - anchorOffset);
                    break;
                }
            }
        }
    }

    void render(GuiGraphicsExtractor graphics, ScreenText text, int mouseX, int mouseY) {
        ScreenRectangle area = this.scroll.bounds();
        ScreenDrawing.clip(graphics, area);
        for (int index = 0; index < this.grid.entries().size(); index++) {
            LayoutEntry entry = this.grid.entries().get(index);
            ScreenRectangle row = entry.onScreen(area, this.scroll.offset());
            if (row.bottom() < area.y() || row.y() > area.bottom()) continue;
            if (entry.isOption())
                this.drawCard(graphics, text, entry.option(), row, index == this.focusIndex, mouseX, mouseY);
            else this.drawGroup(graphics, text, entry.group(), row);
        }
        if (this.grid.entries().isEmpty()) {
            graphics.text(text.font(), Component.translatable("spb.gui.no_options"), area.x() + 2, area.y() + 4, ScreenDrawing.MUTED);
        }
        graphics.disableScissor();
        ScreenDrawing.scrollbar(graphics, this.scroll);
    }

    private void drawGroup(GuiGraphicsExtractor graphics, ScreenText text, GroupLine group, ScreenRectangle row) {
        // Headers extend two pixels past the grid's cards, as in the original presentation.
        int right = row.right() + 2;
        graphics.text(text.font(), text.trim(group.title(), row.w() - 60), row.x() + 2, row.y() + 2,
                group.visible() ? ScreenDrawing.HEADER : ScreenDrawing.MUTED);
        Component count = group.visible() ? Component.literal(group.count() + " / " + group.max())
                : Component.translatable("spb.gui.hidden");
        graphics.text(text.font(), count, right - 3 - text.font().width(count), row.y() + 2,
                group.min() > 0 && group.count() < group.min() ? ScreenDrawing.ERROR : ScreenDrawing.MUTED);
        graphics.fill(row.x() + 2, row.bottom() - 2, right - 2, row.bottom() - 1, ScreenDrawing.SEPARATOR);
    }

    private void drawCard(GuiGraphicsExtractor graphics, ScreenText text, OptionLine option, ScreenRectangle row,
                          boolean focused, int mouseX, int mouseY) {
        boolean hover = row.contains(mouseX, mouseY);
        boolean selected = option.quantity() > 0;
        boolean invalid = !option.issues().isEmpty();
        boolean active = option.visible() && (option.enabled() || selected);
        ScreenDrawing.buttonBackground(graphics, row, active, hover || selected);
        if (invalid) graphics.outline(row.x() + 1, row.y() + 1, row.w() - 2, row.h() - 2, ScreenDrawing.ERROR);
        if (focused) graphics.outline(row.x() + 2, row.y() + 2, row.w() - 4, row.h() - 4, ScreenDrawing.HEADER);
        if (option.icon().isPresent()) {
            ScreenDrawing.slot(graphics, row.x() + 4, row.y() + 6);
            ScreenDrawing.icon(graphics, option.icon(), row.x() + 5, row.y() + 7);
        }
        QuantityControls controls = QuantityControls.of(row, option.quantity(), text.font());
        int textX = row.x() + 25;
        int textRight = option.repeatable() ? controls.minus().x() - 6 : row.right() - 27;
        int textWidth = Math.max(0, textRight - textX);
        int nameColor = !option.visible() ? ScreenDrawing.MUTED : invalid ? ScreenDrawing.ERROR
                : option.enabled() || selected ? ScreenDrawing.TEXT : ScreenDrawing.DISABLED;
        graphics.text(text.font(), text.trim(option.name(), textWidth), textX, row.y() + 5, nameColor);
        Component detail = invalid ? text.trim(option.issues().getFirst(), textWidth)
                : text.cost(this.requests.snapshot(), option, textWidth);
        if (text.font().width(detail) > 0) {
            graphics.text(text.font(), detail, textX, row.y() + 16, invalid ? ScreenDrawing.ERROR : ScreenDrawing.MUTED);
        }
        if (option.repeatable()) this.drawControls(graphics, text, controls, option, mouseX, mouseY);
        else ScreenDrawing.checkbox(graphics, selected, hover, row.right() - 23, row.y() + 6);
    }

    private void drawControls(GuiGraphicsExtractor graphics, ScreenText text, QuantityControls controls, OptionLine option,
                              int mouseX, int mouseY) {
        boolean selected = option.quantity() > 0;
        ScreenDrawing.button(graphics, text, controls.minus(), Component.literal("-"), selected, mouseX, mouseY);
        ScreenDrawing.button(graphics, text, controls.plus(), Component.literal("+"), canIncrease(option), mouseX, mouseY);
        graphics.centeredText(text.font(), Component.literal(String.valueOf(option.quantity())),
                controls.count().x() + controls.count().w() / 2, controls.count().y() + 4,
                selected ? ScreenDrawing.HEADER : ScreenDrawing.MUTED);
    }

    boolean click(ScreenText text, double mouseX, double mouseY) {
        if (!this.scroll.bounds().contains(mouseX, mouseY)) return false;
        if (this.scroll.startDrag(mouseX, mouseY)) return true;
        int index = this.grid.indexAt(this.scroll.bounds(), this.scroll.offset(), mouseX, mouseY);
        if (index < 0 || !this.grid.entries().get(index).isOption()) return false;
        this.focusIndex = index;
        if (!this.requests.canEdit()) return true;
        LayoutEntry entry = this.grid.entries().get(index);
        OptionLine option = entry.option();
        if (option.repeatable()) {
            QuantityControls controls = QuantityControls.of(entry.onScreen(this.scroll.bounds(), this.scroll.offset()), option.quantity(), text.font());
            if (controls.minus().contains(mouseX, mouseY)) {
                if (option.quantity() > 0) this.requests.setQuantity(option, option.quantity() - 1);
                return true;
            }
            if (controls.plus().contains(mouseX, mouseY)) {
                if (canIncrease(option)) this.requests.setQuantity(option, option.quantity() + 1);
                return true;
            }
            if (controls.count().contains(mouseX, mouseY)) return true;
        }
        this.toggle(option, true);
        return true;
    }

    @Nullable OptionLine at(double mouseX, double mouseY) {
        int index = this.grid.indexAt(this.scroll.bounds(), this.scroll.offset(), mouseX, mouseY);
        return index < 0 ? null : this.grid.entries().get(index).option();
    }

    void hover(ScreenText text, double mouseX, double mouseY) {
        int index = this.grid.indexAt(this.scroll.bounds(), this.scroll.offset(), mouseX, mouseY);
        if (!this.requests.canEdit() || index < 0) {
            this.requests.clearPreview();
            return;
        }
        LayoutEntry entry = this.grid.entries().get(index);
        OptionLine option = entry.option();
        int target = -1;
        if (option != null && option.repeatable()) {
            QuantityControls controls = QuantityControls.of(entry.onScreen(this.scroll.bounds(), this.scroll.offset()), option.quantity(), text.font());
            if (controls.plus().contains(mouseX, mouseY) && canIncrease(option)) target = option.quantity() + 1;
            else if (controls.minus().contains(mouseX, mouseY) && option.quantity() > 0) target = option.quantity() - 1;
        }
        if (target < 0) this.requests.clearPreview();
        else this.requests.requestPreview(option, target);
    }

    boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case GLFW_KEY_RIGHT, GLFW_KEY_LEFT -> {
                this.focusIndex = this.grid.nextOption(this.focusIndex, event.key() == GLFW_KEY_RIGHT ? 1 : -1);
                this.revealFocus();
            }
            case GLFW_KEY_DOWN, GLFW_KEY_UP -> {
                this.focusIndex = this.grid.verticalOption(this.focusIndex, event.key() == GLFW_KEY_DOWN ? 1 : -1);
                this.revealFocus();
            }
            case GLFW_KEY_ENTER, GLFW_KEY_SPACE -> {
                if (this.focusIndex < 0 || this.focusIndex >= this.grid.entries().size()) return false;
                OptionLine option = this.grid.entries().get(this.focusIndex).option();
                if (option != null) this.toggle(option, false);
            }
            case GLFW_KEY_PAGE_DOWN, GLFW_KEY_PAGE_UP ->
                    this.scroll.setOffset(this.scroll.offset() + (event.key() == GLFW_KEY_PAGE_DOWN ? 1 : -1) * this.scroll.bounds().h());
            default -> {
                return false;
            }
        }
        this.requests.clearPreview();
        return true;
    }

    private void revealFocus() {
        if (this.focusIndex >= 0) this.scroll.reveal(this.grid.entries().get(this.focusIndex).bounds());
    }

    private void toggle(OptionLine option, boolean clearAll) {
        if (option.quantity() > 0) {
            this.requests.setQuantity(option, clearAll || !option.repeatable() ? 0 : option.quantity() - 1);
        } else if (option.visible() && option.enabled()) this.requests.setQuantity(option, 1);
    }

    private static boolean canIncrease(OptionLine option) {
        return option.quantity() < option.maxCount() && (option.enabled() || option.quantity() > 0);
    }
}
