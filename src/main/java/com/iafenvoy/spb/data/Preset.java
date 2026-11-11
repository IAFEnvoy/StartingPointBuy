package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A named, complete option quantity table. Presets are embedded in the owning plan so they cannot
 * leak between plans. Applying a preset replaces the whole draft selection in one edit.
 */
public record Preset(Component name, Optional<Component> description, Optional<IconReference> icon,
                     Object2IntMap<Identifier> selections) {
    private static final Codec<Object2IntMap<Identifier>> SELECTIONS_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, Integer.MAX_VALUE))
                    .xmap(values -> Object2IntMaps.unmodifiable(new Object2IntOpenHashMap<>(values)),
                            values -> values);

    public static final Codec<Preset> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("name").forGetter(Preset::name),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description").forGetter(Preset::description),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Preset::icon),
            SELECTIONS_CODEC
                    .optionalFieldOf("selections", Object2IntMaps.emptyMap())
                    .forGetter(Preset::selections)
    ).apply(i, Preset::new));
}
