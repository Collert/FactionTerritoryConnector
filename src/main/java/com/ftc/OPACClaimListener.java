package com.ftc;

import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

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

        if (claim != null) {
            boolean isNew = knownClaims.add(claimId);
            
            if (isNew) {
                if (ServerLifecycleHooks.getCurrentServer() != null) {
                    ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(claim.getPlayerId());
                    if (player != null && !player.hasPermissions(2)) {
                        
                        // Check Faction Leader
                        if (!ClaimSyncManager.isFactionLeader(player)) {
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
                        }
                    } else {
                        // Admin claim bypasses requirements
                        FactionTerritoryConnector.LOGGER.info("Admin chunk claim bypassed at " + chunkX + ", " + chunkZ);
                    }
                }
            }
        } else {
            knownClaims.remove(claimId);
        }
    }

    @Override
    public void onDimensionChange(@Nonnull ResourceLocation dimension) {
    }
}
