package io.github.brainage04.modernminecraftmodtemplate.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class ExampleClientCommand {
    public static final String COMMAND_NAME = "exampleclient";

    public static int execute(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("This is an example client command."));

        return 1;
    }

    public static void initialize(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(COMMAND_NAME)
                .executes(context ->
                        execute(
                                context.getSource()
                        )
                )
        );
    }
}
