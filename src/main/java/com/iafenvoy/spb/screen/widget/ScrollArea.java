package com.iafenvoy.spb.screen.widget;

import com.iafenvoy.spb.screen.layout.ScreenRectangle;

/**
 * Independent viewport scroll and drag state shared by the two columns.
 */
public final class ScrollArea {
    public static final int BAR_WIDTH = 6;
    private ScreenRectangle bounds = ScreenRectangle.EMPTY;
    private int contentHeight;
    private int maxScroll;
    private double offset;
    private boolean dragging;
    private double dragY;
    private double dragOffset;

    public void configure(ScreenRectangle bounds, int contentHeight, int bottomPadding) {
        this.bounds = bounds;
        this.contentHeight = contentHeight;
        this.maxScroll = Math.max(0, contentHeight - bounds.h() + bottomPadding);
        this.setOffset(this.offset);
        this.dragging = false;
    }

    public ScreenRectangle bounds() {
        return this.bounds;
    }

    public int contentHeight() {
        return this.contentHeight;
    }

    public int maxScroll() {
        return this.maxScroll;
    }

    public double offset() {
        return this.offset;
    }

    public void setOffset(double offset) {
        this.offset = Math.max(0, Math.min(offset, this.maxScroll));
    }

    public boolean scroll(double mouseX, double mouseY, double delta) {
        if (!this.bounds.contains(mouseX, mouseY)) return false;
        this.setOffset(this.offset + delta);
        return true;
    }

    public boolean startDrag(double mouseX, double mouseY) {
        if (this.maxScroll <= 0 || !this.bounds.contains(mouseX, mouseY)
                || mouseX < this.bounds.right() - BAR_WIDTH) return false;
        this.dragging = true;
        this.dragY = mouseY;
        this.dragOffset = this.offset;
        return true;
    }

    public boolean drag(double mouseY) {
        if (!this.dragging) return false;
        this.setOffset(this.dragOffset + (mouseY - this.dragY) * this.contentHeight / Math.max(1, this.bounds.h()));
        return true;
    }

    public void release() {
        this.dragging = false;
    }

    public void reveal(ScreenRectangle entry) {
        if (entry.y() < this.offset) this.setOffset(entry.y());
        else if (entry.bottom() > this.offset + this.bounds.h()) this.setOffset(entry.bottom() - this.bounds.h());
    }
}
