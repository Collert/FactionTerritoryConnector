package com.ftc;

import com.talhanation.recruits.client.ClientManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = FactionTerritoryConnector.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class XaeroUIEnhancements {

    private static Field rightClickMenuField = null;
    private static Field actionOptionsField = null;
    private static Class<?> rightClickOptionClass = null;

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        
        if (screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
            boolean isLeader = false;
            if (ClientManager.ownFaction != null && Minecraft.getInstance().player != null) {
                isLeader = ClientManager.ownFaction.getTeamLeaderUUID().equals(Minecraft.getInstance().player.getUUID());
            }

            if (!isLeader) {
                try {
                    if (rightClickMenuField == null) {
                        rightClickMenuField = screen.getClass().getDeclaredField("rightClickMenu");
                        rightClickMenuField.setAccessible(true);
                    }
                    
                    Object rightClickMenu = rightClickMenuField.get(screen);
                    if (rightClickMenu != null) {
                        if (actionOptionsField == null) {
                            actionOptionsField = rightClickMenu.getClass().getDeclaredField("actionOptions");
                            actionOptionsField.setAccessible(true);
                        }
                        
                        ArrayList<?> optionsList = (ArrayList<?>) actionOptionsField.get(rightClickMenu);
                        if (optionsList != null) {
                            if (rightClickOptionClass == null) {
                                rightClickOptionClass = Class.forName("xaero.map.gui.dropdown.rightclick.RightClickOption");
                            }

                            Field dropDownOptionsField = rightClickMenu.getClass().getSuperclass().getDeclaredField("options");
                            dropDownOptionsField.setAccessible(true);
                            String[] strOptions = (String[]) dropDownOptionsField.get(rightClickMenu);

                            Field dropDownRealOptionsField = rightClickMenu.getClass().getSuperclass().getDeclaredField("realOptions");
                            dropDownRealOptionsField.setAccessible(true);
                            String[] strRealOptions = (String[]) dropDownRealOptionsField.get(rightClickMenu);

                            for (int i = 0; i < optionsList.size(); i++) {
                                Object option = optionsList.get(i);
                                if (option != null && rightClickOptionClass.isInstance(option)) {
                                    Method getDisplayNameMethod = rightClickOptionClass.getMethod("getDisplayName");
                                    Method setActiveMethod = rightClickOptionClass.getMethod("setActive", boolean.class);
                                    
                                    String displayName = ((String) getDisplayNameMethod.invoke(option)).toLowerCase();
                                    if (displayName.contains("claim") || displayName.contains("forceload")) {
                                        setActiveMethod.invoke(option, false);
                                        
                                        String customText = "\u00A78(Claims) Not a faction leader";
                                        if (strOptions != null && i < strOptions.length) {
                                            strOptions[i] = customText;
                                        }
                                        if (strRealOptions != null && i < strRealOptions.length) {
                                            strRealOptions[i] = customText;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore reflection errors to prevent log spam
                }
            }
        }
    }
}
