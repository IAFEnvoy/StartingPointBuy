package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.spb.util.DefinitionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One selectable entry. The selection quantity {@code q} is always a non-negative integer; zero means
 * "not selected". A single option therefore covers single choice, multi choice and repeated purchase.
 */
public record Option(Component name, Optional<Component> description, Optional<IconReference> icon,
                     int hardMax, NumberProvider minCount, Optional<NumberProvider> maxCount,
                     EntityCondition visibleCondition, EntityCondition enabledCondition,
                     List<ValidationRule> validationRules, Map<Holder<Resource>, NumberProvider> resourceChanges,
                     List<Outcome> outcomes) {
    public static final Codec<Option> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("name").forGetter(Option::name),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description").forGetter(Option::description),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Option::icon),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("hard_max", 1).forGetter(Option::hardMax),
            NumberProvider.CODEC.optionalFieldOf("min_count", new Constant(1.0)).forGetter(Option::minCount),
            NumberProvider.CODEC.optionalFieldOf("max_count").forGetter(Option::maxCount),
            EntityCondition.optionalCodec("visible_condition").forGetter(Option::visibleCondition),
            EntityCondition.optionalCodec("enabled_condition").forGetter(Option::enabledCondition),
            ValidationRule.CODEC.listOf().optionalFieldOf("validation_rules", List.of()).forGetter(Option::validationRules),
            Codec.unboundedMap(Resource.CODEC, NumberProvider.CODEC).optionalFieldOf("resource_changes", Map.of()).forGetter(Option::resourceChanges),
            DefinitionCodecs.uniqueList(Outcome.CODEC, Outcome::key, "outcome key").optionalFieldOf("outcomes", List.of()).forGetter(Option::outcomes)
    ).apply(i, Option::new));
}
