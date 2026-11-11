package com.iafenvoy.spb.screen;

import com.iafenvoy.spb.network.model.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Presentation-only text, shared by rendering, resource wrapping and tooltips.
 */
record ScreenText(Font font) {
    Component trim(Component component, int width) {
        if (width <= 0) return Component.empty();
        if (this.font.width(component) <= width) return component;
        int textWidth = width - this.font.width("…");
        if (textWidth < 0) return Component.empty();
        return Component.literal(this.font.plainSubstrByWidth(component.getString(), textWidth) + "…");
    }

    Component cost(SelectionSnapshot snapshot, OptionLine option, int width) {
        MutableComponent costs = Component.empty();
        for (ContributionLine contribution : prices(option)) {
            double value = option.quantity() > 0 ? contribution.total() : contribution.perUnit();
            if (value == 0.0) continue;
            if (!costs.getSiblings().isEmpty()) costs.append(", ");
            costs.append(Component.literal(formatCost(value) + " ").append(resourceName(snapshot, contribution.resourceKey())));
        }
        return this.trim(costs, width);
    }

    List<Component> presetTooltip(PresetLine preset) {
        List<Component> lines = new ArrayList<>();
        lines.add(preset.name().copy().withStyle(Style.EMPTY.withBold(true)));
        preset.description().ifPresent(description -> lines.addAll(this.wrap(description, 180)));
        if (!preset.entries().isEmpty()) {
            if (lines.size() > 1) lines.add(Component.empty());
            int shown = Math.min(8, preset.entries().size());
            lines.addAll(preset.entries().subList(0, shown));
            if (preset.entries().size() > shown) {
                lines.add(Component.translatable("spb.gui.preset.more", preset.entries().size() - shown)
                        .withStyle(Style.EMPTY.withColor(ScreenDrawing.MUTED)));
            }
        }
        return lines;
    }

    List<Component> optionTooltip(SelectionSnapshot snapshot, OptionLine option, @Nullable SelectionPreview preview) {
        List<Component> lines = new ArrayList<>();
        lines.add(option.name().copy().withStyle(Style.EMPTY.withBold(true)));
        option.description().ifPresent(description -> lines.addAll(this.wrap(description, 180)));
        for (ContributionLine contribution : prices(option)) {
            lines.add(Component.translatable("spb.tooltip.price", formatCost(contribution.perUnit()),
                    resourceName(snapshot, contribution.resourceKey()), formatCost(contribution.total())));
        }
        lines.add(Component.translatable("spb.tooltip.quantity", option.quantity(), option.minCount(), option.maxCount(), option.hardMax()));
        if (option.quantity() == 0 && !option.enabled()) {
            lines.add(Component.translatable("spb.tooltip.disabled").withStyle(Style.EMPTY.withColor(ScreenDrawing.ERROR)));
        }
        if (!option.visible()) {
            lines.add(Component.translatable("spb.tooltip.hidden").withStyle(Style.EMPTY.withColor(ScreenDrawing.ERROR)));
        }
        for (Component issue : option.issues())
            lines.add(issue.copy().withStyle(Style.EMPTY.withColor(ScreenDrawing.ERROR)));
        for (Component effect : option.effects())
            lines.add(effect.copy().withStyle(Style.EMPTY.withColor(ScreenDrawing.MUTED)));
        if (preview != null && preview.option().equals(option.id())) {
            lines.add(Component.empty());
            lines.add(Component.translatable("spb.tooltip.preview", preview.quantity()).withStyle(Style.EMPTY.withColor(ScreenDrawing.ACCENT)));
            for (ResourceLine resource : preview.resources()) {
                if (resource.display()) {
                    lines.add(Component.literal(resource.name().getString() + ": " + format(resource.value()))
                            .withStyle(Style.EMPTY.withColor(resource.valid() ? ScreenDrawing.OK : ScreenDrawing.ERROR)));
                }
            }
            if (!preview.allowed() && !preview.failures().isEmpty()) {
                lines.add(preview.failures().getFirst().copy().withStyle(Style.EMPTY.withColor(ScreenDrawing.ERROR)));
            }
        }
        return lines;
    }

    List<List<ResourceLine>> resourceRows(SelectionSnapshot snapshot, int width) {
        List<List<ResourceLine>> rows = new ArrayList<>();
        List<ResourceLine> current = new ArrayList<>();
        int used = 0;
        for (ResourceLine resource : snapshot.resources()) {
            if (!resource.display()) continue;
            int entryWidth = this.resourceWidth(resource);
            if (!current.isEmpty() && used + entryWidth > width) {
                rows.add(List.copyOf(current));
                current.clear();
                used = 0;
            }
            current.add(resource);
            used += entryWidth;
        }
        if (!current.isEmpty()) rows.add(List.copyOf(current));
        return List.copyOf(rows);
    }

    int resourceWidth(ResourceLine resource) {
        return this.font.width(resourceLabel(resource)) + 12;
    }

    static Component resourceLabel(ResourceLine resource) {
        return Component.empty().append(resource.name()).append(" " + format(resource.value()));
    }

    private static Component resourceName(SelectionSnapshot snapshot, String key) {
        return snapshot.resources().stream().filter(resource -> resource.key().equals(key))
                .map(ResourceLine::name).findFirst().orElseGet(() -> Component.literal(key));
    }

    private static List<ContributionLine> prices(OptionLine option) {
        // Stable sorting keeps the configured resource order within spending and refunds.
        return option.contributions().stream()
                .filter(contribution -> contribution.perUnit() != 0.0 || contribution.total() != 0.0)
                .sorted(Comparator.comparing(contribution -> contribution.perUnit() > 0.0))
                .toList();
    }

    private static String formatCost(double contribution) {
        // A price has the opposite sign to the actual resource change.
        return format(-contribution);
    }

    private List<Component> wrap(Component text, int width) {
        List<Component> lines = new ArrayList<>();
        String remaining = text.getString();
        while (!remaining.isEmpty()) {
            String line = this.font.plainSubstrByWidth(remaining, Math.max(8, width));
            if (line.isEmpty()) break;
            lines.add(Component.literal(line));
            remaining = remaining.substring(line.length()).stripLeading();
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) return String.valueOf((long) Math.rint(value));
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
