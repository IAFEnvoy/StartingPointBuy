package com.iafenvoy.spb.data.resolution;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.util.DefinitionNames;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A group with its option definitions resolved, in display order.
 */
public record ResolvedGroup(Identifier id, Group definition, List<ResolvedOption> options) {
    public String key() {
        return DefinitionNames.key(this.id);
    }

    public EntityCondition visibleCondition() {
        return this.definition.visibleCondition();
    }
}
