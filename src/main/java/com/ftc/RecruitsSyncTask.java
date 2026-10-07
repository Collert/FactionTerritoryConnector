package com.ftc;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.player.config.PlayerConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Recruits has no claim events, so every few seconds this compares Recruits' claims with OPAC's
 * overworld claims and fixes OPAC to match. Recruits wins conflicts, so sieges and leadership
 * changes carry over to OPAC (claims are owned by the faction leader there).
 */
public class RecruitsSyncTask {

    private static final int SYNC_INTERVAL_TICKS = 100;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (++tickCounter < SYNC_INTERVAL_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || ClaimEvents.recruitsClaimManager == null) return;
        try {
            syncClaims(server);
            ClaimSyncManager.refreshTerritoryNames(server);
        } catch (Exception e) {
            FactionTerritoryConnector.LOGGER.error("Failed to sync Recruits claims to OPAC", e);
        }
    }

    public static void syncClaims(MinecraftServer server) {
        IServerClaimsManagerAPI opac = OpenPACServerAPI.get(server).getServerClaimsManager();

        // Who Recruits says owns each chunk, as the current leader of the owning faction.
        Map<ChunkPos, UUID> recruitsOwners = new HashMap<>();
        for (RecruitsClaim claim : ClaimEvents.recruitsClaimManager.getAllClaims()) {
            if (claim == null || claim.isRemoved) continue;
            RecruitsFaction faction = ClaimSyncManager.getLiveOwner(claim);
            if (faction == null || faction.getTeamLeaderUUID() == null) continue;
            for (ChunkPos pos : claim.getClaimedChunks()) {
                recruitsOwners.put(pos, faction.getTeamLeaderUUID());
            }
        }

        // Who OPAC says owns each overworld chunk.
        Map<ChunkPos, UUID> opacOwners = new HashMap<>();
        opac.getPlayerInfoStream().forEach(info -> {
            IPlayerDimensionClaimsAPI dimension = info.getDimension(ClaimSyncManager.OVERWORLD);
            if (dimension == null) return;
            dimension.getStream().map(IPlayerClaimPosListAPI::getStream)
                    .forEach(positions -> positions.forEach(pos -> opacOwners.put(pos, info.getPlayerId())));
        });

        Map<UUID, RecruitsFaction> factionsByLeader = ClaimSyncManager.getFactionsByLeader();
        Set<ChunkPos> inSync = new HashSet<>();

        ClaimSyncManager.isSyncing = true;
        try {
            // Recruits -> OPAC: claims made, conquered or handed to a new leader in Recruits.
            for (Map.Entry<ChunkPos, UUID> entry : recruitsOwners.entrySet()) {
                ChunkPos pos = entry.getKey();
                UUID leader = entry.getValue();
                UUID opacOwner = opacOwners.get(pos);
                if (leader.equals(opacOwner)) {
                    inSync.add(pos);
                } else if (PlayerConfig.SERVER_CLAIM_UUID.equals(opacOwner)) {
                    // Never overwrite server claims (spawn protection etc.).
                    continue;
                } else {
                    opac.claim(ClaimSyncManager.OVERWORLD, leader, -1, pos.x, pos.z, false);
                    inSync.add(pos);
                    FactionTerritoryConnector.LOGGER.debug("Recruits sync: {} now belongs to {} in OPAC", pos, leader);
                }
            }

            // OPAC claims of faction leaders that Recruits doesn't know about.
            for (Map.Entry<ChunkPos, UUID> entry : opacOwners.entrySet()) {
                ChunkPos pos = entry.getKey();
                if (recruitsOwners.containsKey(pos)) continue;
                RecruitsFaction faction = factionsByLeader.get(entry.getValue());
                if (faction == null) continue; // Not faction territory (admin, server or factionless claims).

                if (ClaimSyncManager.isManaged(pos)) {
                    // It was in both before, so Recruits removed it: remove it from OPAC too.
                    opac.unclaim(ClaimSyncManager.OVERWORLD, pos.x, pos.z);
                    FactionTerritoryConnector.LOGGER.debug("Recruits sync: unclaimed {} in OPAC (removed in Recruits)", pos);
                } else {
                    // Never synced (e.g. claimed before this mod was installed): bring it into Recruits.
                    RecruitsClaimEditor.queueAdd(pos, faction, entry.getValue());
                }
            }
        } finally {
            ClaimSyncManager.isSyncing = false;
        }

        ClaimSyncManager.setManaged(inSync);
        // Imports mark themselves as managed once they're in Recruits.
        RecruitsClaimEditor.flush(server);
    }
}
