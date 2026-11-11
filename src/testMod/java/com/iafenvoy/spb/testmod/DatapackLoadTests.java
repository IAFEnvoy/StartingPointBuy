package com.iafenvoy.spb.testmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Exercises Minecraft's actual registry loading future, including deferred Holder binding.
 */
final class DatapackLoadTests {
    private static final List<RegistryDataLoader.RegistryData<?>> REGISTRIES = List.of(
            new RegistryDataLoader.RegistryData<>(SaturnResourceKeys.RESOURCE, Resource.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(SaturnResourceKeys.ABILITY, Ability.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(SpbResourceKeys.OPTION, Option.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(SpbResourceKeys.GROUP, Group.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(SpbResourceKeys.PLAN, Plan.DIRECT_CODEC, RegistryValidator.none()));

    private DatapackLoadTests() {
    }

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("loading/valid", helper -> {
            RegistryAccess.Frozen loaded = load(helper, helper.getLevel().getServer().getResourceManager());
            helper.assertTrue(loaded.lookupOrThrow(SpbResourceKeys.PLAN).size() >= 14, "valid registries finish loading");
            helper.succeed();
        });
        for (JsonElement element : DatapackTests.read("fixtures/invalid/index.json").getAsJsonArray()) {
            JsonObject fixture = element.getAsJsonObject();
            tests.put("loading/" + fixture.get("name").getAsString(), helper -> reject(helper, fixture));
        }
    }

    static RegistryAccess.Frozen load(GameTestHelper helper, ResourceManager manager) {
        var keys = REGISTRIES.stream().map(RegistryDataLoader.RegistryData::key).toList();
        List<HolderLookup.RegistryLookup<?>> context = helper.getLevel().registryAccess().listRegistries().filter(registry -> !keys.contains(registry.key())).toList();
        return RegistryDataLoader.load(manager, context, REGISTRIES, Runnable::run, List.of()).join();
    }

    private static void reject(GameTestHelper helper, JsonObject fixture) {
        JsonElement json = DatapackTests.read(fixture.get("file").getAsString());
        ResourceManager parent = helper.getLevel().getServer().getResourceManager();
        String kind = fixture.get("kind").getAsString();
        Identifier path = Identifier.parse("spb_test:spb/" + kind + "/rejected_fixture.json");
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        net.minecraft.server.packs.resources.Resource resource = new net.minecraft.server.packs.resources.Resource(parent.listPacks().findFirst().orElseThrow(),
                () -> new ByteArrayInputStream(bytes));
        boolean failed = false;
        try {
            load(helper, new FixtureResourceManager(parent, Map.of(path, resource)));
        } catch (CompletionException expected) {
            failed = true;
            if (fixture.get("stage").getAsString().equals("resolve")) {
                helper.assertTrue(expected.getCause().getMessage().contains(fixture.get("path").getAsString()),
                        "semantic load error retains its field path: " + expected);
            }
        }
        helper.assertTrue(failed, "one bad definition must fail the registry loading future: " + fixture.get("name"));
        helper.succeed();
    }
}
