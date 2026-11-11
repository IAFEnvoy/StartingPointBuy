package com.iafenvoy.spb.testmod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adds one broken definition to an otherwise valid pack without touching the running world's data.
 */
record FixtureResourceManager(ResourceManager parent, Map<Identifier, Resource> overrides) implements ResourceManager {
    @Override
    public @NonNull Set<String> getNamespaces() {
        return Stream.concat(this.parent.getNamespaces().stream(), this.overrides.keySet().stream().map(Identifier::getNamespace))
                .collect(Collectors.toSet());
    }

    @Override
    public @NonNull Optional<Resource> getResource(@NonNull Identifier id) {
        return this.overrides.containsKey(id) ? Optional.of(this.overrides.get(id)) : this.parent.getResource(id);
    }

    @Override
    public @NonNull List<Resource> getResourceStack(@NonNull Identifier id) {
        return this.overrides.containsKey(id) ? List.of(this.overrides.get(id)) : this.parent.getResourceStack(id);
    }

    @Override
    public @NonNull Map<Identifier, Resource> listResources(@NonNull String directory, @NonNull Predicate<Identifier> filter) {
        Map<Identifier, Resource> result = new LinkedHashMap<>(this.parent.listResources(directory, filter));
        this.overrides.forEach((id, resource) -> {
            if (id.getPath().startsWith(directory + "/") && filter.test(id)) result.put(id, resource);
        });
        return result;
    }

    @Override
    public @NonNull Map<Identifier, List<Resource>> listResourceStacks(@NonNull String directory, @NonNull Predicate<Identifier> filter) {
        Map<Identifier, List<Resource>> result = new LinkedHashMap<>(this.parent.listResourceStacks(directory, filter));
        this.overrides.forEach((id, resource) -> {
            if (id.getPath().startsWith(directory + "/") && filter.test(id)) result.put(id, List.of(resource));
        });
        return result;
    }

    @Override
    public @NonNull Stream<PackResources> listPacks() {
        return this.parent.listPacks();
    }
}
