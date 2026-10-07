package com.ftc;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import xaero.pac.client.api.OpenPACClientAPI;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Adjusts the "Claim Selected" option of Xaero's World Map right-click menu: greys it out for
 * players who aren't faction leaders, and shows leaders what the selection will cost.
 * Also hides the claim button in Recruits' faction screen.
 * Xaero's map has no API for this, so it's done through reflection and fails silently if Xaero changes.
 */
@Mod.EventBusSubscriber(modid = FactionTerritoryConnector.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class XaeroUIEnhancements {

    private static final String GUI_MAP_CLASS = "xaero.map.gui.GuiMap";
    private static final String CLAIM_OPTION_KEY = "gui.xaero_pac_claim_chunks";
    private static final String RECRUITS_FACTION_SCREEN_CLASS = "com.talhanation.recruits.client.gui.faction.TeamInspectionScreen";
    private static final String RECRUITS_CLAIM_BUTTON_KEY = "gui.recruits.team.claim";

    private static boolean reflectionFailed = false;
    private static Field rightClickMenuField;
    private static Field actionOptionsField;
    private static Field optionsField;
    private static Field realOptionsField;
    private static Field optionNameField;
    private static Method setActiveMethod;

    /** The menu we already adjusted, so it's only done once per opened menu. */
    private static Object lastPatchedMenu;

    // Recruits' faction screen has a "Claim" button that opens Recruits' own claim map; claiming happens on Xaero's map instead.
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!event.getScreen().getClass().getName().equals(RECRUITS_FACTION_SCREEN_CLASS)) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getMessage().getContents() instanceof TranslatableContents contents
                    && RECRUITS_CLAIM_BUTTON_KEY.equals(contents.getKey())) {
                widget.visible = false;
                widget.active = false;
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (reflectionFailed || !screen.getClass().getName().equals(GUI_MAP_CLASS)) return;
        try {
            if (rightClickMenuField == null) initReflection(screen.getClass());
            Object menu = rightClickMenuField.get(screen);
            if (menu == null || menu == lastPatchedMenu) return;
            lastPatchedMenu = menu;
            patchMenu(menu);
        } catch (Exception e) {
            reflectionFailed = true;
            FactionTerritoryConnector.LOGGER.warn("Couldn't adjust Xaero's World Map claim menu; this Xaero version isn't supported", e);
        }
    }

    private static void initReflection(Class<?> guiMapClass) throws ReflectiveOperationException {
        rightClickMenuField = accessible(guiMapClass.getDeclaredField("rightClickMenu"));
        Class<?> menuClass = rightClickMenuField.getType();
        actionOptionsField = accessible(menuClass.getDeclaredField("actionOptions"));
        optionsField = accessible(menuClass.getSuperclass().getDeclaredField("options"));
        realOptionsField = accessible(menuClass.getSuperclass().getDeclaredField("realOptions"));
        Class<?> optionClass = Class.forName("xaero.map.gui.dropdown.rightclick.RightClickOption");
        optionNameField = accessible(optionClass.getDeclaredField("name"));
        setActiveMethod = optionClass.getMethod("setActive", boolean.class);
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    private static void patchMenu(Object menu) throws ReflectiveOperationException {
        List<?> actionOptions = (List<?>) actionOptionsField.get(menu);
        String[] options = (String[]) optionsField.get(menu);
        String[] realOptions = (String[]) realOptionsField.get(menu);
        if (actionOptions == null) return;

        for (int i = 0; i < actionOptions.size(); i++) {
            Object option = actionOptions.get(i);
            if (option == null || !CLAIM_OPTION_KEY.equals(optionNameField.get(option))) continue;

            String label = getClaimLabel(option);
            if (label == null) continue;
            if (label.startsWith("§8")) setActiveMethod.invoke(option, false);
            if (options != null && i < options.length) options[i] = label;
            if (realOptions != null && i < realOptions.length) realOptions[i] = label;
        }
    }

    /** The new label for the claim option, starting with the grey colour code if claiming isn't possible, or null to leave it alone. */
    private static String getClaimLabel(Object option) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || isOpacBypassing()) return null;

        RecruitsFaction faction = ClientManager.ownFaction;
        if (faction == null) return "§8Claim (join a faction first)";
        if (!player.getUUID().equals(faction.getTeamLeaderUUID())) return "§8Claim (faction leader only)";

        int costPerChunk = ClientManager.configValueChunkCost;
        if (costPerChunk <= 0) return null;
        ItemStack currency = ClientManager.currencyItemStack != null ? ClientManager.currencyItemStack : ClientManager.currency;
        String currencyName = currency != null && !currency.isEmpty() ? currency.getHoverName().getString() : "Emerald";
        int balance = currency == null ? 0 : countInInventory(player.getInventory(), currency);
        int chunks = getSelectedChunkCount(option);

        if (balance < costPerChunk) return "§8Claim (need " + costPerChunk + " " + currencyName + ")";
        String cost = chunks > 0 ? (chunks * costPerChunk) + " " + currencyName : costPerChunk + " " + currencyName + "/chunk";
        return "Claim Selected (" + cost + ")";
    }

    private static boolean isOpacBypassing() {
        try {
            IClientClaimsManagerAPI claims = OpenPACClientAPI.get().getClaimsManager();
            return claims.isAdminMode() || claims.isServerMode();
        } catch (Exception e) {
            return false;
        }
    }

    private static int countInInventory(Inventory inventory, ItemStack currency) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == currency.getItem()) count += stack.getCount();
        }
        return count;
    }

    /** The selection bounds are captured in the option's anonymous class; 0 if they can't be read. */
    private static int getSelectedChunkCount(Object option) {
        try {
            Class<?> c = option.getClass();
            int left = accessible(c.getDeclaredField("val$reqLeft")).getInt(option);
            int top = accessible(c.getDeclaredField("val$reqTop")).getInt(option);
            int right = accessible(c.getDeclaredField("val$reqRight")).getInt(option);
            int bottom = accessible(c.getDeclaredField("val$reqBottom")).getInt(option);
            return (right - left + 1) * (bottom - top + 1);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }
}
