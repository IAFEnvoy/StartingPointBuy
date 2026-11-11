package com.iafenvoy.spb.network.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Optional;

public record GroupLine(String key, Component title, Optional<Component> description, String countMode,
                        int count, int min, int max, boolean visible) {
    public static final Codec<GroupLine> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(GroupLine::key),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(GroupLine::title),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(GroupLine::description),
            Codec.STRING.optionalFieldOf("count_mode", "distinct").forGetter(GroupLine::countMode),
            Codec.INT.optionalFieldOf("count", 0).forGetter(GroupLine::count),
            Codec.INT.optionalFieldOf("min", 0).forGetter(GroupLine::min),
            Codec.INT.optionalFieldOf("max", 0).forGetter(GroupLine::max),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(GroupLine::visible)
    ).apply(i, GroupLine::new));
}
