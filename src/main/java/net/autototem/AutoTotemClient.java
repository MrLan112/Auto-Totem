package net.autototem;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Auto Totem: keeps a Totem of Undying in the offhand.
 *
 * Hotkeys: J toggles the mod, B opens the settings (both can be rebound in the controls menu).
 */
public class AutoTotemClient implements ClientModInitializer {

    public static final String MOD_ID = "autototem";

    private static KeyMapping toggleKey;
    private static KeyMapping menuKey;

    private static AutoTotemConfig config;
    private static boolean enabled = true;

    private final TotemLogic logic = new TotemLogic();

    @Override
    public void onInitializeClient() {
        config = AutoTotemConfig.load();
        enabled = config.enabled;
        AutoTotemSound.reload(config);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main")
        );

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.toggle",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_J,
                category
        ));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_B,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                AutoTotemClient::renderHud
        );
    }

    public static AutoTotemConfig getConfig() {
        return config;
    }

    public static void saveConfig() {
        AutoTotemConfig.save(config);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        config.enabled = value;
        saveConfig();
    }

    /** Name of the key currently bound to the toggle action, for display in the settings screen. */
    public static String getToggleKeyName() {
        return toggleKey == null ? "J" : toggleKey.getTranslatedKeyMessage().getString();
    }

    private void onClientTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            setEnabled(!enabled);
            if (client.player != null) {
                client.gui.hud.setOverlayMessage(Component.translatable(
                        enabled ? "message.autototem.enabled" : "message.autototem.disabled"), true);
            }
        }

        while (menuKey.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new AutoTotemConfigScreen(null));
            }
        }

        logic.tick(client, config, enabled);
    }

    /** HUD elements are not hidden automatically, so F1 and open screens are checked by hand. */
    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || client.gui.screen() != null) {
            return;
        }

        if (config.statsHud.enabled) {
            StatsHud.render(graphics, client, config.statsHud);
        }
        NotificationHud.render(graphics, client, config.notificationHud);
    }
}
