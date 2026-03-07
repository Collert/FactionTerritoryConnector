package com.ftc;

import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class OPACClaimListener implements IClaimsManagerListenerAPI {

    @Override
    public void onWholeRegionChange(@Nonnull ResourceLocation dimension, int regionX, int regionZ) {
    }

    @Override
    public void onChunkChange(@Nonnull ResourceLocation dimension, int chunkX, int chunkZ, @Nullable IPlayerChunkClaimAPI claim) {
    }

    @Override
    public void onDimensionChange(@Nonnull ResourceLocation dimension) {
    }
}
