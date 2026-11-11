package com.iafenvoy.spb.runtime.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persisted resource snapshots of a session. Stored as identifier/value lists so removing a resource
 * from a data pack does not invalidate the whole session; unknown entries stay and are reported.
 */
public record ResourceState(List<PersistedResource> baseline, List<PersistedResource> draft,
                            List<PersistedResource> committed, Map<Identifier, Double> realSnapshot) {
    public static final ResourceState EMPTY = new ResourceState(List.of(), List.of(), List.of(), Map.of());
    public static final Codec<ResourceState> CODEC = RecordCodecBuilder.create(i -> i.group(
            PersistedResource.CODEC.listOf().optionalFieldOf("baseline", List.of()).forGetter(ResourceState::baseline),
            PersistedResource.CODEC.listOf().optionalFieldOf("draft", List.of()).forGetter(ResourceState::draft),
            PersistedResource.CODEC.listOf().optionalFieldOf("committed", List.of()).forGetter(ResourceState::committed),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("real_snapshot", Map.of()).forGetter(ResourceState::realSnapshot)
    ).apply(i, ResourceState::new));

    public static Object2DoubleMap<Identifier> toMap(List<PersistedResource> list) {
        Object2DoubleMap<Identifier> map = new Object2DoubleOpenHashMap<>();
        for (PersistedResource resource : list)
            map.put(resource.id(), resource.value());
        return map;
    }

    public static List<PersistedResource> toList(Map<Identifier, Double> map) {
        List<PersistedResource> list = new ArrayList<>(map.size());
        map.forEach((id, value) -> list.add(new PersistedResource(id, value)));
        return list;
    }
}
