package com.iafenvoy.spb.data.resolution;

import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnDatapackRegistries;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Stable fingerprint of everything that defines a session: the plan, the referenced groups, options
 * and the bound resources. Stored in the session so a data-pack change can be detected after a
 * reload; a mismatching draft is rebuilt before it can be submitted again.
 */
public final class Fingerprint {
    private Fingerprint() {
    }

    public static String of(HolderLookup.Provider access, Identifier planId, Plan plan,
                            Map<String, Holder<Resource>> resources, List<ResolvedGroup> groups,
                            List<ResolvedOption> options) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, access);
        StringBuilder builder = new StringBuilder();
        builder.append(StartingPointBuy.MOD_ID).append(":fingerprint/1\n");
        builder.append("plan=").append(planId).append('\n');
        append(builder, ops, Plan.DIRECT_CODEC, plan);
        for (ResolvedGroup group : groups) {
            builder.append("group=").append(group.id()).append('\n');
            append(builder, ops, Group.DIRECT_CODEC, group.definition());
        }
        for (ResolvedOption option : options) {
            builder.append("option=").append(option.id()).append('\n');
            append(builder, ops, Option.DIRECT_CODEC, option.definition());
        }
        // The bound Saturn resource definitions are part of the effective plan, even though the plan
        // only references them by id.
        for (ResourceBinding binding : plan.resources()) {
            Holder<Resource> resource = resources.get(binding.key());
            if (resource != null) {
                builder.append("resource=").append(binding.resourceId()).append('\n');
                append(builder, ops, Resource.DIRECT_CODEC, resource.value());
            }
        }
        Set<Identifier> abilities = new LinkedHashSet<>();
        collectAbilities(plan.outcomes(), abilities);
        options.forEach(option -> collectAbilities(option.definition().outcomes(), abilities));
        for (Identifier ability : abilities) {
            SaturnDatapackRegistries.holder(access, SaturnResourceKeys.ABILITY, ability).ifPresent(holder -> {
                builder.append("ability=").append(ability).append('\n');
                append(builder, ops, Ability.DIRECT_CODEC, holder.value());
            });
        }
        return sha256(builder.toString());
    }

    private static void collectAbilities(List<Outcome> outcomes, Set<Identifier> abilities) {
        for (Outcome outcome : outcomes) {
            outcome.abilities().forEach(holder -> abilities.add(holder.unwrapKey().orElseThrow().identifier()));
        }
    }

    private static <T> void append(StringBuilder builder, DynamicOps<Tag> ops, Codec<T> codec, T value) {
        DataResult<Tag> result = codec.encodeStart(ops, value);
        if (result.result().isPresent()) {
            canonical(builder, result.result().get());
        } else {
            builder.append("encoded=").append(value);
        }
        builder.append('\n');
    }

    private static void canonical(StringBuilder builder, Tag tag) {
        if (tag instanceof CompoundTag compound) {
            builder.append('{');
            for (String key : new TreeSet<>(compound.keySet())) {
                builder.append(key).append('=');
                canonical(builder, compound.get(key));
                builder.append(',');
            }
            builder.append('}');
        } else if (tag instanceof ListTag list) {
            builder.append('[');
            for (Tag value : list) {
                canonical(builder, value);
                builder.append(',');
            }
            builder.append(']');
        } else {
            builder.append(tag);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte element : bytes) {
                hex.append(Character.forDigit((element >> 4) & 0xF, 16)).append(Character.forDigit(element & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
