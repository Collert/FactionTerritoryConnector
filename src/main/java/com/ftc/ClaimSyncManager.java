package com.ftc;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.ServerPlayerClaimInfo;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central coordination hub: faction lookups, shared sync state and the
 * "{faction}'s Territory" naming of OPAC claims.
 */
public class ClaimSyncManager {

    /** Recruits claims only exist in the overworld, so only overworld OPAC claims are mirrored. */
    public static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();

    /**
     * True while the connector itself is editing OPAC claims, so the OPAC listener
     * neither charges for nor mirrors those edits back into Recruits.
     */
    public static boolean isSyncing = false;

    /**
     * Overworld chunks that are known to be present in both OPAC and Recruits.
     * Lets the sync task tell "Recruits removed this chunk" apart from
     * "this OPAC claim was never imported into Recruits".
     */
    private static final Set<ChunkPos> managedChunks = new HashSet<>();

    @Nullable
    public static RecruitsFaction getPlayerFaction(ServerPlayer player) {
        if (player == null || FactionEvents.recruitsFactionManager == null) return null;
        PlayerTeam team = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null) return null;
        return FactionEvents.recruitsFactionManager.getFactionByStringID(team.getName());
    }

    public static boolean isFactionLeader(ServerPlayer player) {
        RecruitsFaction faction = getPlayerFaction(player);
        return faction != null && player.getUUID().equals(faction.getTeamLeaderUUID());
    }

    /** The faction whose current leader is {@code playerId}, or null. */
    @Nullable
    public static RecruitsFaction getFactionLedBy(UUID playerId) {
        return getFactionsByLeader().get(playerId);
    }

    public static Map<UUID, RecruitsFaction> getFactionsByLeader() {
        Map<UUID, RecruitsFaction> result = new HashMap<>();
        if (FactionEvents.recruitsFactionManager == null) return result;
        for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
            if (faction.getTeamLeaderUUID() != null) {
                result.put(faction.getTeamLeaderUUID(), faction);
            }
        }
        return result;
    }

    /**
     * The live faction that owns a Recruits claim. The faction copy stored inside the
     * claim is a snapshot and can be stale (e.g. after a leadership change).
     */
    @Nullable
    public static RecruitsFaction getLiveOwner(RecruitsClaim claim) {
        RecruitsFaction stored = claim.getOwnerFaction();
        if (stored == null) return null;
        if (FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction live = FactionEvents.recruitsFactionManager.getFactionByStringID(stored.getStringID());
            if (live != null) return live;
        }
        return stored;
    }

    public static boolean isSameFaction(@Nullable RecruitsFaction a, @Nullable RecruitsFaction b) {
        return a != null && b != null && a.getStringID() != null && a.getStringID().equals(b.getStringID());
    }

    public static boolean isManaged(ChunkPos pos) {
        return managedChunks.contains(pos);
    }

    public static void markManaged(ChunkPos pos) {
        managedChunks.add(pos);
    }

    public static void unmarkManaged(ChunkPos pos) {
        managedChunks.remove(pos);
    }

    public static void setManaged(Set<ChunkPos> chunks) {
        managedChunks.clear();
        managedChunks.addAll(chunks);
    }

    /**
     * Shows the leader's claims as "{factionName}'s Territory" on Xaero's map by replacing the
     * username OPAC displays for them. The real player id is untouched, so permissions still work.
     */
    public static void applyTerritoryName(MinecraftServer server, UUID leaderId, RecruitsFaction faction) {
        String name = faction.getTeamDisplayName();
        if (name == null || name.isEmpty()) name = faction.getStringID();
        setOpacUsername(server, leaderId, name);
    }

    static void setOpacUsername(MinecraftServer server, UUID playerId, String name) {
        try {
            IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
            if (!claims.hasPlayerInfo(playerId)) return;
            IServerPlayerClaimInfoAPI info = claims.getPlayerInfo(playerId);
            if (info instanceof ServerPlayerClaimInfo serverInfo && !name.equals(serverInfo.getPlayerUsername())) {
                // Syncs the new name to every client.
                serverInfo.setPlayerUsername(name);
            }
        } catch (Exception e) {
            FactionTerritoryConnector.LOGGER.error("Failed to update OPAC claim name", e);
        }
    }

    /** Keeps every leader's claims named after their faction, and gives former leaders their own name back. */
    public static void refreshTerritoryNames(MinecraftServer server) {
        Map<UUID, RecruitsFaction> factionsByLeader = getFactionsByLeader();
        factionsByLeader.forEach((leaderId, faction) -> applyTerritoryName(server, leaderId, faction));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!factionsByLeader.containsKey(player.getUUID())) {
                setOpacUsername(server, player.getUUID(), player.getGameProfile().getName());
            }
        }
    }

    // OPAC resets the username on login, so re-apply the faction name afterwards.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecruitsFaction faction = getPlayerFaction(player);
            if (faction != null && player.getUUID().equals(faction.getTeamLeaderUUID())) {
                applyTerritoryName(player.server, player.getUUID(), faction);
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        managedChunks.clear();
        OPACClaimListener.seedKnownOwners(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        managedChunks.clear();
        OPACClaimListener.clearKnownOwners();
        RecruitsClaimEditor.clear();
        CurrencyBridge.clear();
    }
}
