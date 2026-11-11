package com.iafenvoy.spb.util;

import com.iafenvoy.saturn.util.formula.FormulaVariables;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Codec helpers shared by the data definitions. All number fields use Saturn's {@link NumberProvider}
 * codec, which accepts either a plain number or an expression string as a shortcut.
 */
public final class DefinitionCodecs {
    public static final Codec<String> INPUT_NAME = Codec.STRING.validate(value -> FormulaVariables.isValidName("input_" + value) && !value.isEmpty()
            ? DataResult.success(value) : DataResult.error(() -> "Expected a nonempty input name containing only letters, digits and underscores: " + value));

    private DefinitionCodecs() {
    }

    public static <T, K> Codec<List<T>> uniqueList(Codec<T> codec, Function<T, K> key, String field) {
        return codec.listOf().validate(values -> uniqueValues(values, key, field));
    }

    public static <T, K> DataResult<List<T>> uniqueValues(List<T> values, Function<T, K> key, String field) {
        HashSet<K> seen = new HashSet<>();
        for (T value : values) {
            K name = key.apply(value);
            if (!seen.add(name)) return DataResult.error(() -> "Duplicate " + field + ": " + name);
        }
        return DataResult.success(values);
    }

    public static <T> DataResult<List<T>> uniqueAliases(List<T> values, Function<T, Identifier> id) {
        Map<String, Identifier> aliases = new HashMap<>();
        for (T value : values) {
            Identifier current = id.apply(value);
            String alias = DefinitionNames.key(current);
            Identifier previous = aliases.putIfAbsent(alias, current);
            if (previous != null)
                return DataResult.error(() -> "Generated alias '" + alias + "' conflicts between " + previous + " and " + current);
        }
        return DataResult.success(values);
    }
}
