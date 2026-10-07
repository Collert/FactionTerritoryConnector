package com.ftc;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.config.RecruitsServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Charges Recruits' currency (emeralds by default) for OPAC claims, using Recruits' "ChunkCost" config.
 */
public class CurrencyBridge {

    private static final int FALLBACK_CHUNK_COST = 15;

    /** Chunks claimed and currency spent per player during the current tick, reported once at tick end. */
    private static final Map<UUID, int[]> pendingPayments = new HashMap<>();
    /** Last claim failure per player during the current tick, reported once at tick end. */
    private static final Map<UUID, Component> pendingFailures = new HashMap<>();

    public static int getChunkCost() {
        try {
            return RecruitsServerConfig.ChunkCost.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet.
            return FALLBACK_CHUNK_COST;
        }
    }

    public static ItemStack getCurrency() {
        return FactionEvents.getCurrency();
    }

    public static String getCurrencyName() {
        return getCurrency().getHoverName().getString();
    }

    public static int getBalance(ServerPlayer player) {
        return FactionEvents.playerGetEmeraldsInInventory(player, getCurrency().getItem());
    }

    /** How many more chunks the player can pay for right now. */
    public static int getAffordableChunks(ServerPlayer player) {
        int cost = getChunkCost();
        if (cost <= 0) return Integer.MAX_VALUE;
        return getBalance(player) / cost;
    }

    /** Removes {@code amount} currency from the inventory, or nothing if the player can't afford it. */
    public static boolean tryCharge(ServerPlayer player, int amount) {
        if (amount <= 0) return true;
        Item currency = getCurrency().getItem();
        if (FactionEvents.playerGetEmeraldsInInventory(player, currency) < amount) return false;

        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == currency) {
                int taken = Math.min(stack.getCount(), remaining);
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        inventory.setChanged();
        return true;
    }

    public static void recordPayment(ServerPlayer player, int amount) {
        int[] totals = pendingPayments.computeIfAbsent(player.getUUID(), id -> new int[2]);
        totals[0]++;
        totals[1] += amount;
    }

    public static void recordFailure(ServerPlayer player, Component message) {
        pendingFailures.put(player.getUUID(), message);
    }

    static void clear() {
        pendingPayments.clear();
        pendingFailures.clear();
    }

    // An area claim claims up to 100 chunks in one go, so summarise instead of sending a message per chunk.
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingPayments.isEmpty() && pendingFailures.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        pendingPayments.forEach((playerId, totals) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            String chunks = totals[0] == 1 ? "1 chunk" : totals[0] + " chunks";
            player.sendSystemMessage(Component.literal("Claimed " + chunks + " for " + totals[1] + " " + getCurrencyName()
                    + ". You have " + getBalance(player) + " left.").withStyle(ChatFormatting.GREEN));
        });
        pendingFailures.forEach((playerId, message) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(message);
        });
        clear();
    }
}
