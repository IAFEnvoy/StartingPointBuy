package com.iafenvoy.spb.runtime.outcome;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Execution state of one delayed action.
 *
 * <p>{@link #RUNNING} persisted across a restart means the side effect may or may not have happened;
 * it is converted to {@link #UNCERTAIN} and never replayed automatically. A normal return of a Saturn
 * action proves only that the call returned, not that the effect happened.
 */
public enum TaskState implements StringRepresentable {
    PENDING, RUNNING, DONE, FAILED, UNCERTAIN;
    public static final Codec<TaskState> CODEC = StringRepresentable.fromValues(TaskState::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean isFinished() {
        return this == DONE || this == FAILED || this == UNCERTAIN;
    }
}
