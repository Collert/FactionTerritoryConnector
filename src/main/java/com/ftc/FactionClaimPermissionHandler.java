package com.ftc;

import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;
import xaero.pac.common.server.player.data.api.ServerPlayerDataAPI;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Decides who may claim OPAC chunks: only faction leaders, and only as many chunks as they can pay for.
 * Hooked into OPAC by {@link com.ftc.mixin.ServerClaimsManagerMixin}.
 */
public class FactionClaimPermissionHandler {

    /** Players in OPAC admin mode or server-claim mode keep OPAC's normal behaviour and pay nothing. */
    public static boolean isBypassing(ServerPlayer player) {
        try {
            ServerPlayerDataAPI data = ServerPlayerDataAPI.from(player);
            return data != null && (data.isClaimsAdminMode() || data.isClaimsServerMode());
        } catch (Exception e) {
            return false;
        }
    }

    /** Why this player can't claim a chunk right now, or null if they can. */
    @Nullable
    public static Component getClaimBlockReason(ServerPlayer player) {
        RecruitsFaction faction = ClaimSyncManager.getPlayerFaction(player);
        if (faction == null) {
            return Component.literal("Join a faction first! Only faction leaders can claim territory.").withStyle(ChatFormatting.RED);
        }
        if (!player.getUUID().equals(faction.getTeamLeaderUUID())) {
            return Component.literal("Only your faction leader (" + faction.getTeamLeaderName() + ") can claim territory for "
                    + faction.getTeamDisplayName() + ".").withStyle(ChatFormatting.RED);
        }
        if (CurrencyBridge.getAffordableChunks(player) < 1) {
            return Component.literal("Claiming a chunk costs " + CurrencyBridge.getChunkCost() + " " + CurrencyBridge.getCurrencyName()
                    + ". You have " + CurrencyBridge.getBalance(player) + ".").withStyle(ChatFormatting.RED);
        }
        return null;
    }

    /**
     * Called before OPAC handles a claim request. Returns true (and tells the player why) if the request must be refused.
     */
    public static boolean shouldBlockClaimRequest(UUID playerId) {
        if (PlayerConfig.SERVER_CLAIM_UUID.equals(playerId)) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        // Claims made through the API or for offline players aren't player requests; leave them alone.
        if (player == null || isBypassing(player)) return false;

        Component reason = getClaimBlockReason(player);
        if (reason == null) return false;
        player.sendSystemMessage(reason);
        return true;
    }

    /**
     * Replaces OPAC's base claim limit for faction leaders with "claims owned + claims affordable",
     * so OPAC itself stops an area claim at the last chunk the leader can pay for, and Xaero's map
     * shows how many more chunks they can afford. Everyone else who isn't bypassing gets 0.
     */
    public static int adjustBaseClaimLimit(@Nullable ServerPlayer player, int original) {
        if (player == null || isBypassing(player)) return original;
        if (!ClaimSyncManager.isFactionLeader(player)) return 0;
        if (CurrencyBridge.getChunkCost() <= 0) return original;
        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
            int claimCount = api.getServerClaimsManager().getPlayerInfo(player.getUUID()).getClaimCount();
            IPlayerConfigAPI config = api.getPlayerConfigs().getLoadedConfig(player.getUUID());
            int bonus = config == null ? 0 : config.getEffective(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            long limit = (long) claimCount + CurrencyBridge.getAffordableChunks(player) - bonus;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, limit));
        } catch (Exception e) {
            FactionTerritoryConnector.LOGGER.error("Failed to compute claim limit", e);
            return original;
        }
    }
}
