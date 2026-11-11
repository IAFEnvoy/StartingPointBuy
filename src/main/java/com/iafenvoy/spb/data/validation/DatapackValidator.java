package com.iafenvoy.spb.data.validation;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.*;

/**
 * Rejects cross-definition errors after every datapack registry and its tags have frozen.
 */
public final class DatapackValidator {
    private DatapackValidator() {
    }

    public static RegistryAccess.Frozen validate(RegistryAccess.Frozen access) {
        // Dimension-only loads do not contain the SPB registries.
        if (access.lookup(SpbResourceKeys.PLAN).isEmpty()) return access;
        List<Issue> issues = new ArrayList<>();
        access.lookupOrThrow(SpbResourceKeys.GROUP).listElements().forEach(holder ->
                PlanIndex.resolveOptions(access, holder.key().identifier(), holder.value(), issues));
        for (ResolvedPlan plan : PlanIndex.all(access)) {
            issues.addAll(plan.issues());
        }
        Set<String> errors = new LinkedHashSet<>();
        for (Issue issue : new LinkedHashSet<>(issues)) {
            String message = issue.path() + ": " + issue.message().getString();
            if (issue.message().getContents() instanceof TranslatableContents translated && translated.getArgs().length > 0)
                message += " " + Arrays.toString(translated.getArgs());
            if (issue.isError()) errors.add(message);
            else StartingPointBuy.LOGGER.warn("Starting Point Buy datapack: {}", message);
        }
        if (!errors.isEmpty()) {
            errors.forEach(error -> StartingPointBuy.LOGGER.error("Starting Point Buy datapack: {}", error));
            throw new IllegalStateException("Invalid Starting Point Buy datapack definitions:\n" + String.join("\n", errors));
        }
        return access;
    }
}
