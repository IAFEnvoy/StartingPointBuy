package com.iafenvoy.spb.data.resolution;

import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.util.DefinitionNames;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * An option that belongs to exactly one group of the plan.
 */
public record ResolvedOption(Identifier id, Option definition, @Nullable Identifier groupId) {
    public String key() {
        return DefinitionNames.key(this.id);
    }
}
