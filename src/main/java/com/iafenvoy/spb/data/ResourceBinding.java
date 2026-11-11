package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.spb.util.DefinitionNames;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Binds one Saturn resource, using its registry id to generate the formula alias.
 *
 * @param initial optional provider evaluated once when the session is first created; when absent the
 *                already initialised real resource value is captured instead
 * @param quantum smallest supported unit; final values must align to it (no implicit rounding)
 */
public record ResourceBinding(Holder<Resource> resource, Optional<NumberProvider> initial, double quantum,
                              CommitMode commit, boolean display, Optional<Component> name) {
    public static final Codec<ResourceBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceBinding::resource),
            NumberProvider.CODEC.optionalFieldOf("initial").forGetter(ResourceBinding::initial),
            MiscCodecs.POSITIVE_DOUBLE.optionalFieldOf("quantum", 1.0).forGetter(ResourceBinding::quantum),
            CommitMode.CODEC.optionalFieldOf("commit", CommitMode.REPLACE).forGetter(ResourceBinding::commit),
            Codec.BOOL.optionalFieldOf("display", true).forGetter(ResourceBinding::display),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("name").forGetter(ResourceBinding::name)
    ).apply(i, ResourceBinding::new));

    public Component displayName() {
        return this.name.orElseGet(() -> Component.translatable("spb.resource." + this.resourceId().getNamespace() + "." + this.resourceId().getPath()));
    }

    public Identifier resourceId() {
        return this.resource.unwrapKey().orElseThrow().identifier();
    }

    public String key() {
        return DefinitionNames.key(this.resourceId());
    }
}
