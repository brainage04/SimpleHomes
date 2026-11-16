package io.github.brainage04.simplehomes.fabric;

import io.github.brainage04.simplehomes.HomeCommandRegistration;
import io.github.brainage04.simplehomes.SimpleHomes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class SimpleHomesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimpleHomes.initialize(
                GameRuleBuilder.forInteger(10)
                        .category(GameRuleCategory.PLAYER)
                        .range(1, Integer.MAX_VALUE)
                        .buildAndRegister(
                                Identifier.fromNamespaceAndPath(
                                        SimpleHomes.MOD_ID, "maximum_homes")),
                GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.PLAYER)
                        .buildAndRegister(
                                Identifier.fromNamespaceAndPath(SimpleHomes.MOD_ID, "reset_fall")));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        HomeCommandRegistration.register(dispatcher));
    }
}
