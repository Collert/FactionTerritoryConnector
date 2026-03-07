package com.ftc;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;

/**
 * Handles UI alterations for Xaero's map/claiming interface,
 * displaying faction status and currency balances directly to the user.
 */
@Mod.EventBusSubscriber(modid = FactionTerritoryConnector.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class XaeroUIEnhancements {

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        
        // We'll hook onto Xaero's claim map screen class name to render UI logic
        if (screen.getClass().getName().contains("xaero.pac.client.gui.ClaimsGui")) {
            // Apply client-side faction checks
            // 1. If player isn't a faction leader, find and disable "Claim" buttons.
            // 2. Add Emerald UI text overlays for cost visibility.
            
            for (Object widget : event.getListenersList()) {
                if (widget instanceof Button) {
                    Button btn = (Button) widget;
                    // String matching for OPAC's native "Claim" button
                    if (btn.getMessage().getString().toLowerCase().contains("claim")) {
                        // Logic to disable using client wrapper check
                        // btn.active = ClientClaimManager.isLeader(); 
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen.getClass().getName().contains("xaero.pac.client.gui.ClaimsGui")) {
            // Render 2D icon of an emerald next to the claim button coordinates 
            // and the dynamic claim cost fetched from CurrencyBridge
        }
    }
}
