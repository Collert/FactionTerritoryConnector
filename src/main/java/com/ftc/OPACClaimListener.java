package com.ftc;

import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.server.ServerLifecycleHooks;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reacts to every OPAC claim change: charges faction leaders for new chunks and mirrors the change into Recruits.
 */
public class OPACClaimListener implements IClaimsManagerListenerAPI {

    /**
     * Last known owner of every claimed chunk. OPAC only reports the new state, and also reports
     * changes that keep the owner (forceloading, sub-claims), which must not be charged again.
     */
    private static final Map<String, UUID> knownOwners = new HashMap<>();

    private static String key(ResourceLocation dimension, int chunkX, int chunkZ) {
        return dimension + "|" + chunkX + "|" + chunkZ;
    }

    static void seedKnownOwners(MinecraftServer server) {
        knownOwners.clear();
        OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerInfoStream().forEach(info ->
                info.getStream().forEach(dimensionEntry ->
                        dimensionEntry.getValue().getStream().forEach(posList ->
                                posList.getStream().forEach(pos ->
                                        knownOwners.put(key(dimensionEntry.getKey(), pos.x, pos.z), info.getPlayerId())))));
        FactionTerritoryConnector.LOGGER.info("Tracking {} existing OPAC claims", knownOwners.size());
    }

    static void clearKnownOwners() {
        knownOwners.clear();
    }

    @Override
    public void onWholeRegionChange(@Nonnull ResourceLocation dimension, int regionX, int regionZ) {
    }

    @Override
    public void onDimensionChange(@Nonnull ResourceLocation dimension) {
    }

    @Override
    public void onChunkChange(@Nonnull ResourceLocation dimension, int chunkX, int chunkZ, @Nullable IPlayerChunkClaimAPI claim) {
        String key = key(dimension, chunkX, chunkZ);
        UUID newOwner = claim == null ? null : claim.getPlayerId();
        UUID oldOwner = newOwner == null ? knownOwners.remove(key) : knownOwners.put(key, newOwner);

        if (ClaimSyncManager.isSyncing || Objects.equals(oldOwner, newOwner)) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        boolean overworld = ClaimSyncManager.OVERWORLD.equals(dimension);

        if (newOwner == null) {
            if (overworld && oldOwner != null) RecruitsClaimEditor.queueRemove(pos, oldOwner);
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(newOwner);
        RecruitsFaction faction;
        if (player != null && !FactionClaimPermissionHandler.isBypassing(player)) {
            // A normal player claim: enforce leadership and take payment.
            faction = ClaimSyncManager.getPlayerFaction(player);
            Component reason = FactionClaimPermissionHandler.getClaimBlockReason(player);
            if (reason == null && overworld && RecruitsClaimEditor.isClaimedByOtherFaction(pos, faction)) {
                reason = Component.literal("That chunk is Recruits territory of another faction.").withStyle(ChatFormatting.RED);
            }
            // Already the faction's Recruits territory (the sync task just hasn't caught up): don't charge twice.
            boolean alreadyPaid = reason == null && overworld && RecruitsClaimEditor.isClaimedByFaction(pos, faction);
            if (reason == null && !alreadyPaid && !CurrencyBridge.tryCharge(player, CurrencyBridge.getChunkCost())) {
                reason = Component.literal("Not enough " + CurrencyBridge.getCurrencyName() + " to claim more chunks.").withStyle(ChatFormatting.RED);
            }
            if (reason != null) {
                revert(server, dimension, chunkX, chunkZ, oldOwner);
                CurrencyBridge.recordFailure(player, reason);
                return;
            }
            if (!alreadyPaid) CurrencyBridge.recordPayment(player, CurrencyBridge.getChunkCost());
        } else {
            // Admin/server-mode claims, and claims made through the API or for offline players, are free.
            // They only become faction territory if the owner leads a faction.
            faction = ClaimSyncManager.getFactionLedBy(newOwner);
            if (faction == null) {
                if (overworld && oldOwner != null) RecruitsClaimEditor.queueRemove(pos, oldOwner);
                return;
            }
        }

        if (overworld) {
            if (oldOwner != null) RecruitsClaimEditor.queueRemove(pos, oldOwner);
            RecruitsClaimEditor.queueAdd(pos, faction, newOwner);
        }
        ClaimSyncManager.applyTerritoryName(server, newOwner, faction);
    }

    private static void revert(MinecraftServer server, ResourceLocation dimension, int chunkX, int chunkZ, @Nullable UUID oldOwner) {
        boolean wasSyncing = ClaimSyncManager.isSyncing;
        ClaimSyncManager.isSyncing = true;
        try {
            var claims = OpenPACServerAPI.get(server).getServerClaimsManager();
            if (oldOwner == null) {
                claims.unclaim(dimension, chunkX, chunkZ);
            } else {
                claims.claim(dimension, oldOwner, -1, chunkX, chunkZ, false);
            }
        } finally {
            ClaimSyncManager.isSyncing = wasSyncing;
        }
    }
}
