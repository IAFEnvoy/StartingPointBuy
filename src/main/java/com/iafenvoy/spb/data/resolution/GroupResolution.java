package com.iafenvoy.spb.data.resolution;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

record GroupResolution(List<ResolvedGroup> groups,
                       Map<Identifier, ResolvedOption> options) {
}
