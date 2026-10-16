package net.autototem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Performs the "swap with offhand" inventory click.
 *
 * Mojang renamed the game mode's click method and replaced its ClickType argument with a
 * different type between releases, so the method is looked up by name and parameter shape
 * instead of being called directly. Minecraft is not obfuscated in this version, which is
 * what makes a lookup by name safe.
 */
final class ContainerClick {

    private static Method method;
    private static Object swapConstant;
    private static boolean lookupFailed;

    private ContainerClick() {
    }

    /** Returns true if the click was sent. */
    static boolean swapWithOffhand(Minecraft client, LocalPlayer player, int slotIndex, int button) {
        if (lookupFailed || client.gameMode == null) {
            return false;
        }
        try {
            if (method == null) {
                resolve(client);
                if (lookupFailed) {
                    return false;
                }
            }
            method.invoke(client.gameMode, player.inventoryMenu.containerId, slotIndex, button, swapConstant, player);
            return true;
        } catch (ReflectiveOperationException e) {
            lookupFailed = true;
            return false;
        }
    }

    private static void resolve(Minecraft client) {
        for (Method candidate : client.gameMode.getClass().getMethods()) {
            String name = candidate.getName();
            if (!name.equals("handleContainerInput") && !name.equals("handleInventoryMouseClick")) {
                continue;
            }
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length != 5 || types[0] != int.class || types[1] != int.class || types[2] != int.class) {
                continue;
            }
            Object constant = findSwap(types[3]);
            if (constant != null) {
                method = candidate;
                swapConstant = constant;
                return;
            }
        }
        lookupFailed = true;
    }

    private static Object findSwap(Class<?> type) {
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("SWAP")) {
                    return constant;
                }
            }
        }
        try {
            Field field = type.getField("SWAP");
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
