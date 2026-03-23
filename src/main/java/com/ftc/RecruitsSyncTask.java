package com.ftc;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = FactionTerritoryConnector.MOD_ID)
public class RecruitsSyncTask {
    
    private static int tickCounter = 0;
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        
        tickCounter++;
        if (tickCounter >= 100) { // Every 5 seconds
            tickCounter = 0;
            syncClaims();
        }
    }

    private static void syncClaims() {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        if (ClaimEvents.recruitsClaimManager == null) return;
        
        IServerClaimsManagerAPI opacManager = OpenPACServerAPI.get(ServerLifecycleHooks.getCurrentServer()).getServerClaimsManager();
        ResourceLocation overworldDim = new ResourceLocation("minecraft", "overworld");
        
        // 1. Gather all OPAC claims in the overworld -> Map<ChunkPos, UUID>
        Map<ChunkPos, UUID> opacClaims = new HashMap<>();
        
        opacManager.getPlayerInfoStream().forEach(playerInfo -> {
            UUID playerId = playerInfo.getPlayerId();
            var dimClaims = playerInfo.getDimension(overworldDim);
            if (dimClaims != null) {
                dimClaims.getStream().forEach(posList -> {
                    posList.getStream().forEach(chunkPos -> {
                        opacClaims.put(chunkPos, playerId);
                    });
                });
            }
        });
        
        // 2. Gather all Recruits claims -> Map<ChunkPos, UUID>
        Map<ChunkPos, UUID> recClaims = new HashMap<>();
        List<RecruitsClaim> allClaims = ClaimEvents.recruitsClaimManager.getAllClaims();
        if (allClaims == null) allClaims = new ArrayList<>();
        
        for (RecruitsClaim claim : allClaims) {
            if (claim.getOwnerFaction() == null) continue;
            UUID leaderId = claim.getOwnerFaction().getTeamLeaderUUID();
            if (leaderId == null) continue;
            
            for (ChunkPos cp : claim.getClaimedChunks()) {
                recClaims.put(cp, leaderId);
            }
        }
        
        // Temporarily disable our OPAC chunk listener requirements so we don't deduct emeralds during forced syncs
        ClaimSyncManager.isSyncing = true;
        try {
            // 3. Reconcile OPAC -> Recruits (if Recruits removed it or changed owner, update OPAC)
            for (Map.Entry<ChunkPos, UUID> entry : opacClaims.entrySet()) {
                ChunkPos opacChunk = entry.getKey();
                UUID opacOwner = entry.getValue();
                
                UUID recOwner = recClaims.get(opacChunk);
                if (recOwner == null) {
                    // Claim no longer exists in Recruits (e.g. siege lost, abandoned), unclaim in OPAC
                    opacManager.unclaim(overworldDim, opacChunk.x, opacChunk.z);
                    FactionTerritoryConnector.LOGGER.info("Recruits sync: Unclaiming " + opacChunk + " in OPAC (removed in Recruits).");
                } else if (!recOwner.equals(opacOwner)) {
                    // Ownership mismatch (e.g. siege transferred it)
                    opacManager.unclaim(overworldDim, opacChunk.x, opacChunk.z);
                    opacManager.claim(overworldDim, recOwner, opacChunk.x, opacChunk.z, 0, true);
                    FactionTerritoryConnector.LOGGER.info("Recruits sync: Transferred " + opacChunk + " in OPAC to " + recOwner);
                }
            }
            
            // 4. Reconcile Recruits -> OPAC (if Recruits claimed it but OPAC didn't know)
            for (Map.Entry<ChunkPos, UUID> entry : recClaims.entrySet()) {
                ChunkPos recChunk = entry.getKey();
                UUID recOwner = entry.getValue();
                
                UUID opacOwner = opacClaims.get(recChunk);
                if (opacOwner == null) {
                    // Missing in OPAC
                    opacManager.claim(overworldDim, recOwner, recChunk.x, recChunk.z, 0, true);
                    FactionTerritoryConnector.LOGGER.info("Recruits sync: Forcing OPAC claim at " + recChunk + " for " + recOwner);
                }
            }
        } finally {
            ClaimSyncManager.isSyncing = false;
        }
    }
}
