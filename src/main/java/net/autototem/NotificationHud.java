package net.autototem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * "Totem used!" message that pops up for a couple of seconds after a totem is consumed.
 * Off by default.
 */
public final class NotificationHud {

    private NotificationHud() {
    }

    private static final int BASE_WIDTH = 160;
    private static final int BASE_HEIGHT = 28;
    private static final int PADDING = 5;
    private static final int DEFAULT_MARGIN = 20;

    private static final long HOLD_MILLIS = 2500;
    private static final long FADE_MILLIS = 700;

    private static long triggeredAtMillis = -1;

    public static void trigger() {
        triggeredAtMillis = System.currentTimeMillis();
    }

    public static int getWidth(AutoTotemConfig.NotificationHudConfig cfg) {
        return Math.round(BASE_WIDTH * cfg.scale);
    }

    public static int getHeight(AutoTotemConfig.NotificationHudConfig cfg) {
        return Math.round(BASE_HEIGHT * cfg.scale);
    }

    /** Default spot is the bottom right corner. */
    public static int resolveX(AutoTotemConfig.NotificationHudConfig cfg, int screenWidth) {
        return cfg.x >= 0 ? cfg.x : screenWidth - getWidth(cfg) - DEFAULT_MARGIN;
    }

    public static int resolveY(AutoTotemConfig.NotificationHudConfig cfg, int screenHeight) {
        return cfg.y >= 0 ? cfg.y : screenHeight - getHeight(cfg) - DEFAULT_MARGIN;
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, AutoTotemConfig.NotificationHudConfig cfg) {
        if (!cfg.enabled || triggeredAtMillis < 0) {
            return;
        }

        long elapsed = System.currentTimeMillis() - triggeredAtMillis;
        float fade = fadeMultiplier(elapsed);
        if (fade <= 0.0f) {
            triggeredAtMillis = -1;
            return;
        }

        int x = resolveX(cfg, client.getWindow().getGuiScaledWidth());
        int y = resolveY(cfg, client.getWindow().getGuiScaledHeight());
        renderAt(graphics, client, cfg, x, y, fade * cfg.opacity);
    }

    public static void renderAt(GuiGraphicsExtractor graphics, Minecraft client, AutoTotemConfig.NotificationHudConfig cfg,
                                int x, int y, float opacity) {
        int backgroundAlpha = alpha(opacity * 0.7f);
        int textAlpha = alpha(Math.min(1.0f, opacity + 0.2f));
        int background = (backgroundAlpha << 24) | 0x1A1A1A;
        int textColor = (textAlpha << 24) | 0xFFD24D;

        String line = Component.translatable("hud.autototem.notification.used").getString();
        int textWidth = client.font.width(line);
        int textX = Math.max(PADDING, (BASE_WIDTH - textWidth) / 2);
        int textY = (BASE_HEIGHT - client.font.lineHeight) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(cfg.scale);

        graphics.fill(0, 0, BASE_WIDTH, BASE_HEIGHT, background);
        graphics.text(client.font, line, textX, textY, textColor);

        graphics.pose().popMatrix();
    }

    private static float fadeMultiplier(long elapsedMillis) {
        if (elapsedMillis < HOLD_MILLIS) {
            return 1.0f;
        }
        long fadeElapsed = elapsedMillis - HOLD_MILLIS;
        if (fadeElapsed >= FADE_MILLIS) {
            return 0.0f;
        }
        return 1.0f - ((float) fadeElapsed / (float) FADE_MILLIS);
    }

    private static int alpha(float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        return Math.round(clamped * 255.0f);
    }
}
