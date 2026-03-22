package com.ftc;

import com.talhanation.recruits.FactionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public class CurrencyBridge {

    public static int DEFAULT_CHUNK_COST = 5; 

    public static int getChunkCost() {
        // Ideally we would fetch this dynamically from Recruits config if it becomes exposed.
        return DEFAULT_CHUNK_COST;
    }

    public static int getPlayerCurrencyBalance(ServerPlayer player) {
        Item currencyItem = FactionEvents.getCurrency().getItem();
        return FactionEvents.playerGetEmeraldsInInventory(player, currencyItem);
    }
    
    public static boolean doPayment(ServerPlayer player, int cost) {
        if (FactionEvents.playerHasEnoughEmeralds(player, cost)) {
            FactionEvents.doPayment(player, cost);
            return true;
        }
        return false;
    }
}
