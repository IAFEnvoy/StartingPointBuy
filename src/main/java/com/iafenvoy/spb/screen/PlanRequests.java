package com.iafenvoy.spb.screen;

import com.iafenvoy.spb.network.ClientNetworkHandler;
import com.iafenvoy.spb.network.model.OptionLine;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.network.model.SelectionPreview;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Client request state. Server snapshots remain the sole source of quantities and validity.
 */
final class PlanRequests {
    private static final long TIMEOUT_MS = 4_000L;
    private SelectionSnapshot snapshot;
    private boolean pending;
    private long pendingSince;
    @Nullable
    private SelectionPreview preview;
    private String previewKey = "";

    PlanRequests(SelectionSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    SelectionSnapshot snapshot() {
        return this.snapshot;
    }

    boolean pending() {
        return this.pending;
    }

    boolean canEdit() {
        return this.snapshot.editable() && !this.pending;
    }

    boolean canSubmit() {
        return this.canEdit() && this.snapshot.canComplete();
    }

    boolean needsRefresh() {
        return this.snapshot.state().equals("needs_refresh");
    }

    boolean canResetOrRefresh() {
        return !this.pending && (this.snapshot.editable() || this.needsRefresh());
    }

    @Nullable SelectionPreview preview() {
        return this.preview;
    }

    boolean accepts(SelectionSnapshot next) {
        return !next.sessionId().equals(this.snapshot.sessionId()) || next.revision() >= this.snapshot.revision();
    }

    void accept(SelectionSnapshot next) {
        this.snapshot = next;
        this.pending = false;
        this.clearPreview();
    }

    void tick() {
        if (this.pending && Util.getMillis() - this.pendingSince > TIMEOUT_MS) this.pending = false;
    }

    void close() {
        if (!this.pending) this.send(SelectionIntent.close(this.snapshot.sessionId(), this.snapshot.revision()));
    }

    void resetOrRefresh() {
        if (!this.canResetOrRefresh()) return;
        this.send(this.needsRefresh()
                ? SelectionIntent.refresh(this.snapshot.sessionId(), this.snapshot.revision())
                : SelectionIntent.reset(this.snapshot.sessionId(), this.snapshot.revision()));
    }

    void submit() {
        if (this.canSubmit()) this.send(SelectionIntent.submit(this.snapshot.sessionId(), this.snapshot.revision()));
    }

    void applyPreset(String key) {
        if (this.canEdit())
            this.send(SelectionIntent.applyPreset(this.snapshot.sessionId(), this.snapshot.revision(), key));
    }

    void setQuantity(OptionLine option, int quantity) {
        if (this.canEdit() && quantity >= 0 && quantity <= option.hardMax()) {
            this.send(SelectionIntent.setQuantity(this.snapshot.sessionId(), this.snapshot.revision(), option.id(), quantity));
        }
    }

    void requestPreview(OptionLine option, int quantity) {
        String key = option.id() + ":" + quantity;
        if (!this.canEdit() || key.equals(this.previewKey)) return;
        this.previewKey = key;
        this.preview = null;
        ClientNetworkHandler.sendIntent(SelectionIntent.preview(this.snapshot.sessionId(), this.snapshot.revision(), option.id(), quantity));
    }

    void acceptPreview(SelectionPreview next) {
        if ((next.option() + ":" + next.quantity()).equals(this.previewKey)) this.preview = next;
    }

    void clearPreview() {
        this.previewKey = "";
        this.preview = null;
    }

    private void send(SelectionIntent intent) {
        this.pending = true;
        this.pendingSince = Util.getMillis();
        this.clearPreview();
        ClientNetworkHandler.sendIntent(intent);
    }
}
