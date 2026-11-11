package com.iafenvoy.spb.testmod;

import com.iafenvoy.spb.data.UiSettings;
import com.iafenvoy.spb.network.model.GroupLine;
import com.iafenvoy.spb.network.model.OptionLine;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import com.iafenvoy.spb.screen.layout.LayoutEntry;
import com.iafenvoy.spb.screen.layout.OptionGrid;
import com.iafenvoy.spb.screen.layout.PlanScreenLayout;
import com.iafenvoy.spb.screen.layout.ScreenRectangle;
import com.iafenvoy.spb.screen.widget.ScrollArea;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Pure layout and viewport behaviour; no Minecraft client classes are loaded on the test server.
 */
final class ScreenLayoutTests {
    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("screen_grid_navigation", ScreenLayoutTests::gridNavigation);
        tests.put("screen_hidden_selection", ScreenLayoutTests::hiddenSelection);
        tests.put("screen_compact_layout", ScreenLayoutTests::compactLayout);
        tests.put("screen_scroll_regions", ScreenLayoutTests::scrollRegions);
    }

    private static SelectionSnapshot snapshot(List<OptionLine> options) {
        GroupLine group = new GroupLine("group", Component.literal("Group"), Optional.empty(),
                "distinct", 0, 0, 10, true);
        return new SelectionSnapshot(Identifier.parse("spb_test:layout"), UUID.randomUUID(), 0, "editing", true,
                false, "", Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(group), options,
                List.of(), List.of(), UiSettings.DEFAULT);
    }

    private static OptionLine option(int index, boolean visible, int quantity) {
        String key = "option_" + index;
        return new OptionLine(Identifier.fromNamespaceAndPath("spb_test", key), "group", key, Component.literal(key),
                Optional.empty(), Optional.empty(), quantity, 1, 0, 1, visible, true, false, List.of(), List.of(), List.of());
    }

    private static void gridNavigation(GameTestHelper helper) {
        SelectionSnapshot snapshot = snapshot(IntStream.range(0, 6).mapToObj(index -> option(index, true, 0)).toList());
        OptionGrid wide = OptionGrid.build(snapshot, 400);
        helper.assertValueEqual(wide.nextOption(-1, 1), 1, "focus starts at the first card, skipping the group header");
        helper.assertValueEqual(wide.verticalOption(2, 1), 5, "down keeps the middle column");
        helper.assertValueEqual(wide.verticalOption(5, -1), 2, "up returns to the same column");
        helper.assertValueEqual(wide.nextOption(-1, -1), 6, "left from no focus starts at the final card");
        ScreenRectangle viewport = new ScreenRectangle(30, 40, 400, 100);
        ScreenRectangle card = wide.entries().get(5).onScreen(viewport, 22);
        helper.assertValueEqual(wide.indexAt(viewport, 22, card.x() + 5, card.y() + 5), 5,
                "scrolled hit testing identifies the rendered card");
        helper.assertValueEqual(wide.indexAt(viewport, 22, viewport.right(), card.y()), -1,
                "the right edge is outside the viewport");
        OptionGrid narrow = OptionGrid.build(snapshot, 140);
        helper.assertTrue(narrow.height() > wide.height(), "one column grows content height");
        helper.assertValueEqual(narrow.verticalOption(1, 1), 2, "one-column navigation follows the next row");
        helper.succeed();
    }

    private static void hiddenSelection(GameTestHelper helper) {
        OptionGrid grid = OptionGrid.build(snapshot(List.of(option(0, true, 0), option(1, false, 1), option(2, false, 0))), 400);
        List<LayoutEntry> cards = grid.entries().stream().filter(LayoutEntry::isOption).toList();
        helper.assertValueEqual(cards.size(), 2, "only selected hidden options remain reachable");
        helper.assertValueEqual(cards.getLast().option().key(), "option_1", "hidden selection is retained");
        helper.assertValueEqual(cards.getLast().group().key(), "__attention", "hidden selection moves to the attention group");
        helper.succeed();
    }

    private static void compactLayout(GameTestHelper helper) {
        PlanScreenLayout wide = PlanScreenLayout.create(640, 400, UiSettings.DEFAULT, true, false, 1);
        helper.assertTrue(wide.sidebarWidth() > 0 && wide.presetToggle().isEmpty(), "wide screens use a sidebar");
        helper.assertTrue(wide.presets().right() < wide.options().x(), "the two columns do not overlap");
        PlanScreenLayout narrow = PlanScreenLayout.create(240, 240, UiSettings.DEFAULT, true, false, 2);
        helper.assertTrue(narrow.sidebarWidth() == 0 && !narrow.presetToggle().isEmpty() && narrow.presets().isEmpty(),
                "compact screens initially show only the preset toggle");
        PlanScreenLayout overlay = PlanScreenLayout.create(240, 240, UiSettings.DEFAULT, true, true, 2);
        helper.assertTrue(!overlay.presets().isEmpty() && overlay.presets().right() <= overlay.options().right(),
                "the overlay remains inside the option viewport");
        helper.assertValueEqual(overlay.statusY(), narrow.statusY(), "opening the overlay keeps the footer in place");
        PlanScreenLayout empty = PlanScreenLayout.create(240, 240, UiSettings.DEFAULT, false, true, 0);
        helper.assertTrue(empty.presets().isEmpty() && empty.presetToggle().isEmpty(), "no preset controls without presets");
        UiSettings capped = new UiSettings(100, 300, 200, false);
        PlanScreenLayout fixed = PlanScreenLayout.create(1280, 800, capped, false, false, 1);
        helper.assertValueEqual(fixed.panel().w(), 300, "positive width cap is honoured");
        helper.assertValueEqual(fixed.panel().h(), 200, "positive height cap is honoured");
        helper.succeed();
    }

    private static void scrollRegions(GameTestHelper helper) {
        ScrollArea presets = new ScrollArea();
        ScrollArea options = new ScrollArea();
        presets.configure(new ScreenRectangle(0, 0, 80, 100), 200, 16);
        options.configure(new ScreenRectangle(90, 0, 200, 100), 300, 4);
        helper.assertTrue(presets.scroll(20, 20, 40), "wheel over sidebar scrolls presets");
        helper.assertTrue(!options.scroll(20, 20, 40) && options.offset() == 0, "sidebar scrolling leaves options unchanged");
        helper.assertTrue(!presets.startDrag(79, 150), "scrollbar cannot be grabbed below its viewport");
        helper.assertTrue(presets.startDrag(79, 20), "scrollbar can be grabbed inside its viewport");
        presets.drag(40);
        helper.assertTrue(presets.offset() == 80, "drag scales movement to content height");
        presets.release();
        helper.assertTrue(!presets.drag(60), "release stops dragging");
        options.reveal(new ScreenRectangle(0, 250, 100, 30));
        helper.assertTrue(options.offset() == 180, "keyboard focus reveals the card bottom");
        options.configure(options.bounds(), 40, 4);
        helper.assertTrue(options.offset() == 0, "shrinking content clamps the old offset");
        helper.succeed();
    }
}
