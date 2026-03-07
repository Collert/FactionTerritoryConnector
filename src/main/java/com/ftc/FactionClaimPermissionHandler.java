package com.ftc;

import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.player.permission.api.IPermissionNodeAPI;
import xaero.pac.common.server.player.permission.api.IPlayerPermissionSystemAPI;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.OptionalInt;

public class FactionClaimPermissionHandler implements IPlayerPermissionSystemAPI {
    
    @Nonnull
    @Override
    public OptionalInt getIntPermission(@Nonnull ServerPlayer player, @Nonnull IPermissionNodeAPI<Integer> node) {
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
