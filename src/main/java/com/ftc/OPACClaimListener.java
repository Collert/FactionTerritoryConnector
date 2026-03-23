package com.ftc;

import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import com.talhanation.recruits.ClaimEvents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class OPACClaimListener implements IClaimsManagerListenerAPI {

    private final Set<String> knownClaims = new HashSet<>();

    @Override
    public void onWholeRegionChange(@Nonnull ResourceLocation dimension, int regionX, int regionZ) {
    }

    @Override
    public synchronized void onChunkChange(@Nonnull ResourceLocation dimension, int chunkX, int chunkZ, @Nullable IPlayerChunkClaimAPI claim) {
        String claimId = dimension.toString() + ":" + chunkX + ":" + chunkZ;

        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        
        ResourceKey<net.minecraft.world.level.Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
        
        if (level == null) return;
        
        ChunkPos cPos = new ChunkPos(chunkX, chunkZ);

        if (ClaimSyncManager.isSyncing) {
            if (claim != null) knownClaims.add(claimId);
            else knownClaims.remove(claimId);
            return;
        }

        if (claim != null) {
            boolean isNew = knownClaims.add(claimId);
            
            if (isNew) {
                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(claim.getPlayerId());
                if (player != null && !player.hasPermissions(2)) {
                    
                    RecruitsFaction faction = ClaimSyncManager.getPlayerFaction(player);
                    
                    // Check Faction Leader
                    if (faction == null || !ClaimSyncManager.isFactionLeader(player)) {
                        knownClaims.remove(claimId);
                        OpenPACServerAPI.get(ServerLifecycleHooks.getCurrentServer()).getServerClaimsManager()
                            .unclaim(dimension, chunkX, chunkZ);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must be a faction leader to claim territory! Auto-unclaimed."), false);
                        return;
                    }

                    // Check Emeralds
                    int cost = CurrencyBridge.getChunkCost();
                    if (!CurrencyBridge.doPayment(player, cost)) {
                        knownClaims.remove(claimId);
                        OpenPACServerAPI.get(ServerLifecycleHooks.getCurrentServer()).getServerClaimsManager()
                            .unclaim(dimension, chunkX, chunkZ);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough emeralds to claim! Auto-unclaimed. Cost: " + cost), false);
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Paid " + cost + " Emeralds for claim."), false);
                        FactionTerritoryConnector.LOGGER.info("Chunk was claimed at " + chunkX + ", " + chunkZ + " in " + dimension.toString() + " by " + claim.getPlayerId());
                        
                        // Update OPAC map info explicitly on new claim to keep faction names in sync
                        ClaimSyncManager.updateClaimName(player);
                        
                        // Create and register RecruitsClaim
                        RecruitsClaim existingClaim = ClaimEvents.recruitsClaimManager.getClaim(cPos);
                        if (existingClaim == null) {
                            RecruitsClaim newClaim = new RecruitsClaim(faction.teamDisplayName, faction);
                            newClaim.setCenter(cPos);
                            newClaim.addChunk(cPos);
                            
                            RecruitsPlayerInfo pInfo = new RecruitsPlayerInfo(player.getUUID(), player.getScoreboardName(), faction);
                            newClaim.setPlayer(pInfo);
                            
                            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, newClaim);
                        } else {
                            existingClaim.addChunk(cPos);
                            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, existingClaim);
                        }
                    }
                } else if (player != null) {
                    // Admin claim bypasses requirements
                    FactionTerritoryConnector.LOGGER.info("Admin chunk claim bypassed at " + chunkX + ", " + chunkZ);
                    
                    RecruitsFaction faction = ClaimSyncManager.getPlayerFaction(player);
                    if (faction != null) {
                        RecruitsClaim existingClaim = ClaimEvents.recruitsClaimManager.getClaim(cPos);
                        if (existingClaim == null) {
                            RecruitsClaim newClaim = new RecruitsClaim(faction.teamDisplayName, faction);
                            newClaim.setCenter(cPos);
                            newClaim.addChunk(cPos);
                            
                            RecruitsPlayerInfo pInfo = new RecruitsPlayerInfo(player.getUUID(), player.getScoreboardName(), faction);
                            newClaim.setPlayer(pInfo);
                            
                            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, newClaim);
                        } else {
                            existingClaim.addChunk(cPos);
                            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, existingClaim);
                        }
                    }
                }
            }
        } else {
            knownClaims.remove(claimId);
            RecruitsClaim existingClaim = ClaimEvents.recruitsClaimManager.getClaim(cPos);
            if (existingClaim != null) {
                existingClaim.removeChunk(cPos);
                
                // If no chunks left, delete the claim entirely
                if (existingClaim.getClaimedChunks().isEmpty()) {
                    ClaimEvents.recruitsClaimManager.removeClaim(existingClaim);
                } else {
                    // Otherwise, just update it without the chunk
                    // Set center to first available chunk if center was removed
                    if (cPos.equals(existingClaim.getCenter()) && !existingClaim.getClaimedChunks().isEmpty()) {
                        existingClaim.setCenter(existingClaim.getClaimedChunks().get(0));
                    }
                    ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, existingClaim);
                }
            }
        }
    }

    @Override
    public void onDimensionChange(@Nonnull ResourceLocation dimension) {
    }
}
