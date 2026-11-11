package com.iafenvoy.spb.screen.layout;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.iafenvoy.spb.network.model.GroupLine;
import com.iafenvoy.spb.network.model.OptionLine;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Immutable card geometry, rebuilt only when the snapshot or viewport changes.
 */
public record OptionGrid(List<LayoutEntry> entries, int height) {
    public static final int CARD_HEIGHT = 30;
    public static final int GROUP_HEIGHT = 15;
    private static final int GAP = 6;
    private static final int MIN_CARD_WIDTH = 120;
    private static final int MAX_COLUMNS = 3;
    public static final OptionGrid EMPTY = new OptionGrid(List.of(), 0);

    public static OptionGrid build(SelectionSnapshot snapshot, int viewportWidth) {
        int rowWidth = viewportWidth - 8;
        int columns = Math.max(1, Math.min(MAX_COLUMNS, (rowWidth + GAP) / (MIN_CARD_WIDTH + GAP)));
        int cardWidth = columns == 1 ? rowWidth : Math.max(1, (rowWidth - (columns - 1) * GAP) / columns);
        ListMultimap<String, OptionLine> grouped = LinkedListMultimap.create();
        for (OptionLine option : snapshot.options()) grouped.put(option.groupKey(), option);
        List<LayoutEntry> entries = new ArrayList<>();
        List<OptionLine> attention = new ArrayList<>();
        int bottom = 0;
        for (GroupLine group : snapshot.groups()) {
            List<OptionLine> visible = new ArrayList<>();
            for (OptionLine option : grouped.get(group.key())) {
                if (option.visible()) visible.add(option);
                else if (option.quantity() > 0) attention.add(option);
            }
            if (!visible.isEmpty() || group.visible()) {
                bottom = appendGroup(entries, group, visible, bottom, columns, cardWidth, rowWidth);
            }
        }
        if (!attention.isEmpty()) {
            GroupLine group = new GroupLine("__attention", Component.translatable("spb.gui.needs_attention"),
                    Optional.of(Component.translatable("spb.gui.needs_attention.tooltip")), "distinct",
                    attention.size(), 0, attention.size(), true);
            bottom = appendGroup(entries, group, attention, bottom, columns, cardWidth, rowWidth);
        }
        return new OptionGrid(List.copyOf(entries), bottom);
    }

    private static int appendGroup(List<LayoutEntry> entries, GroupLine group, List<OptionLine> options, int top, int columns, int cardWidth, int rowWidth) {
        entries.add(new LayoutEntry(LayoutEntryKind.GROUP, group, null, new ScreenRectangle(0, top, rowWidth, GROUP_HEIGHT)));
        int gridTop = top + GROUP_HEIGHT + 2;
        for (int index = 0; index < options.size(); index++) {
            ScreenRectangle card = new ScreenRectangle(index % columns * (cardWidth + GAP), gridTop + index / columns * (CARD_HEIGHT + GAP), cardWidth, CARD_HEIGHT);
            entries.add(new LayoutEntry(LayoutEntryKind.OPTION, group, options.get(index), card));
        }
        int rows = (options.size() + columns - 1) / columns;
        return gridTop + rows * CARD_HEIGHT + Math.max(0, rows - 1) * GAP + 4;
    }

    public int indexAt(ScreenRectangle viewport, double scroll, double mouseX, double mouseY) {
        if (!viewport.contains(mouseX, mouseY)) return -1;
        for (int index = 0; index < this.entries.size(); index++)
            if (this.entries.get(index).onScreen(viewport, scroll).contains(mouseX, mouseY))
                return index;
        return -1;
    }

    public int nextOption(int current, int direction) {
        int start = current < 0 ? (direction > 0 ? -1 : this.entries.size()) : current;
        for (int index = start + direction; index >= 0 && index < this.entries.size(); index += direction)
            if (this.entries.get(index).isOption()) return index;
        return -1;
    }

    public int verticalOption(int current, int direction) {
        if (current < 0 || current >= this.entries.size() || !this.entries.get(current).isOption())
            return this.nextOption(current, direction);
        ScreenRectangle origin = this.entries.get(current).bounds();
        int best = current;
        long bestScore = Long.MAX_VALUE;
        for (int index = 0; index < this.entries.size(); index++) {
            LayoutEntry entry = this.entries.get(index);
            int dy = entry.bounds().y() - origin.y();
            if (!entry.isOption() || (direction > 0 ? dy <= 0 : dy >= 0)) continue;
            int dx = entry.bounds().x() + entry.bounds().w() / 2 - origin.x() - origin.w() / 2;
            long score = (long) Math.abs(dy) * 10_000L + Math.abs(dx);
            if (score < bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return best;
    }
}
