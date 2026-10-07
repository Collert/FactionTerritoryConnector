package com.ftc;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsClaimManager;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mirrors OPAC claim changes into Recruits' claims. Edits are queued and applied at the end of the
 * tick, because every Recruits claim update re-sends all claims to every player.
 */
public class RecruitsClaimEditor {

    /** A queued edit: add the chunk for {@code faction}, or (if faction is null) remove it from {@code owner}'s faction. */
    private record PendingEdit(ChunkPos pos, @Nullable RecruitsFaction faction, UUID owner) {}

    private static final List<PendingEdit> pendingEdits = new ArrayList<>();

    /** True if Recruits says this chunk belongs to a different faction. */
    public static boolean isClaimedByOtherFaction(ChunkPos pos, RecruitsFaction faction) {
        RecruitsClaimManager manager = ClaimEvents.recruitsClaimManager;
        if (manager == null) return false;
        RecruitsClaim claim = manager.getClaim(pos);
        return claim != null && !ClaimSyncManager.isSameFaction(ClaimSyncManager.getLiveOwner(claim), faction);
    }

    /** True if Recruits says this chunk already belongs to this faction. */
    public static boolean isClaimedByFaction(ChunkPos pos, RecruitsFaction faction) {
        RecruitsClaimManager manager = ClaimEvents.recruitsClaimManager;
        if (manager == null) return false;
        RecruitsClaim claim = manager.getClaim(pos);
        return claim != null && ClaimSyncManager.isSameFaction(ClaimSyncManager.getLiveOwner(claim), faction);
    }

    public static void queueAdd(ChunkPos pos, RecruitsFaction faction, UUID leaderId) {
        pendingEdits.add(new PendingEdit(pos, faction, leaderId));
    }

    /** Removes the chunk from Recruits if it belongs to the faction that {@code formerOwner} leads. */
    public static void queueRemove(ChunkPos pos, UUID formerOwner) {
        pendingEdits.add(new PendingEdit(pos, null, formerOwner));
    }

    static void clear() {
        pendingEdits.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            flush(ServerLifecycleHooks.getCurrentServer());
        }
    }

    public static void flush(@Nullable MinecraftServer server) {
        if (pendingEdits.isEmpty()) return;
        RecruitsClaimManager manager = ClaimEvents.recruitsClaimManager;
        if (server == null || manager == null) {
            clear();
            return;
        }
        ServerLevel level = server.overworld();

        // Claims created or changed in this batch, in order. The manager's chunk lookup only updates
        // when a claim is saved, so keep our own lookup of chunks changed in this batch (null = removed).
        Set<RecruitsClaim> changed = new LinkedHashSet<>();
        Map<ChunkPos, RecruitsClaim> batchLookup = new HashMap<>();

        for (PendingEdit edit : pendingEdits) {
            if (edit.faction() == null) {
                applyRemove(manager, batchLookup, changed, edit.pos(), edit.owner());
            } else {
                applyAdd(manager, batchLookup, changed, edit.pos(), edit.faction(), edit.owner());
            }
        }

        for (RecruitsClaim claim : changed) {
            manager.addOrUpdateClaim(level, claim);
        }
        clear();
    }

    @Nullable
    private static RecruitsClaim lookup(RecruitsClaimManager manager, Map<ChunkPos, RecruitsClaim> batchLookup, ChunkPos pos) {
        if (batchLookup.containsKey(pos)) return batchLookup.get(pos);
        RecruitsClaim claim = manager.getClaim(pos);
        // The manager's lookup isn't updated until the end of the batch, so double-check the claim still has the chunk.
        return claim != null && !claim.isRemoved && claim.containsChunk(pos) ? claim : null;
    }

    private static void applyRemove(RecruitsClaimManager manager, Map<ChunkPos, RecruitsClaim> batchLookup,
                                    Set<RecruitsClaim> changed, ChunkPos pos, UUID formerOwner) {
        RecruitsClaim claim = lookup(manager, batchLookup, pos);
        if (claim == null) return;
        RecruitsFaction owner = ClaimSyncManager.getLiveOwner(claim);
        // Only release chunks of the faction the former OPAC owner leads; anything else is Recruits' business.
        if (owner == null || !formerOwner.equals(owner.getTeamLeaderUUID())) return;
        claim.removeChunk(pos);
        if (claim.getClaimedChunks().isEmpty()) {
            claim.isRemoved = true;
        } else if (pos.equals(claim.getCenter())) {
            claim.setCenter(claim.getClaimedChunks().get(0));
        }
        batchLookup.put(pos, null);
        changed.add(claim);
        ClaimSyncManager.unmarkManaged(pos);
    }

    private static void applyAdd(RecruitsClaimManager manager, Map<ChunkPos, RecruitsClaim> batchLookup,
                                 Set<RecruitsClaim> changed, ChunkPos pos, RecruitsFaction faction, UUID leaderId) {
        RecruitsClaim existing = lookup(manager, batchLookup, pos);
        if (existing != null) {
            if (ClaimSyncManager.isSameFaction(ClaimSyncManager.getLiveOwner(existing), faction)) {
                ClaimSyncManager.markManaged(pos);
            }
            return;
        }

        RecruitsClaim target = findAdjacentClaim(manager, batchLookup, pos, faction);
        if (target == null) {
            target = new RecruitsClaim(faction.getTeamDisplayName(), faction);
            target.setCenter(pos);
            target.setPlayer(new RecruitsPlayerInfo(leaderId, faction.getTeamLeaderName(), faction));
        }
        target.addChunk(pos);
        batchLookup.put(pos, target);
        changed.add(target);
        ClaimSyncManager.markManaged(pos);
    }

    /** A claim of the same faction next to {@code pos} that still has room, so territory grows as one claim. */
    @Nullable
    private static RecruitsClaim findAdjacentClaim(RecruitsClaimManager manager, Map<ChunkPos, RecruitsClaim> batchLookup,
                                                   ChunkPos pos, RecruitsFaction faction) {
        ChunkPos[] neighbours = {
                new ChunkPos(pos.x + 1, pos.z), new ChunkPos(pos.x - 1, pos.z),
                new ChunkPos(pos.x, pos.z + 1), new ChunkPos(pos.x, pos.z - 1)
        };
        for (ChunkPos neighbour : neighbours) {
            RecruitsClaim claim = lookup(manager, batchLookup, neighbour);
            if (claim != null && !claim.isUnderSiege
                    && claim.getClaimedChunks().size() < RecruitsClaim.MAX_SIZE
                    && ClaimSyncManager.isSameFaction(ClaimSyncManager.getLiveOwner(claim), faction)) {
                return claim;
            }
        }
        return null;
    }
}
