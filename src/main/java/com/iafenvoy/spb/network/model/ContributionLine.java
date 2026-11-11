package com.iafenvoy.spb.network.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ContributionLine(String resourceKey, double perUnit, double total) {
    public static final Codec<ContributionLine> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("resource").forGetter(ContributionLine::resourceKey),
            Codec.DOUBLE.optionalFieldOf("per_unit", 0.0).forGetter(ContributionLine::perUnit),
            Codec.DOUBLE.optionalFieldOf("total", 0.0).forGetter(ContributionLine::total)
    ).apply(i, ContributionLine::new));
}
