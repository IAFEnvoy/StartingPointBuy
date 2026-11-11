package com.iafenvoy.spb.network.model;

import com.iafenvoy.spb.data.UiSettings;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the client needs to render the selection screen. The server computes all numbers and
 * rule results; the client only sends intents back. Display data is part of the snapshot because the
 * plan registries are intentionally not synced.
 */
public record SelectionSnapshot(Identifier planId, UUID sessionId, int revision, String state, boolean editable,
                                boolean canComplete, String presetKey, Optional<Component> title,
                                Optional<Component> description,
                                List<ResourceLine> resources, List<PresetLine> presets, List<GroupLine> groups,
                                List<OptionLine> options, List<Component> failures, List<Component> issues,
                                UiSettings ui) {
    public static final Codec<SelectionSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("plan").forGetter(SelectionSnapshot::planId),
            UUIDUtil.CODEC.fieldOf("session").forGetter(SelectionSnapshot::sessionId),
            Codec.INT.optionalFieldOf("revision", 0).forGetter(SelectionSnapshot::revision),
            Codec.STRING.optionalFieldOf("state", "editing").forGetter(SelectionSnapshot::state),
            Codec.BOOL.optionalFieldOf("editable", true).forGetter(SelectionSnapshot::editable),
            Codec.BOOL.optionalFieldOf("can_complete", false).forGetter(SelectionSnapshot::canComplete),
            Codec.STRING.optionalFieldOf("preset_key", "").forGetter(SelectionSnapshot::presetKey),
            ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(SelectionSnapshot::title),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(SelectionSnapshot::description),
            ResourceLine.CODEC.listOf().optionalFieldOf("resources", List.of()).forGetter(SelectionSnapshot::resources),
            PresetLine.CODEC.listOf().optionalFieldOf("presets", List.of()).forGetter(SelectionSnapshot::presets),
            GroupLine.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(SelectionSnapshot::groups),
            OptionLine.CODEC.listOf().optionalFieldOf("options", List.of()).forGetter(SelectionSnapshot::options),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("failures", List.of()).forGetter(SelectionSnapshot::failures),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("issues", List.of()).forGetter(SelectionSnapshot::issues),
            UiSettings.CODEC.optionalFieldOf("ui", UiSettings.DEFAULT).forGetter(SelectionSnapshot::ui)
    ).apply(i, SelectionSnapshot::new));
}
