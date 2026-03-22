package com.ftc;

import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.player.permission.api.IPermissionNodeAPI;
import xaero.pac.common.server.player.permission.api.IPlayerPermissionSystemAPI;
import xaero.pac.common.server.player.permission.api.UsedPermissionNodes;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.OptionalInt;

public class FactionClaimPermissionHandler implements IPlayerPermissionSystemAPI {
    
    @Nonnull
    @Override
    public OptionalInt getIntPermission(@Nonnull ServerPlayer player, @Nonnull IPermissionNodeAPI<Integer> node) {
        if (node.getNodeString().equals(UsedPermissionNodes.MAX_PLAYER_CLAIMS.getNodeString())) {
            if (!ClaimSyncManager.isFactionLeader(player)) {
                // Return 0 so it triggers OPAC's built-in "limit reached" which is then 
                // shown cleanly on Xaero's Map UI with our translation overrides!
                return OptionalInt.of(0);
            }
            
            // Bypass cost for operators
            if (player.hasPermissions(2)) {
                return OptionalInt.of(9999);
            }

            int currentClaims = 0;
            try {
                IServerPlayerClaimInfoAPI info = OpenPACServerAPI.get(player.server).getServerClaimsManager().getPlayerInfo(player.getUUID());
                if (info != null) {
                    currentClaims = info.getClaimCount();
                }
            } catch (Exception e) {
                FactionTerritoryConnector.LOGGER.error("Failed to get player claim info", e);
            }

            int balance = CurrencyBridge.getPlayerCurrencyBalance(player);
            int cost = CurrencyBridge.getChunkCost();
            int possibleAdditionalClaims = balance / cost;
            
            return OptionalInt.of(currentClaims + possibleAdditionalClaims);
        }
        return OptionalInt.empty();
    }

    @Override
    public boolean getPermission(@Nonnull ServerPlayer player, @Nonnull IPermissionNodeAPI<Boolean> node) {
        return false;
    }

    @Nonnull
    @Override
    public <T> Optional<T> getPermissionTyped(@Nonnull ServerPlayer player, @Nonnull IPermissionNodeAPI<T> node) {
        return Optional.empty();
    }
}
