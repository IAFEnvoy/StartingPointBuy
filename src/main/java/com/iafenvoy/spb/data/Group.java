package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * A selectable group of options.
 *
 * <p>{@code count_mode} selects whether limits count option kinds or total quantities.
 * {@code min_count}/{@code max_count} are providers evaluated against the full choice set; each
 * option controls its own repeat limit independently.
 */
public record Group(Component title, Optional<Component> description, CountMode countMode,
                    NumberProvider minCount, Optional<NumberProvider> maxCount, EntityCondition visibleCondition,
                    List<EntryRef<Option>> options) {
    public static final Codec<Group> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("title").forGetter(Group::title),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description").forGetter(Group::description),
            CountMode.CODEC.optionalFieldOf("count_mode", CountMode.DISTINCT).forGetter(Group::countMode),
            NumberProvider.CODEC.optionalFieldOf("min_count", new Constant(0.0)).forGetter(Group::minCount),
            NumberProvider.CODEC.optionalFieldOf("max_count").forGetter(Group::maxCount),
            EntityCondition.optionalCodec("visible_condition").forGetter(Group::visibleCondition),
            EntryRef.codec(SpbResourceKeys.OPTION).listOf().fieldOf("options").forGetter(Group::options)
    ).apply(i, Group::new));
}
