package com.ftc.mixin;

import com.ftc.FactionClaimPermissionHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.claims.ClaimsManager;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.claims.result.api.AreaClaimResult;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.claims.ServerClaimsManager;

import java.util.Set;
import java.util.UUID;

/**
 * Hooks OPAC's claim requests (Xaero's map, commands and the API all go through these methods)
 * so faction rules are enforced before anything is claimed.
 */
@Mixin(value = ServerClaimsManager.class, remap = false)
public abstract class ServerClaimsManagerMixin {

    // Area claims, used by Xaero's World Map.
    @Inject(method = "tryClaimActionOverArea", at = @At("HEAD"), cancellable = true)
    private void ftc$checkAreaClaim(ResourceLocation dimension, UUID playerId, int subConfigIndex, int fromX, int fromZ,
                                    int left, int top, int right, int bottom, ClaimsManager.Action action, boolean replace,
                                    CallbackInfoReturnable<AreaClaimResult> cir) {
        if (action == ClaimsManager.Action.CLAIM && FactionClaimPermissionHandler.shouldBlockClaimRequest(playerId)) {
            cir.setReturnValue(new AreaClaimResult(Set.of(ClaimResult.Type.CLAIM_LIMIT_REACHED), left, top, right, bottom));
        }
    }

    // Single-chunk claims, used by the /openpac-claims commands.
    @Inject(method = "tryToClaimTyped", at = @At("HEAD"), cancellable = true)
    private void ftc$checkClaim(ResourceLocation dimension, UUID playerId, int subConfigIndex, int fromX, int fromZ,
                                int x, int z, boolean replace, CallbackInfoReturnable<ClaimResult<PlayerChunkClaim>> cir) {
        if (FactionClaimPermissionHandler.shouldBlockClaimRequest(playerId)) {
            cir.setReturnValue(new ClaimResult<>(null, ClaimResult.Type.CLAIM_LIMIT_REACHED));
        }
    }

    @Inject(method = "getPlayerBaseClaimLimit(Lnet/minecraft/server/level/ServerPlayer;)I", at = @At("RETURN"), cancellable = true)
    private void ftc$adjustLimitForPlayer(ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FactionClaimPermissionHandler.adjustBaseClaimLimit(player, cir.getReturnValue()));
    }

    @Inject(method = "getPlayerBaseClaimLimit(Ljava/util/UUID;)I", at = @At("RETURN"), cancellable = true)
    private void ftc$adjustLimitForId(UUID playerId, CallbackInfoReturnable<Integer> cir) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
        cir.setReturnValue(FactionClaimPermissionHandler.adjustBaseClaimLimit(player, cir.getReturnValue()));
    }
}
