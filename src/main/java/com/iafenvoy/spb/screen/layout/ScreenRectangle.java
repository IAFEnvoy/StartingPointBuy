package com.iafenvoy.spb.screen.layout;

public record ScreenRectangle(int x, int y, int w, int h) {
    public static final ScreenRectangle EMPTY = new ScreenRectangle(0, 0, 0, 0);

    public boolean contains(double mouseX, double mouseY) {
        return this.w > 0 && this.h > 0 && mouseX >= this.x && mouseX < this.x + this.w
                && mouseY >= this.y && mouseY < this.y + this.h;
    }

    public boolean isEmpty() {
        return this.w <= 0 || this.h <= 0;
    }

    public int right() {
        return this.x + this.w;
    }

    public int bottom() {
        return this.y + this.h;
    }

    public ScreenRectangle move(int dx, int dy) {
        return new ScreenRectangle(this.x + dx, this.y + dy, this.w, this.h);
    }
}
