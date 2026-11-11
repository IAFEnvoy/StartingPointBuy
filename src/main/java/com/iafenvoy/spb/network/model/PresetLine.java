package com.iafenvoy.spb.network.model;

import com.iafenvoy.saturn.data.IconReference;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Optional;

public record PresetLine(String key, Component name, Optional<Component> description, Optional<IconReference> icon,
                         boolean matches, List<Component> entries) {
    public static final Codec<PresetLine> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(PresetLine::key),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(PresetLine::name),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(PresetLine::description),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(PresetLine::icon),
            Codec.BOOL.optionalFieldOf("matches", false).forGetter(PresetLine::matches),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(PresetLine::entries)
    ).apply(i, PresetLine::new));
}
