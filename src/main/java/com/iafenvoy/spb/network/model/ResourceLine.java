package com.iafenvoy.spb.network.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public record ResourceLine(String key, Component name, double base, double value, double min, double max,
                           boolean display, boolean valid) {
    public static final Codec<ResourceLine> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(ResourceLine::key),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ResourceLine::name),
            Codec.DOUBLE.optionalFieldOf("base", 0.0).forGetter(ResourceLine::base),
            Codec.DOUBLE.optionalFieldOf("value", 0.0).forGetter(ResourceLine::value),
            Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(ResourceLine::min),
            Codec.DOUBLE.optionalFieldOf("max", 0.0).forGetter(ResourceLine::max),
            Codec.BOOL.optionalFieldOf("display", true).forGetter(ResourceLine::display),
            Codec.BOOL.optionalFieldOf("valid", true).forGetter(ResourceLine::valid)
    ).apply(i, ResourceLine::new));
}
