package com.ftc.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;

import java.util.Objects;

/** Refreshes Xaero's Minimap claim tooltip when the claim owner's name changes. See {@link ClaimTooltipCache}. */
@Pseudo
@Mixin(targets = "xaero.common.mods.pac.highlight.ClaimsHighlighter", remap = false)
public abstract class XaeroMinimapClaimsHighlighterMixin {

    @Shadow
    @Final
    @SuppressWarnings("rawtypes")
    private IClientClaimsManagerAPI claimsManager;

    @Shadow
    private IPlayerChunkClaimAPI cachedTooltipFor;

    @Unique
    private String ftc$cachedOwnerName;

    @Inject(method = "addChunkHighlightTooltips", at = @At("HEAD"), require = 0)
    private void ftc$invalidateRenamedOwner(CallbackInfo ci) {
        String ownerName = ClaimTooltipCache.getOwnerName(claimsManager, cachedTooltipFor);
        if (!Objects.equals(ownerName, ftc$cachedOwnerName)) {
            cachedTooltipFor = null;
        }
        ftc$cachedOwnerName = ownerName;
    }
}
