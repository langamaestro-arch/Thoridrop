package com.langamaestro.thoridrop;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Thoridrop initial development build.
 * Commands and server lifecycle hooks are the first milestone.
 */
@Mod(Thoridrop.MOD_ID)
public final class Thoridrop {
    public static final String MOD_ID = "thoridrop";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Thoridrop() {
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Thoridrop development build initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("thoridrop")
            .then(Commands.literal("status")
                .executes(context -> {
                    context.getSource().sendSuccess(
                        () -> Component.literal("Thoridrop: development build; no drop event active."),
                        false
                    );
                    return 1;
                }))
        );
    }
}
