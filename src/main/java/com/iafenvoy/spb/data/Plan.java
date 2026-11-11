package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.iafenvoy.spb.util.DefinitionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A complete starting-point definition. Everything that decides numbers, availability, completion and
 * rewards lives in data; the Java side only loads, validates, evaluates and applies it.
 */
public record Plan(Optional<Component> title, Optional<Component> description, Activation activation,
                   Map<String, NumberProvider> inputs, List<ResourceBinding> resources, List<Preset> presets,
                   List<EntryRef<Group>> groups, List<CompletionRule> completionRules, List<Outcome> outcomes,
                   UiSettings ui) {
    public static final Codec<Holder<Plan>> CODEC = RegistryFixedCodec.create(SpbResourceKeys.PLAN);
    public static final Codec<Plan> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("title").forGetter(Plan::title),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description").forGetter(Plan::description),
            Activation.CODEC.optionalFieldOf("activation", Activation.DEFAULT).forGetter(Plan::activation),
            Codec.unboundedMap(DefinitionCodecs.INPUT_NAME, NumberProvider.CODEC).optionalFieldOf("inputs", Map.of()).forGetter(Plan::inputs),
            DefinitionCodecs.uniqueList(ResourceBinding.CODEC, ResourceBinding::resource, "resource")
                    .validate(bindings -> DefinitionCodecs.uniqueAliases(bindings, ResourceBinding::resourceId))
                    .fieldOf("resources").forGetter(Plan::resources),
            Preset.CODEC.listOf().optionalFieldOf("presets", List.of()).forGetter(Plan::presets),
            EntryRef.codec(SpbResourceKeys.GROUP).listOf().fieldOf("groups").forGetter(Plan::groups),
            CompletionRule.CODEC.listOf().optionalFieldOf("completion_rules", List.of()).forGetter(Plan::completionRules),
            DefinitionCodecs.uniqueList(Outcome.CODEC, Outcome::key, "outcome key").optionalFieldOf("outcomes", List.of()).forGetter(Plan::outcomes),
            UiSettings.CODEC.optionalFieldOf("ui", UiSettings.DEFAULT).forGetter(Plan::ui)
    ).apply(i, Plan::new));
}
