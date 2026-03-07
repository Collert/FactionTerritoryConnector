package com.ftc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

public class CurrencyBridge {
    private static final Logger LOGGER = LogManager.getLogger();

    // Default cost if Recruits config is inaccessible
    private static final int DEFAULT_CHUNK_COST = 5; 

    /**
     * Intercept method to check and handle payment before claiming chunks.
     * @param player The player attempting to claim
     * @param chunksToClaim The number of chunks being claimed
     * @return true if the player has enough currency and payment was successful, false otherwise
     */
    public static boolean handleClaimPayment(ServerPlayer player, int chunksToClaim) {
        if (player == null || chunksToClaim <= 0) return false;

        // Bypassing costs for ops/admins could be added here if needed
        if (player.hasPermissions(2)) {
            return true;
        }

        int totalCost = calculateTotalCost(chunksToClaim);
        
        if (!playerHasEnoughCurrency(player, totalCost)) {
            // Player doesn't have enough emeralds
            return false;
        }

        return doPayment(player, totalCost);
    }

    /**
     * Calculates the cost based on Recruits config properties.
     */
    public static int calculateTotalCost(int chunksToClaim) {
        // Ideally we fetch the cost dynamically from Recruits configuration:
        // int chunkCost = RecruitsServerConfig.ChunkCost.get(); // Fallback to DEFAULT_CHUNK_COST if missing
        int chunkCost = DEFAULT_CHUNK_COST;
        
        return chunkCost * chunksToClaim;
    }

    /**
     * Helper to determine if the player has enough Emeralds in their inventory.
     */
    public static boolean playerHasEnoughCurrency(ServerPlayer player, int cost) {
        int emeraldCount = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.EMERALD) {
                emeraldCount += stack.getCount();
            }
        }
        return emeraldCount >= cost;
    }

    /**
     * Deducts the emeralds from the player's inventory.
     */
    public static boolean doPayment(ServerPlayer player, int cost) {
        int remainingToPay = cost;
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (remainingToPay <= 0) break;
            
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.EMERALD) {
                if (stack.getCount() >= remainingToPay) {
                    stack.shrink(remainingToPay);
                    remainingToPay = 0;
                } else {
                    remainingToPay -= stack.getCount();
                    stack.setCount(0);
                }
            }
        }
        
        return remainingToPay == 0;
    }
}
