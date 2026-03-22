package com.ftc;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

public class ClaimSyncManager {

    public static RecruitsFaction getPlayerFaction(ServerPlayer player) {
        if (player == null || player.level() == null) return null;
        PlayerTeam team = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null) return null;
        return FactionEvents.recruitsFactionManager.getFactionByStringID(team.getName());
    }

    public static boolean isFactionLeader(ServerPlayer player) {
        RecruitsFaction faction = getPlayerFaction(player);
        if (faction == null) return false;
        return player.getUUID().equals(faction.getTeamLeaderUUID());
    }

    public static void updateClaimName(ServerPlayer player) {
        if (player == null) return;
        RecruitsFaction faction = getPlayerFaction(player);
        if (faction != null && isFactionLeader(player)) {
            String claimName = faction.teamDisplayName;
            try {
                if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
                    // First, clear the "Claim Name" config so we don't get the "Name - Player" format.
                    xaero.pac.common.server.player.config.api.IPlayerConfigAPI config = 
                        xaero.pac.common.server.api.OpenPACServerAPI.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer())
                            .getPlayerConfigs().getLoadedConfig(player.getUUID());
                    if (config != null) {
                        config.tryToReset(xaero.pac.common.server.player.config.api.PlayerConfigOptions.CLAIMS_NAME);
                    }

                    // Second, override the player's core claim username, which native OPAC saves into the uuid.dat file.
                    xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI infoAPI = 
                        xaero.pac.common.server.api.OpenPACServerAPI.get(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer())
                            .getServerClaimsManager().getPlayerInfo(player.getUUID());
                    
                    if (infoAPI instanceof xaero.pac.common.server.claims.player.ServerPlayerClaimInfo) {
                        ((xaero.pac.common.server.claims.player.ServerPlayerClaimInfo) infoAPI).setPlayerUsername(claimName);
                    }
                }
            } catch (Exception e) {
                FactionTerritoryConnector.LOGGER.error("Failed to update OPAC claim name", e);
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            updateClaimName((ServerPlayer) event.getEntity());
        }
    }
}
