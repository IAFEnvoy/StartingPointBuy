package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.saturn.util.codec.CollectionCodecs;
import com.iafenvoy.spb.runtime.outcome.FrozenTask;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Server-authoritative session data. All mutations happen on the server thread; the client only ever
 * sees snapshots.
 *
 * <p>Resources are persisted as plain identifier/value pairs rather than through Saturn's
 * registry-aware holder codec, so removing a resource from a data pack cannot invalidate the whole
 * session. Values are materialised into a
 * {@link com.iafenvoy.saturn.attachment.ResourceHolderAttachment} for every evaluation and commit,
 * which keeps Saturn's resource model authoritative for the draft.
 */
public final class SelectionSession {
    // Connection-local UI state: never persisted or copied as invulnerability across a login.
    private boolean screenOpen;
    private boolean allowCloseBeforeComplete;

    public static final int CURRENT_FORMAT = 1;

    private final int formatVersion;
    private final UUID id;
    private final Identifier planId;
    private String fingerprint;
    private int revision;
    private SessionState state;
    private final Object2IntMap<Identifier> selections;
    @Nullable
    private String presetKey;
    private final Object2DoubleMap<String> inputs;
    private final Object2DoubleMap<Identifier> baseline;
    private final Object2DoubleMap<Identifier> draft;
    private final Object2DoubleMap<Identifier> realSnapshot;
    private final Object2DoubleMap<Identifier> committed;
    @Nullable
    private CommitRecord commit;
    private final List<FrozenTask> tasks;
    private boolean abilitiesActive;
    private final long createdGameTime;
    private long updatedGameTime;

    public boolean screenOpen() {
        return this.screenOpen;
    }

    public void setScreenOpen(boolean value) {
        this.screenOpen = value;
    }

    public boolean allowCloseBeforeComplete() {
        return this.allowCloseBeforeComplete;
    }

    public void setAllowCloseBeforeComplete(boolean value) {
        this.allowCloseBeforeComplete = value;
    }

    public static final Codec<SelectionSession> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format_version", CURRENT_FORMAT).forGetter(SelectionSession::formatVersion),
            UUIDUtil.CODEC.fieldOf("id").forGetter(SelectionSession::id),
            Identifier.CODEC.fieldOf("plan").forGetter(SelectionSession::planId),
            Codec.STRING.optionalFieldOf("fingerprint", "").forGetter(SelectionSession::fingerprint),
            Codec.INT.optionalFieldOf("revision", 0).forGetter(SelectionSession::revision),
            SessionState.CODEC.optionalFieldOf("state", SessionState.EDITING).forGetter(SelectionSession::state),
            SelectionState.CODEC.optionalFieldOf("selection", SelectionState.EMPTY).forGetter(SelectionSession::selectionState),
            CollectionCodecs.doubleMap(Codec.STRING).optionalFieldOf("inputs", Object2DoubleMaps.emptyMap()).forGetter(SelectionSession::inputs),
            ResourceState.CODEC.optionalFieldOf("resources", ResourceState.EMPTY).forGetter(SelectionSession::resourceState),
            CommitRecord.CODEC.optionalFieldOf("commit").forGetter(session -> Optional.ofNullable(session.commit)),
            FrozenTask.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(SelectionSession::tasksList),
            Codec.BOOL.optionalFieldOf("abilities_active", false).forGetter(SelectionSession::abilitiesActive),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(SelectionSession::createdGameTime),
            Codec.LONG.optionalFieldOf("updated", 0L).forGetter(SelectionSession::updatedGameTime)
    ).apply(i, SelectionSession::new));

    public SelectionSession(UUID id, Identifier planId, long gameTime) {
        this(CURRENT_FORMAT, id, planId, "", 0, SessionState.EDITING, SelectionState.EMPTY, Object2DoubleMaps.emptyMap(), ResourceState.EMPTY,
                Optional.empty(), List.of(), false, gameTime, gameTime);
    }

    private SelectionSession(int formatVersion, UUID id, Identifier planId, String fingerprint, int revision, SessionState state,
                             SelectionState selection, Object2DoubleMap<String> inputs, ResourceState resources,
                             Optional<CommitRecord> commit, List<FrozenTask> tasks, boolean abilitiesActive,
                             long createdGameTime, long updatedGameTime) {
        this.formatVersion = formatVersion;
        this.id = id;
        this.planId = planId;
        this.fingerprint = fingerprint;
        this.revision = Math.max(0, revision);
        this.state = state;
        this.selections = new Object2IntOpenHashMap<>(selection.selections());
        this.presetKey = selection.presetKey().orElse(null);
        this.inputs = new Object2DoubleOpenHashMap<>(inputs);
        this.baseline = ResourceState.toMap(resources.baseline());
        this.draft = ResourceState.toMap(resources.draft());
        this.realSnapshot = new Object2DoubleOpenHashMap<>(resources.realSnapshot());
        this.committed = ResourceState.toMap(resources.committed());
        this.commit = commit.orElse(null);
        this.tasks = new ArrayList<>(tasks);
        this.abilitiesActive = abilitiesActive;
        this.createdGameTime = createdGameTime;
        this.updatedGameTime = updatedGameTime;
    }

    public int formatVersion() {
        return this.formatVersion;
    }

    public UUID id() {
        return this.id;
    }

    public Identifier planId() {
        return this.planId;
    }

    public String fingerprint() {
        return this.fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public int revision() {
        return this.revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public SessionState state() {
        return this.state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public Object2IntMap<Identifier> selections() {
        return this.selections;
    }

    public void setSelections(Object2IntMap<Identifier> selections) {
        this.selections.clear();
        this.selections.putAll(selections);
    }

    public Optional<String> presetKey() {
        return Optional.ofNullable(this.presetKey);
    }

    public void setPresetKey(@Nullable String presetKey) {
        this.presetKey = presetKey;
    }

    public SelectionState selectionState() {
        return new SelectionState(this.selections, this.presetKey());
    }

    public Object2DoubleMap<String> inputs() {
        return this.inputs;
    }

    public void setInputs(Object2DoubleMap<String> inputs) {
        this.inputs.clear();
        this.inputs.putAll(inputs);
    }

    public Map<Identifier, Double> baseline() {
        return this.baseline;
    }

    public void setBaseline(Object2DoubleMap<Identifier> baseline) {
        this.baseline.clear();
        this.baseline.putAll(baseline);
    }

    public Object2DoubleMap<Identifier> draft() {
        return this.draft;
    }

    public void setDraft(Object2DoubleMap<Identifier> draft) {
        this.draft.clear();
        this.draft.putAll(draft);
    }

    public Object2DoubleMap<Identifier> realSnapshot() {
        return this.realSnapshot;
    }

    public void setRealSnapshot(Object2DoubleMap<Identifier> realSnapshot) {
        this.realSnapshot.clear();
        this.realSnapshot.putAll(realSnapshot);
    }

    public Object2DoubleMap<Identifier> committed() {
        return this.committed;
    }

    public void setCommitted(Object2DoubleMap<Identifier> committed) {
        this.committed.clear();
        this.committed.putAll(committed);
    }

    public ResourceState resourceState() {
        return new ResourceState(ResourceState.toList(this.baseline), ResourceState.toList(this.draft),
                ResourceState.toList(this.committed), Map.copyOf(this.realSnapshot));
    }

    @Nullable
    public CommitRecord commit() {
        return this.commit;
    }

    public void setCommit(@Nullable CommitRecord commit) {
        this.commit = commit;
    }

    public List<FrozenTask> tasks() {
        return this.tasks;
    }

    public void setTasks(List<FrozenTask> tasks) {
        this.tasks.clear();
        this.tasks.addAll(tasks);
    }

    public void replaceTask(FrozenTask task) {
        for (int index = 0; index < this.tasks.size(); index++) {
            if (this.tasks.get(index).id().equals(task.id())) {
                this.tasks.set(index, task);
                return;
            }
        }
        this.tasks.add(task);
    }

    public boolean abilitiesActive() {
        return this.abilitiesActive;
    }

    public void setAbilitiesActive(boolean abilitiesActive) {
        this.abilitiesActive = abilitiesActive;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public long updatedGameTime() {
        return this.updatedGameTime;
    }

    public void touch(long gameTime) {
        this.updatedGameTime = gameTime;
    }

    public int quantity(Identifier option) {
        return this.selections.getOrDefault(option, 0);
    }

    public Object2IntMap<Identifier> nonZeroSelections() {
        Object2IntMap<Identifier> result = new Object2IntOpenHashMap<>();
        this.selections.forEach((id, quantity) -> {
            if (quantity > 0) result.put(id, quantity);
        });
        return result;
    }

    private List<FrozenTask> tasksList() {
        return List.copyOf(this.tasks);
    }
}
