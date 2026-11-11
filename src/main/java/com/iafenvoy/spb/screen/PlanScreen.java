package com.iafenvoy.spb.screen;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.network.ClientNetworkHandler;
import com.iafenvoy.spb.network.model.*;
import com.iafenvoy.spb.screen.layout.PlanScreenLayout;
import com.iafenvoy.spb.screen.layout.ScreenRectangle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Coordinates the server-backed selection screen. Regions own their drawing, hit testing and local
 * interaction state; the screen owns lifecycle, closing rules and event routing.
 */
public final class PlanScreen extends Screen {
    private final PlanRequests requests;
    private final PresetPanel presets;
    private final OptionPanel options;
    private final PlanFooter footer;
    private ScreenText text;
    private PlanScreenLayout layout;
    private boolean serverReleased;

    public PlanScreen(SelectionSnapshot snapshot) {
        super(snapshot.title().orElseGet(Component::empty));
        this.requests = new PlanRequests(snapshot);
        this.presets = new PresetPanel(this.requests);
        this.options = new OptionPanel(this.requests);
        this.footer = new PlanFooter(this.requests);
        StartingPointBuy.LOGGER.debug("Opening starting-point screen for {}", snapshot.planId());
    }

    @Override
    public @NonNull Component getTitle() {
        return this.requests.snapshot().title().orElseGet(Component::empty);
    }

    public boolean accept(SelectionSnapshot next) {
        if (!this.requests.accepts(next)) return false;
        this.requests.accept(next);
        this.rebuildLayout(true);
        return true;
    }

    public void acceptPreview(SelectionPreview next) {
        this.requests.acceptPreview(next);
    }

    public boolean isCloseLocked() {
        SelectionSnapshot snapshot = this.requests.snapshot();
        return !this.serverReleased && !snapshot.ui().allowCloseBeforeComplete()
                && (snapshot.editable() || snapshot.state().equals("needs_refresh"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.isCloseLocked();
    }

    @Override
    public void onClose() {
        if (!this.isCloseLocked()) this.requests.close();
    }

    public void releaseFromServer() {
        this.serverReleased = true;
        this.minecraft.setScreen(null);
    }

    @Override
    public void removed() {
        if (!this.serverReleased && this.minecraft.getConnection() != null) {
            SelectionSnapshot snapshot = this.requests.snapshot();
            ClientNetworkHandler.sendIntent(SelectionIntent.close(snapshot.sessionId(), snapshot.revision()));
        }
        super.removed();
    }

    @Override
    protected void init() {
        this.text = new ScreenText(this.font);
        this.rebuildLayout(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        this.requests.tick();
    }

    private void rebuildLayout(boolean resetFocus) {
        SelectionSnapshot snapshot = this.requests.snapshot();
        int rows = this.footer.configure(this.text, PlanScreenLayout.resourceWidth(this.width, snapshot.ui()));
        this.layout = PlanScreenLayout.create(this.width, this.height, snapshot.ui(), !snapshot.presets().isEmpty(),
                this.presets.overlay(), rows);
        if (this.layout.sidebarWidth() > 0 || snapshot.presets().isEmpty()) this.presets.setOverlay(false);
        this.presets.configure(this.layout.presets());
        this.options.configure(this.layout.options(), resetFocus);
        this.requests.clearPreview();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ScreenRectangle panel = this.layout.panel();
        ScreenDrawing.panel(graphics, panel);
        if (this.layout.sidebarWidth() > 0) {
            ScreenRectangle area = this.layout.options();
            graphics.fill(area.x() - 4, area.y(), area.x() - 3, area.bottom() + 2, ScreenDrawing.SEPARATOR);
        }
        this.requests.snapshot().title().ifPresent(title -> graphics.centeredText(this.font,
                this.text.trim(title, panel.w() - 20), panel.x() + panel.w() / 2, Math.max(4, panel.y() - 12), ScreenDrawing.HEADER));
        if (!this.layout.presetToggle().isEmpty()) {
            ScreenDrawing.button(graphics, this.text, this.layout.presetToggle(), Component.translatable("spb.gui.presets"),
                    true, mouseX, mouseY);
        }
        this.options.render(graphics, this.text, mouseX, mouseY);
        this.presets.render(graphics, this.text, mouseX, mouseY);
        this.footer.render(graphics, this.text, this.layout, this.isCloseLocked(), mouseX, mouseY);
        List<Component> tooltip = this.tooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) graphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
    }

    private List<Component> tooltip(int mouseX, int mouseY) {
        PresetLine preset = this.presets.at(mouseX, mouseY);
        if (preset != null) return this.text.presetTooltip(preset);
        if (!this.presets.overlay() || !this.layout.presets().contains(mouseX, mouseY)) {
            OptionLine option = this.options.at(mouseX, mouseY);
            if (option != null)
                return this.text.optionTooltip(this.requests.snapshot(), option, this.requests.preview());
        }
        return this.footer.tooltip(this.text, this.layout, this.isCloseLocked(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double x = event.x(), y = event.y();
        if (this.layout.presetToggle().contains(x, y)) {
            this.presets.setOverlay(!this.presets.overlay());
            this.rebuildLayout(false);
            return true;
        }
        boolean overlay = this.presets.overlay();
        if (this.presets.click(x, y)) {
            if (overlay != this.presets.overlay()) this.rebuildLayout(false);
            return true;
        }
        if (overlay && this.layout.options().contains(x, y)) {
            this.presets.setOverlay(false);
            this.rebuildLayout(false);
            return true;
        }
        if (this.layout.close().contains(x, y)) {
            this.onClose();
            return true;
        }
        if (this.layout.confirm().contains(x, y)) {
            this.requests.submit();
            return true;
        }
        if (this.layout.reset().contains(x, y)) {
            this.requests.resetOrRefresh();
            return true;
        }
        return this.options.click(this.text, x, y) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean handled = this.presets.scroll().scroll(mouseX, mouseY, -scrollY * 14)
                || this.options.scroll().scroll(mouseX, mouseY, -scrollY * 22);
        if (handled) this.requests.clearPreview();
        return handled || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        boolean handled = this.presets.scroll().drag(event.y()) || this.options.scroll().drag(event.y());
        if (handled) this.requests.clearPreview();
        return handled || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.presets.scroll().release();
        this.options.scroll().release();
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (this.presets.overlay()) this.requests.clearPreview();
        else this.options.hover(this.text, mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int digit = event.getDigit();
        if (digit >= 1 && digit <= this.requests.snapshot().presets().size()) {
            this.requests.applyPreset(this.requests.snapshot().presets().get(digit - 1).key());
            return true;
        }
        if (event.isConfirmation() && event.hasControlDown()) {
            this.requests.submit();
            return true;
        }
        return this.options.keyPressed(event) || super.keyPressed(event);
    }
}
