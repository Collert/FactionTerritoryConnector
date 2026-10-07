package com.ftc.mixin.client;

import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.client.claims.player.api.IClientPlayerClaimInfoAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;

import javax.annotation.Nullable;

/**
 * Xaero's map and minimap cache the claim tooltip per claim, but not per owner name. Faction claims are
 * renamed by changing the owner's OPAC username, so without this the old faction name stays until relog.
 */
public final class ClaimTooltipCache {
    private ClaimTooltipCache() {
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    public static String getOwnerName(IClientClaimsManagerAPI claimsManager, @Nullable IPlayerChunkClaimAPI claim) {
        if (claimsManager == null || claim == null) return null;
        IClientPlayerClaimInfoAPI info = claimsManager.getPlayerInfo(claim.getPlayerId());
        return info == null ? null : info.getPlayerUsername();
    }
}
