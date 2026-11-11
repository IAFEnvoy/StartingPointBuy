package com.iafenvoy.spb.util;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Naming shared by the loader and runtime. Registry ids generate formula aliases; collisions after
 * normalisation are rejected at load time rather than resolved using load-order-dependent suffixes.
 */
public final class DefinitionNames {
    private DefinitionNames() {
    }

    public static String key(Identifier id) {
        return id.toString().replace(':', '_').replace('/', '_').replace('-', '_').replace('.', '_');
    }

    /**
     * Presets are embedded, so their one-based list position is their generated identity.
     */
    public static String presetKey(int index) {
        return "preset_" + (index + 1);
    }

    public static String inputVariable(String key) {
        return "input_" + key;
    }

    public static String baseVariable(String key) {
        return "base_" + key;
    }

    public static String selectedVariable(String key) {
        return "selected_" + key;
    }

    public static String groupDistinctVariable(String key) {
        return "group_" + key + "_distinct";
    }

    public static String groupTotalVariable(String key) {
        return "group_" + key + "_total";
    }

    public static String resourceVariable(String key) {
        return "resource_" + key;
    }

    /**
     * Turns an arbitrary data-pack id into a valid identifier path fragment for grant sources.
     */
    public static String sanitize(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (char character : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean allowed = character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                    || character == '_' || character == '-' || character == '/' || character == '.';
            builder.append(allowed ? character : '_');
        }
        String result = builder.toString();
        return result.isEmpty() ? "unnamed" : result;
    }
}
