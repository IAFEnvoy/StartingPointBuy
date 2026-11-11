package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.spb.data.resolution.ResolvedOption;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public record OptionView(Identifier id, ResolvedOption option, int quantity, boolean visible,
                         boolean enabled, int minCount, int maxCount, List<Component> issues,
                         List<Contribution> contributions, List<Identifier> abilities, List<String> actionKeys) {
    public boolean selected() {
        return this.quantity > 0;
    }

    public boolean valid() {
        return this.issues.isEmpty();
    }

    public boolean repeatable() {
        return this.maxCount > 1 || this.option.definition().hardMax() > 1;
    }
}
