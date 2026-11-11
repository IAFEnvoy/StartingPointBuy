package com.iafenvoy.spb.screen.layout;

import com.iafenvoy.spb.network.model.GroupLine;
import com.iafenvoy.spb.network.model.OptionLine;
import org.jspecify.annotations.Nullable;

/**
 * Bounds are relative to the option viewport, before applying its scroll offset.
 */
public record LayoutEntry(LayoutEntryKind kind, GroupLine group, @Nullable OptionLine option, ScreenRectangle bounds) {
    public boolean isOption() {
        return this.kind == LayoutEntryKind.OPTION;
    }

    public ScreenRectangle onScreen(ScreenRectangle viewport, double scroll) {
        return this.bounds.move(viewport.x(), viewport.y() - (int) scroll);
    }
}
