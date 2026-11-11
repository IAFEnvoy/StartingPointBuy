package com.iafenvoy.spb.runtime.outcome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A permanent ability grant frozen at commit time. {@code source} is a Saturn grant source, so
 * removing this plan's source never removes the same ability granted by another system.
 */
public record FrozenGrant(Identifier source, String scope, String outcomeKey, List<Identifier> abilities) {
    public static final Codec<FrozenGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("source").forGetter(FrozenGrant::source),
            Codec.STRING.optionalFieldOf("scope", "").forGetter(FrozenGrant::scope),
            Codec.STRING.optionalFieldOf("outcome", "").forGetter(FrozenGrant::outcomeKey),
            Identifier.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(FrozenGrant::abilities)
    ).apply(i, FrozenGrant::new));
}
