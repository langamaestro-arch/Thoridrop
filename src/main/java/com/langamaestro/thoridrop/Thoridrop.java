package com.langamaestro.thoridrop;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Thoridrop entry point. Event gameplay is not yet implemented.
 */
@Mod(Thoridrop.MOD_ID)
public final class Thoridrop {
    public static final String MOD_ID = "thoridrop";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Thoridrop() {
        LOGGER.info("Thoridrop initialized (development build)");
    }
}
