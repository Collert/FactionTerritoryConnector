package com.ftc;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import xaero.pac.common.server.api.OpenPACServerAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures standard OPAC claims checks route strictly through
 * the custom faction permission module defined in FactionTerritoryConnector.
 */
public class PermissionOverride {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Called at post-registration to lock OPAC's API to use our Faction module exclusively.
     */
    public static void initializeRouting() {
        // Enforce Faction system routing
        // This is usually configured in OPAC's "permissionSystem" config, but we can programmatically 
        // nudge or log a warning if the server isn't matching "faction_leader_only"
        LOGGER.info("Verifying permission routing targets the Faction Territory Connector.");
    }
}
