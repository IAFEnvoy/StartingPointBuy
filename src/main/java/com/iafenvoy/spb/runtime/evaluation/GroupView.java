package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.util.DefinitionNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public record GroupView(Identifier id, Group definition, int count, int min, int max, boolean visible,
                        List<Component> issues) {
    public String key() {
        return DefinitionNames.key(this.id);
    }

    public boolean valid() {
        return this.issues.isEmpty();
    }
}
