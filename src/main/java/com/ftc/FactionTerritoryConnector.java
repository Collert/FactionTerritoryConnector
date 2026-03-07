package com.ftc;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xaero.pac.common.event.api.OPACServerAddonRegisterEvent;

@Mod(FactionTerritoryConnector.MOD_ID)
public class FactionTerritoryConnector {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "factionterritoryconnector";

    public FactionTerritoryConnector() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ClaimSyncManager());
    }

    @SubscribeEvent
    public void onOPACAddonRegister(OPACServerAddonRegisterEvent event) {
        LOGGER.info("Registering OPAC Addon for Faction Territory Connector...");
        
        // Register custom permission system to only allow faction leaders to claim
        event.getPermissionSystemManager().register("faction_leader_only", new FactionClaimPermissionHandler());
        
        // Register claim tracker listener for synchronization
        event.getClaimsManagerTrackerAPI().register(new OPACClaimListener());
    }
}
