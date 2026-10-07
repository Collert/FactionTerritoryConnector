package com.ftc.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;

import java.util.Objects;

/** Refreshes Xaero's Map claim tooltip when the claim owner's name changes. See {@link ClaimTooltipCache}. */
@Pseudo
@Mixin(targets = "xaero.map.mods.pac.highlight.ClaimsHighlighter", remap = false)
public abstract class XaeroMapClaimsHighlighterMixin {

    @Shadow
    @Final
    @SuppressWarnings("rawtypes")
    private IClientClaimsManagerAPI claimsManager;

    @Shadow
    private IPlayerChunkClaimAPI cachedTooltipFor;

    @Unique
    private String ftc$cachedOwnerName;

    @Inject(method = "getChunkHighlightSubtleTooltip", at = @At("HEAD"), require = 0)
    private void ftc$invalidateRenamedOwner(CallbackInfoReturnable<?> cir) {
        String ownerName = ClaimTooltipCache.getOwnerName(claimsManager, cachedTooltipFor);
        if (!Objects.equals(ownerName, ftc$cachedOwnerName)) {
            cachedTooltipFor = null;
        }
        ftc$cachedOwnerName = ownerName;
    }
}
