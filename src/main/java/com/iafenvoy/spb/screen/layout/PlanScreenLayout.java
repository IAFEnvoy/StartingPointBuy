package com.iafenvoy.spb.screen.layout;

import com.iafenvoy.spb.data.UiSettings;

/**
 * All fixed screen regions in GUI logical pixels.
 */
public record PlanScreenLayout(ScreenRectangle panel, ScreenRectangle presets, ScreenRectangle options,
                               ScreenRectangle presetToggle, ScreenRectangle close, ScreenRectangle reset,
                               ScreenRectangle confirm, int footerY, int statusY, int sidebarWidth) {
    public static PlanScreenLayout create(int width, int height, UiSettings ui, boolean hasPresets,
                                          boolean presetOverlay, int resourceRows) {
        int availableWidth = Math.max(120, width - 32);
        int availableHeight = Math.max(80, height - 32);
        int panelWidth = ui.maxWidth() > 0 ? Math.min(availableWidth, Math.max(120, ui.maxWidth())) : availableWidth;
        int panelHeight = ui.maxHeight() > 0 ? Math.min(availableHeight, Math.max(80, ui.maxHeight())) : availableHeight;
        ScreenRectangle panel = new ScreenRectangle((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        int bodyWidth = panelWidth - 16;
        int sidebar = 0;
        if (hasPresets) {
            int min = bodyWidth * 20 / 100;
            int max = Math.max(min, bodyWidth * 25 / 100);
            sidebar = Math.min(max, Math.max(ui.presetSidebarWidth(), min));
            sidebar = Math.min(sidebar, Math.max(0, bodyWidth - 110));
            if (sidebar < 56) sidebar = 0;
        }
        boolean toggle = hasPresets && sidebar == 0;
        int bodyTop = panel.y() + (toggle ? 26 : 10);
        int footer = Math.max(bodyTop + 40, panel.bottom() - (46 + resourceRows * 11));
        ScreenRectangle options = new ScreenRectangle(panel.x() + 8 + sidebar, bodyTop + 2,
                bodyWidth - sidebar, footer - bodyTop - 6);
        ScreenRectangle presets = sidebar > 0
                ? new ScreenRectangle(panel.x() + 8, bodyTop + 2, sidebar - 8, options.h())
                : toggle && presetOverlay
                ? new ScreenRectangle(options.x(), options.y(), Math.min(170, options.w()), options.h())
                : ScreenRectangle.EMPTY;
        ScreenRectangle presetToggle = toggle
                ? new ScreenRectangle(panel.right() - 64, panel.y() + 6, 56, 16) : ScreenRectangle.EMPTY;
        int statusY = footer + 7 + resourceRows * 11;
        ScreenRectangle confirm = new ScreenRectangle(panel.right() - 86, statusY + 14, 78, 18);
        ScreenRectangle reset = new ScreenRectangle(confirm.x() - 62, statusY + 14, 56, 18);
        ScreenRectangle close = new ScreenRectangle(Math.max(panel.x() + 8, reset.x() - 86), statusY + 14, 80, 18);
        return new PlanScreenLayout(panel, presets, options, presetToggle, close, reset, confirm, footer, statusY, sidebar);
    }

    public static int resourceWidth(int width, UiSettings ui) {
        int available = Math.max(120, width - 32);
        return (ui.maxWidth() > 0 ? Math.min(available, Math.max(120, ui.maxWidth())) : available) - 16;
    }
}
