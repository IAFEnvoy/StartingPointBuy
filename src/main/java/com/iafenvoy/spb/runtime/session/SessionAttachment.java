package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.saturn.util.codec.CollectionCodecs;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.iafenvoy.spb.runtime.outcome.FrozenGrant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Player attachment root: at most one active session plus the per-plan completion records. Death
 * copies this attachment, so completed plans are not replayed after respawning. Permanent ledgers
 * discard missing plan holders on load; the active session retains its IDs for recovery.
 */
public final class SessionAttachment {
    public static final MapCodec<SessionAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SelectionSession.CODEC.optionalFieldOf("session").forGetter(attachment -> Optional.ofNullable(attachment.session)),
            CollectionCodecs.map(Plan.CODEC, CompletionRecord.CODEC).optionalFieldOf("completions", Map.of()).forGetter(SessionAttachment::completions),
            CollectionCodecs.map(Plan.CODEC, FrozenGrant.CODEC.listOf()).optionalFieldOf("grants", Map.of()).forGetter(SessionAttachment::grants)
    ).apply(i, SessionAttachment::new));
    private SelectionSession session;
    private final Map<Holder<Plan>, CompletionRecord> completions;
    private final Map<Holder<Plan>, List<FrozenGrant>> grants;

    private SessionAttachment(Optional<SelectionSession> session, Map<Holder<Plan>, CompletionRecord> completions,
                              Map<Holder<Plan>, List<FrozenGrant>> grants) {
        this.session = session.orElse(null);
        this.completions = new LinkedHashMap<>(completions);
        this.grants = new LinkedHashMap<>(grants);
    }

    public SessionAttachment() {
        this.session = null;
        this.completions = new LinkedHashMap<>();
        this.grants = new LinkedHashMap<>();
    }

    @Nullable
    public SelectionSession session() {
        return this.session;
    }

    public void setSession(@NotNull SelectionSession session, HolderLookup.Provider registries) {
        this.captureActiveGrants(registries);
        this.session = session;
    }

    public void clearSession(HolderLookup.Provider registries) {
        this.captureActiveGrants(registries);
        this.session = null;
    }

    /**
     * Also migrates saves written before the independent permanent-grant ledger existed.
     */
    public void captureActiveGrants(HolderLookup.Provider registries) {
        if (this.session != null && this.session.abilitiesActive() && this.session.commit() != null)
            registries.lookupOrThrow(SpbResourceKeys.PLAN)
                    .get(ResourceKey.create(SpbResourceKeys.PLAN, this.session.planId()))
                    .ifPresent(plan -> this.grants.put(plan, List.copyOf(this.session.commit().grants())));
    }

    public Map<Holder<Plan>, List<FrozenGrant>> grants() {
        return this.grants;
    }

    public Map<Holder<Plan>, CompletionRecord> completions() {
        return this.completions;
    }

    public boolean isCompleted(Identifier plan) {
        return this.completions.keySet().stream()
                .anyMatch(holder -> holder.unwrapKey().orElseThrow().identifier().equals(plan));
    }

    public void complete(Holder<Plan> plan, CompletionRecord record) {
        this.completions.put(plan, record);
    }

    public void uncomplete(Holder<Plan> plan) {
        this.completions.remove(plan);
    }
}
