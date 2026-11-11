package com.iafenvoy.spb.runtime.session;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Life cycle of a starting point session.
 *
 * <pre>
 * EDITING -&gt; COMMITTED -&gt; APPLYING -&gt; COMPLETED
 *                    \          \-&gt; NEEDS_REVIEW (an action failed or its result is unknown)
 *                     \-&gt; NEEDS_REFRESH (definition or real resource changed before commit)
 * </pre>
 */
public enum SessionState implements StringRepresentable {
    EDITING, COMMITTED, APPLYING, NEEDS_REVIEW, COMPLETED, NEEDS_REFRESH;
    public static final Codec<SessionState> CODEC = StringRepresentable.fromValues(SessionState::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean isTerminal() {
        return this == COMPLETED;
    }

    public boolean isEditable() {
        return this == EDITING;
    }
}
