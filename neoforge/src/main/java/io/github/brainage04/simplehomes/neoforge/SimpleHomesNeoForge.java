package io.github.brainage04.simplehomes.neoforge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.simplehomes.HomeCommandRegistration;
import io.github.brainage04.simplehomes.SimpleHomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SimpleHomes.MOD_ID)
public final class SimpleHomesNeoForge {
    private static final DeferredRegister<GameRule<?>> GAME_RULES =
            DeferredRegister.create(Registries.GAME_RULE, SimpleHomes.MOD_ID);
    private static final DeferredHolder<GameRule<?>, GameRule<Integer>> MAXIMUM_HOMES =
            GAME_RULES.register(
                    "maximum_homes",
                    () ->
                            new GameRule<>(
                                    GameRuleCategory.PLAYER,
                                    GameRuleType.INT,
                                    IntegerArgumentType.integer(1, Integer.MAX_VALUE),
                                    (visitor, rule) -> visitor.visitInteger(rule),
                                    Codec.intRange(1, Integer.MAX_VALUE),
                                    value -> value,
                                    10,
                                    FeatureFlagSet.of()));
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> RESET_FALL =
            GAME_RULES.register(
                    "reset_fall",
                    () ->
                            new GameRule<>(
                                    GameRuleCategory.PLAYER,
                                    GameRuleType.BOOL,
                                    BoolArgumentType.bool(),
                                    (visitor, rule) -> visitor.visitBoolean(rule),
                                    Codec.BOOL,
                                    value -> value ? 1 : 0,
                                    true,
                                    FeatureFlagSet.of()));

    public SimpleHomesNeoForge(IEventBus modEventBus) {
        GAME_RULES.register(modEventBus);
        modEventBus.addListener(
                (FMLCommonSetupEvent event) ->
                        SimpleHomes.initialize(MAXIMUM_HOMES.get(), RESET_FALL.get()));
        NeoForge.EVENT_BUS.addListener(SimpleHomesNeoForge::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        HomeCommandRegistration.register(event.getDispatcher());
    }
}
