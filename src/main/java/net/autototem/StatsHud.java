package net.autototem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The small panel with the number of totems left and the time since the last pop.
 * Sizes are in unscaled GUI pixels; the user's "size" setting is applied with the pose matrix,
 * so text and box scale together.
 */
public final class StatsHud {

    private StatsHud() {
    }

    private static final int MIN_WIDTH = 120;
    private static final int HEIGHT = 34;
    private static final int PADDING = 4;
    private static final int DEFAULT_MARGIN = 10;

    public static int resolveX(AutoTotemConfig.StatsHudConfig cfg) {
        return cfg.x >= 0 ? cfg.x : DEFAULT_MARGIN;
    }

    public static int resolveY(AutoTotemConfig.StatsHudConfig cfg) {
        return cfg.y >= 0 ? cfg.y : DEFAULT_MARGIN;
    }

    /** On-screen width including the user's scale. */
    public static int getWidth(Minecraft client, AutoTotemConfig.StatsHudConfig cfg, boolean preview) {
        return Math.round(baseWidth(client, preview) * cfg.scale);
    }

    /** On-screen height including the user's scale. */
    public static int getHeight(AutoTotemConfig.StatsHudConfig cfg) {
        return Math.round(HEIGHT * cfg.scale);
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, AutoTotemConfig.StatsHudConfig cfg) {
        renderAt(graphics, client, cfg, resolveX(cfg), resolveY(cfg), false);
    }

    public static void renderAt(GuiGraphicsExtractor graphics, Minecraft client, AutoTotemConfig.StatsHudConfig cfg,
                                int x, int y, boolean preview) {
        String remaining = remainingLine(preview);
        String lastUsed = lastUsedLine(preview);
        int width = baseWidth(client, preview);

        // Colors must carry an alpha byte (ARGB), otherwise they render fully transparent.
        int backgroundAlpha = alpha(cfg.opacity * 0.6f);
        int textAlpha = alpha(Math.min(1.0f, cfg.opacity + 0.2f));
        int background = backgroundAlpha << 24;
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        int accentColor = (textAlpha << 24) | 0xFFD24D;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(cfg.scale);

        graphics.fill(0, 0, width, HEIGHT, background);
        graphics.text(client.font, remaining, PADDING, PADDING, accentColor);
        graphics.text(client.font, lastUsed, PADDING, PADDING + client.font.lineHeight + 3, textColor);

        graphics.pose().popMatrix();
    }

    private static int baseWidth(Minecraft client, boolean preview) {
        int widest = Math.max(
                client.font.width(remainingLine(preview)),
                client.font.width(lastUsedLine(preview))
        );
        return Math.max(MIN_WIDTH, widest + PADDING * 2);
    }

    private static String remainingLine(boolean preview) {
        int totems = preview ? 3 : AutoTotemState.getTotemsRemaining();
        return Component.translatable("hud.autototem.stats.remaining", totems).getString();
    }

    private static String lastUsedLine(boolean preview) {
        if (preview) {
            return Component.translatable("hud.autototem.stats.last_used_ago", formatDuration(12)).getString();
        }
        if (!AutoTotemState.hasBeenUsed()) {
            return Component.translatable("hud.autototem.stats.last_used_never").getString();
        }
        long secondsAgo = (System.currentTimeMillis() - AutoTotemState.getLastUsedAtMillis()) / 1000L;
        return Component.translatable("hud.autototem.stats.last_used_ago", formatDuration(secondsAgo)).getString();
    }

    private static String formatDuration(long totalSeconds) {
        String s = Component.translatable("hud.autototem.unit.seconds").getString();
        String m = Component.translatable("hud.autototem.unit.minutes").getString();
        String h = Component.translatable("hud.autototem.unit.hours").getString();

        if (totalSeconds < 60) {
            return totalSeconds + s;
        }
        long minutes = totalSeconds / 60;
        if (minutes < 60) {
            return minutes + m + " " + (totalSeconds % 60) + s;
        }
        return (minutes / 60) + h + " " + (minutes % 60) + m;
    }

    private static int alpha(float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        return Math.round(clamped * 255.0f);
    }
}
