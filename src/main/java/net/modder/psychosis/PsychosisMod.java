package net.modder.psychosis;

import net.fabricmc.api.ModInitializer;

/** Common entrypoint deliberately contains no server behaviour. */
public final class PsychosisMod implements ModInitializer {
    public static final String MOD_ID = "psychosis";

    @Override
    public void onInitialize() {
        // The feature is registered exclusively by the client entrypoint.
    }
}
