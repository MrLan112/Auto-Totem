package net.autototem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The actual auto-equip behaviour, run once per client tick.
 *
 * Normal mode: whenever the offhand has no totem and the inventory has one, move it over
 * (after the configured delay).
 *
 * Health threshold mode: the totem is only moved when health drops to the threshold (or when
 * the player takes a hit, if that option is on). Once the player has recovered, the totem is
 * swapped back to the slot it came from and the previous offhand item returns.
 */
final class TotemLogic {

    /** Slot numbers of the player inventory menu: 9-35 main inventory, 36-44 hotbar. */
    private static final int INVENTORY_SLOT_MIN = 9;
    private static final int INVENTORY_SLOT_MAX = 44;

    /** Button number that makes a SWAP click exchange with the offhand. */
    private static final int OFFHAND_SWAP_BUTTON = 40;

    /** Pause after a swap so a lagging server can't make us flip items back and forth. */
    private static final int SWAP_COOLDOWN_TICKS = 2;

    private LocalPlayer lastPlayer;
    private int previousTotalCount = -1;
    private float previousHealth = -1.0f;

    private int waitedTicks = 0;
    private int cooldownTicks = 0;

    /** Threshold mode: a danger situation has been detected and the totem should be in hand. */
    private boolean armed = false;
    /** Threshold mode: the inventory slot the totem was taken from, or -1. */
    private int returnSlot = -1;
    /** Threshold mode: what the offhand held before we swapped. */
    private ItemStack displacedStack = ItemStack.EMPTY;

    void tick(Minecraft client, AutoTotemConfig config, boolean enabled) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            resetAll();
            return;
        }

        if (player != lastPlayer) {
            // New player object (join, respawn, dimension change): old numbers mean nothing now.
            resetAll();
            lastPlayer = player;
        }

        // Chests, crafting tables and so on use a different menu with different slot numbers.
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }

        float health = player.getHealth();
        boolean tookHit = previousHealth >= 0.0f && health < previousHealth - 0.001f;
        previousHealth = health;

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (enabled && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            handleEquipping(client, player, config, health, tookHit);
        } else {
            clearThresholdState();
        }

        updateStatsAndDetectUsage(player, config);
    }

    void resetAll() {
        lastPlayer = null;
        previousTotalCount = -1;
        previousHealth = -1.0f;
        waitedTicks = 0;
        cooldownTicks = 0;
        clearThresholdState();
    }

    private void clearThresholdState() {
        armed = false;
        returnSlot = -1;
        displacedStack = ItemStack.EMPTY;
        waitedTicks = 0;
    }

    // ------------------------------------------------------------------ equipping

    private void handleEquipping(Minecraft client, LocalPlayer player, AutoTotemConfig config,
                                 float health, boolean tookHit) {
        AutoTotemConfig.HealthThresholdConfig threshold = config.healthThreshold;
        boolean wantTotemInHand;

        if (!threshold.enabled) {
            armed = false;
            returnSlot = -1;
            wantTotemInHand = true;
        } else {
            float thresholdHealth = threshold.hearts * 2.0f;
            if (health <= thresholdHealth || (threshold.afterFirstHit && tookHit)) {
                armed = true;
            } else if (armed && isRecovered(player, threshold, health, thresholdHealth)) {
                armed = false;
            }
            wantTotemInHand = armed;
        }

        if (wantTotemInHand) {
            equipIfNeeded(client, player, config, threshold.enabled);
        } else {
            waitedTicks = 0;
            returnTotemIfNeeded(client, player);
        }
    }

    /** Back to safe: above the threshold, and, in "after first hit" mode, fully healed. */
    private static boolean isRecovered(LocalPlayer player, AutoTotemConfig.HealthThresholdConfig threshold,
                                       float health, float thresholdHealth) {
        if (health <= thresholdHealth) {
            return false;
        }
        if (threshold.afterFirstHit) {
            return health >= player.getMaxHealth() - 0.01f;
        }
        return true;
    }

    private void equipIfNeeded(Minecraft client, LocalPlayer player, AutoTotemConfig config, boolean rememberSlot) {
        if (isTotem(player.getOffhandItem())) {
            waitedTicks = 0;
            return;
        }
        if (waitedTicks < config.equipDelayTicks) {
            waitedTicks++;
            return;
        }
        if (cooldownTicks > 0) {
            return;
        }

        for (Slot slot : player.inventoryMenu.slots) {
            if (slot.index < INVENTORY_SLOT_MIN || slot.index > INVENTORY_SLOT_MAX) {
                continue;
            }
            if (isTotem(slot.getItem())) {
                if (rememberSlot) {
                    returnSlot = slot.index;
                    displacedStack = player.getOffhandItem().copy();
                }
                swapWithOffhand(client, player, slot.index);
                waitedTicks = 0;
                return;
            }
        }
    }

    private void returnTotemIfNeeded(Minecraft client, LocalPlayer player) {
        if (returnSlot < 0) {
            return;
        }
        if (cooldownTicks > 0) {
            return;
        }

        int slotIndex = returnSlot;
        returnSlot = -1;

        // The totem might have been used up or moved by the player in the meantime.
        if (!isTotem(player.getOffhandItem()) || slotIndex >= player.inventoryMenu.slots.size()) {
            return;
        }
        // Only swap back if the slot still holds what we put there, otherwise we would
        // pull some unrelated item into the offhand.
        Slot slot = player.inventoryMenu.slots.get(slotIndex);
        if (!ItemStack.matches(slot.getItem(), displacedStack)) {
            return;
        }

        swapWithOffhand(client, player, slotIndex);
        displacedStack = ItemStack.EMPTY;
    }

    private void swapWithOffhand(Minecraft client, LocalPlayer player, int slotIndex) {
        if (!ContainerClick.swapWithOffhand(client, player, slotIndex, OFFHAND_SWAP_BUTTON)) {
            return;
        }
        cooldownTicks = SWAP_COOLDOWN_TICKS;
    }

    // ------------------------------------------------------------------ statistics

    /**
     * Counts all totems the player carries and treats a drop in that number, while the player
     * is still alive, as "a totem just saved me".
     *
     * Known limitation: throwing a totem away with Q looks the same.
     */
    private void updateStatsAndDetectUsage(LocalPlayer player, AutoTotemConfig config) {
        int totalCount = countTotems(player);

        boolean countDropped = previousTotalCount >= 0 && totalCount < previousTotalCount;
        if (countDropped && player.isAlive() && player.getHealth() > 0.0f) {
            onTotemUsed(config);
        }

        previousTotalCount = totalCount;
        AutoTotemState.setTotemsRemaining(totalCount);
    }

    private static int countTotems(LocalPlayer player) {
        int count = 0;

        ItemStack offhand = player.getOffhandItem();
        if (isTotem(offhand)) {
            count += offhand.getCount();
        }

        for (Slot slot : player.inventoryMenu.slots) {
            if (slot.index < INVENTORY_SLOT_MIN || slot.index > INVENTORY_SLOT_MAX) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (isTotem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void onTotemUsed(AutoTotemConfig config) {
        AutoTotemState.markUsedNow();

        if (!config.notificationHud.enabled) {
            return;
        }
        NotificationHud.trigger();
        if (config.notificationHud.volume > 0.0f) {
            AutoTotemSound.play(config.notificationHud.volume);
        }
    }

    private static boolean isTotem(ItemStack stack) {
        return stack.is(Items.TOTEM_OF_UNDYING);
    }
}
