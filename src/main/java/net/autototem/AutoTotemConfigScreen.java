package net.autototem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings screen. Everything lives on one page, split into boxes ("sections"). With a wide window
 * the boxes sit in two columns; with a narrow one they stack and the page scrolls with the mouse wheel.
 *
 * Both HUDs are drawn at their real positions behind the buttons and can be dragged with the mouse.
 * Backgrounds and previews are drawn in renderBackground(), i.e. underneath the widgets, so nothing
 * ever dims or covers a button.
 */
public class AutoTotemConfigScreen extends Screen {

    // ---- layout constants (GUI pixels)
    private static final int ROW_HEIGHT = 20;
    private static final int TEXT_ROW_HEIGHT = 22;
    private static final int ROW_GAP = 4;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 14;
    private static final int SECTION_GAP = 8;
    private static final int STEP_BUTTON_WIDTH = 20;
    private static final int COLUMN_WIDTH = 200;
    private static final int COLUMN_GAP = 16;
    private static final int VIEW_TOP = 34;
    private static final int FOOTER_HEIGHT = 36;

    // ---- steps
    private static final float SIZE_STEP = 0.1f;
    private static final float OPACITY_STEP = 0.1f;
    private static final float VOLUME_STEP = 0.1f;
    private static final float HEARTS_STEP = 0.5f;

    private static final long HINT_VISIBLE_MILLIS = 5000;
    private static final long STATUS_VISIBLE_MILLIS = 8000;

    private static final int COLOR_ACCENT = 0xFFFFD24D;
    private static final int COLOR_OK = 0xFF55FF55;
    private static final int COLOR_ERROR = 0xFFFF5555;
    private static final int COLOR_MUTED = 0xFFAAAAAA;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private enum RowKind { SINGLE, STEPPER, SPLIT, TEXT }

    private static final class Row {
        final RowKind kind;
        final AbstractWidget[] widgets;
        final int height;
        int x;
        int y;
        int width;
        boolean visible;

        Row(RowKind kind, int height, AbstractWidget... widgets) {
            this.kind = kind;
            this.height = height;
            this.widgets = widgets;
        }
    }

    private static final class Section {
        final String titleKey;
        final int column;
        final List<Row> rows = new ArrayList<>();
        int x;
        int y;
        int width;
        int height;

        Section(String titleKey, int column) {
            this.titleKey = titleKey;
            this.column = column;
        }
    }

    private enum DragTarget { NONE, STATS, NOTIFICATION }

    private final Screen parent;
    private final AutoTotemConfig config;
    private final List<Section> sections = new ArrayList<>();

    // widgets whose text changes
    private Button enabledButton;
    private Button delayLabel;
    private Button thresholdToggle;
    private Button thresholdLabel;
    private Button firstHitToggle;
    private Button statsToggle;
    private Button statsSizeLabel;
    private Button statsOpacityLabel;
    private Button notificationToggle;
    private Button notificationSizeLabel;
    private Button notificationOpacityLabel;
    private Button volumeLabel;
    private Row soundStatusRow;

    private int scroll = 0;
    private int maxScroll = 0;

    private long hintShownAtMillis;

    private DragTarget dragTarget = DragTarget.NONE;
    private int dragOffsetX;
    private int dragOffsetY;

    private boolean importInProgress = false;
    private String statusKey = "";
    private String statusArgument = "";
    private int statusColor = COLOR_MUTED;
    private long statusShownAtMillis = 0;

    public AutoTotemConfigScreen(Screen parent) {
        super(Component.translatable("screen.autototem.title"));
        this.parent = parent;
        this.config = AutoTotemClient.getConfig();
    }

    // ------------------------------------------------------------------ building

    @Override
    protected void init() {
        sections.clear();
        dragTarget = DragTarget.NONE;

        // Picks up a custom_sound file that was dropped into config/autototem by hand.
        AutoTotemSound.reload(config);

        // --- Auto equip
        Section equip = addSection("screen.autototem.section.equip", 0);
        enabledButton = button(b -> {
            AutoTotemClient.setEnabled(!AutoTotemClient.isEnabled());
            refreshLabels();
        }, "screen.autototem.toggle.tooltip");
        addSingle(equip, enabledButton);
        delayLabel = labelButton("screen.autototem.delay.tooltip");
        addStepper(equip,
                stepButton("-", b -> adjustDelay(-1)), delayLabel, stepButton("+", b -> adjustDelay(1)));

        // --- Health threshold
        Section health = addSection("screen.autototem.section.health", 0);
        thresholdToggle = button(b -> {
            config.healthThreshold.enabled = !config.healthThreshold.enabled;
            save();
        }, "screen.autototem.health_toggle.tooltip");
        addSingle(health, thresholdToggle);
        thresholdLabel = labelButton("screen.autototem.health_value.tooltip");
        addStepper(health,
                stepButton("-", b -> adjustHearts(-HEARTS_STEP)), thresholdLabel, stepButton("+", b -> adjustHearts(HEARTS_STEP)));
        firstHitToggle = button(b -> {
            config.healthThreshold.afterFirstHit = !config.healthThreshold.afterFirstHit;
            save();
        }, "screen.autototem.first_hit.tooltip");
        addSingle(health, firstHitToggle);

        // --- Stats HUD
        AutoTotemConfig.StatsHudConfig stats = config.statsHud;
        Section statsSection = addSection("screen.autototem.section.stats", 1);
        statsToggle = button(b -> {
            stats.enabled = !stats.enabled;
            save();
        }, null);
        addSingle(statsSection, statsToggle);
        statsSizeLabel = labelButton(null);
        addStepper(statsSection,
                stepButton("-", b -> { stats.scale = clamp(stats.scale - SIZE_STEP, 0.5f, 2.0f); save(); }),
                statsSizeLabel,
                stepButton("+", b -> { stats.scale = clamp(stats.scale + SIZE_STEP, 0.5f, 2.0f); save(); }));
        statsOpacityLabel = labelButton(null);
        addStepper(statsSection,
                stepButton("-", b -> { stats.opacity = clamp(stats.opacity - OPACITY_STEP, 0.0f, 1.0f); save(); }),
                statsOpacityLabel,
                stepButton("+", b -> { stats.opacity = clamp(stats.opacity + OPACITY_STEP, 0.0f, 1.0f); save(); }));
        addSingle(statsSection, plainButton("screen.autototem.reset_position", b -> {
            stats.x = -1;
            stats.y = -1;
            save();
        }));

        // --- Notification HUD
        AutoTotemConfig.NotificationHudConfig notification = config.notificationHud;
        Section notificationSection = addSection("screen.autototem.section.notification", 1);
        notificationToggle = button(b -> {
            notification.enabled = !notification.enabled;
            save();
        }, "screen.autototem.notification_toggle.tooltip");
        addSingle(notificationSection, notificationToggle);
        notificationSizeLabel = labelButton(null);
        addStepper(notificationSection,
                stepButton("-", b -> { notification.scale = clamp(notification.scale - SIZE_STEP, 0.5f, 2.0f); save(); }),
                notificationSizeLabel,
                stepButton("+", b -> { notification.scale = clamp(notification.scale + SIZE_STEP, 0.5f, 2.0f); save(); }));
        notificationOpacityLabel = labelButton(null);
        addStepper(notificationSection,
                stepButton("-", b -> { notification.opacity = clamp(notification.opacity - OPACITY_STEP, 0.0f, 1.0f); save(); }),
                notificationOpacityLabel,
                stepButton("+", b -> { notification.opacity = clamp(notification.opacity + OPACITY_STEP, 0.0f, 1.0f); save(); }));
        addSingle(notificationSection, plainButton("screen.autototem.reset_position", b -> {
            notification.x = -1;
            notification.y = -1;
            save();
        }));

        // --- Notification sound
        Section sound = addSection("screen.autototem.section.sound", 0);
        volumeLabel = labelButton(null);
        addStepper(sound,
                stepButton("-", b -> adjustVolume(-VOLUME_STEP)), volumeLabel, stepButton("+", b -> adjustVolume(VOLUME_STEP)));
        Button importButton = Button.builder(Component.translatable("screen.autototem.sound_import"), b -> startImport())
                .bounds(0, 0, 10, ROW_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("screen.autototem.sound_import.tooltip")))
                .build();
        Button defaultButton = plainButton("screen.autototem.sound_default", b -> {
            AutoTotemSound.useDefault(config);
            setStatus(COLOR_OK, "screen.autototem.sound_reset", "");
        });
        addSplit(sound, importButton, defaultButton);
        addSingle(sound, plainButton("screen.autototem.sound_test", b -> AutoTotemSound.play(config.notificationHud.volume)));
        soundStatusRow = new Row(RowKind.TEXT, TEXT_ROW_HEIGHT);
        sound.rows.add(soundStatusRow);

        // --- Footer (always visible)
        int footerY = this.height - 12 - ROW_HEIGHT;
        int buttonWidth = Math.min(150, (this.width - 24) / 2);
        int footerLeft = this.width / 2 - buttonWidth - 3;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.autototem.reset_all"), b -> resetAll())
                .bounds(footerLeft, footerY, buttonWidth, ROW_HEIGHT)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 + 3, footerY, buttonWidth, ROW_HEIGHT)
                .build());

        refreshLabels();
        layout();
        hintShownAtMillis = System.currentTimeMillis();
    }

    private Section addSection(String titleKey, int column) {
        Section section = new Section(titleKey, column);
        sections.add(section);
        return section;
    }

    private void addSingle(Section section, AbstractWidget widget) {
        this.addRenderableWidget(widget);
        section.rows.add(new Row(RowKind.SINGLE, ROW_HEIGHT, widget));
    }

    private void addStepper(Section section, Button minus, Button label, Button plus) {
        label.active = false; // display only
        this.addRenderableWidget(minus);
        this.addRenderableWidget(label);
        this.addRenderableWidget(plus);
        section.rows.add(new Row(RowKind.STEPPER, ROW_HEIGHT, minus, label, plus));
    }

    private void addSplit(Section section, Button left, Button right) {
        this.addRenderableWidget(left);
        this.addRenderableWidget(right);
        section.rows.add(new Row(RowKind.SPLIT, ROW_HEIGHT, left, right));
    }

    /** A button whose text is filled in by refreshLabels(). */
    private Button button(Button.OnPress onPress, String tooltipKey) {
        Button.Builder builder = Button.builder(Component.empty(), button -> {
            onPress.onPress(button);
            refreshLabels();
        }).bounds(0, 0, 10, ROW_HEIGHT);
        if (tooltipKey != null) {
            builder.tooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
        return builder.build();
    }

    private Button labelButton(String tooltipKey) {
        Button.Builder builder = Button.builder(Component.empty(), button -> { }).bounds(0, 0, 10, ROW_HEIGHT);
        if (tooltipKey != null) {
            builder.tooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
        return builder.build();
    }

    private Button stepButton(String symbol, Button.OnPress onPress) {
        return Button.builder(Component.literal(symbol), button -> {
            onPress.onPress(button);
            refreshLabels();
        }).bounds(0, 0, STEP_BUTTON_WIDTH, ROW_HEIGHT).build();
    }

    private Button plainButton(String labelKey, Button.OnPress onPress) {
        return Button.builder(Component.translatable(labelKey), button -> {
            onPress.onPress(button);
            refreshLabels();
        }).bounds(0, 0, 10, ROW_HEIGHT).build();
    }

    // ------------------------------------------------------------------ actions

    private void save() {
        AutoTotemClient.saveConfig();
    }

    private void adjustDelay(int delta) {
        config.equipDelayTicks = Math.max(0, Math.min(AutoTotemConfig.MAX_DELAY_TICKS, config.equipDelayTicks + delta));
        save();
    }

    private void adjustHearts(float delta) {
        config.healthThreshold.hearts = clamp(config.healthThreshold.hearts + delta,
                AutoTotemConfig.MIN_HEARTS, AutoTotemConfig.MAX_HEARTS);
        save();
    }

    private void adjustVolume(float delta) {
        config.notificationHud.volume = clamp(config.notificationHud.volume + delta, 0.0f, 1.0f);
        save();
    }

    private void resetAll() {
        config.resetToDefaults();
        AutoTotemSound.useDefault(config);
        AutoTotemClient.setEnabled(config.enabled);
        save();
        setStatus(COLOR_OK, "screen.autototem.reset_done", "");
        refreshLabels();
    }

    private static float clamp(float value, float min, float max) {
        // Round to one decimal so repeated +/- steps never drift (0.1 + 0.2 = 0.30000001).
        float rounded = Math.round(value * 100.0f) / 100.0f;
        return Math.max(min, Math.min(max, rounded));
    }

    // ------------------------------------------------------------------ sound import

    private void startImport() {
        if (importInProgress) {
            return;
        }
        importInProgress = true;
        setStatus(COLOR_MUTED, "screen.autototem.sound_waiting", "");

        Minecraft client = this.minecraft;
        Thread thread = new Thread(() -> {
            Path chosen = null;
            boolean unavailable = false;
            try {
                chosen = AutoTotemSound.chooseFile();
            } catch (AutoTotemSound.DialogUnavailableException e) {
                unavailable = true;
            }
            Path result = chosen;
            boolean failed = unavailable;
            client.execute(() -> onFileChosen(result, failed));
        }, "AutoTotem file dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private void onFileChosen(Path chosen, boolean dialogUnavailable) {
        importInProgress = false;
        if (dialogUnavailable) {
            setStatus(COLOR_ERROR, "screen.autototem.sound_err_dialog", "");
            return;
        }
        if (chosen == null) {
            statusShownAtMillis = 0; // cancelled, drop the "waiting" message
            return;
        }
        AutoTotemSound.ImportResult result = AutoTotemSound.importFile(chosen, config);
        setStatus(result.ok() ? COLOR_OK : COLOR_ERROR, result.messageKey(), result.argument());
        refreshLabels();
    }

    private void setStatus(int color, String key, String argument) {
        statusColor = color;
        statusKey = key;
        statusArgument = argument;
        statusShownAtMillis = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ labels

    private void refreshLabels() {
        enabledButton.setMessage(Component.translatable("screen.autototem.toggle_button",
                onOff(AutoTotemClient.isEnabled()), AutoTotemClient.getToggleKeyName()));
        delayLabel.setMessage(Component.translatable("screen.autototem.delay", config.equipDelayTicks));

        thresholdToggle.setMessage(Component.translatable("screen.autototem.health_toggle",
                onOff(config.healthThreshold.enabled)));
        thresholdLabel.setMessage(Component.translatable("screen.autototem.health_value",
                formatNumber(config.healthThreshold.hearts, 1)));
        firstHitToggle.setMessage(Component.translatable("screen.autototem.first_hit",
                onOff(config.healthThreshold.afterFirstHit)));
        thresholdLabel.active = false;
        // The detail settings only matter while the threshold mode is on.
        firstHitToggle.active = config.healthThreshold.enabled;

        statsToggle.setMessage(Component.translatable("screen.autototem.hud_visible", onOff(config.statsHud.enabled)));
        statsSizeLabel.setMessage(Component.translatable("screen.autototem.size", formatNumber(config.statsHud.scale, 2)));
        statsOpacityLabel.setMessage(Component.translatable("screen.autototem.opacity", percent(config.statsHud.opacity)));

        notificationToggle.setMessage(Component.translatable("screen.autototem.hud_visible",
                onOff(config.notificationHud.enabled)));
        notificationSizeLabel.setMessage(Component.translatable("screen.autototem.size",
                formatNumber(config.notificationHud.scale, 2)));
        notificationOpacityLabel.setMessage(Component.translatable("screen.autototem.opacity",
                percent(config.notificationHud.opacity)));

        volumeLabel.setMessage(Component.translatable("screen.autototem.volume", percent(config.notificationHud.volume)));
    }

    private static String onOff(boolean on) {
        return Component.translatable(on ? "screen.autototem.on" : "screen.autototem.off").getString();
    }

    private static String percent(float value) {
        return Math.round(value * 100.0f) + "%";
    }

    private static String formatNumber(float value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    // ------------------------------------------------------------------ layout

    private void layout() {
        int viewTop = VIEW_TOP;
        int viewBottom = this.height - FOOTER_HEIGHT;

        boolean twoColumns = this.width >= 2 * COLUMN_WIDTH + COLUMN_GAP + 24;
        int columnWidth = twoColumns ? COLUMN_WIDTH : Math.min(COLUMN_WIDTH + 40, this.width - 24);
        int totalWidth = twoColumns ? columnWidth * 2 + COLUMN_GAP : columnWidth;
        int left = (this.width - totalWidth) / 2;

        int origin = viewTop - scroll;
        int[] cursor = {origin, origin};

        for (Section section : sections) {
            int column = twoColumns ? section.column : 0;
            section.x = left + column * (columnWidth + COLUMN_GAP);
            section.y = cursor[column];
            section.width = columnWidth;

            int rowY = section.y + PADDING + HEADER_HEIGHT;
            for (Row row : section.rows) {
                placeRow(row, section.x + PADDING, rowY, columnWidth - 2 * PADDING, viewTop, viewBottom);
                rowY += row.height + ROW_GAP;
            }
            section.height = (rowY - ROW_GAP) - section.y + PADDING;
            cursor[column] = section.y + section.height + SECTION_GAP;
        }

        int contentHeight = Math.max(cursor[0], cursor[1]) - SECTION_GAP - origin;
        maxScroll = Math.max(0, contentHeight - (viewBottom - viewTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
            layout();
        }
    }

    private static void placeRow(Row row, int x, int y, int innerWidth, int viewTop, int viewBottom) {
        row.x = x;
        row.y = y;
        row.width = innerWidth;
        row.visible = y >= viewTop && y + row.height <= viewBottom;

        for (AbstractWidget widget : row.widgets) {
            widget.visible = row.visible;
            widget.setY(y);
        }

        switch (row.kind) {
            case SINGLE -> {
                row.widgets[0].setX(x);
                row.widgets[0].setWidth(innerWidth);
            }
            case STEPPER -> {
                row.widgets[0].setX(x);
                row.widgets[0].setWidth(STEP_BUTTON_WIDTH);
                row.widgets[1].setX(x + STEP_BUTTON_WIDTH + ROW_GAP);
                row.widgets[1].setWidth(innerWidth - 2 * (STEP_BUTTON_WIDTH + ROW_GAP));
                row.widgets[2].setX(x + innerWidth - STEP_BUTTON_WIDTH);
                row.widgets[2].setWidth(STEP_BUTTON_WIDTH);
            }
            case SPLIT -> {
                int half = (innerWidth - ROW_GAP) / 2;
                row.widgets[0].setX(x);
                row.widgets[0].setWidth(half);
                row.widgets[1].setX(x + half + ROW_GAP);
                row.widgets[1].setWidth(innerWidth - half - ROW_GAP);
            }
            case TEXT -> {
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY * ROW_HEIGHT)));
            layout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Buttons get the click first; only a click on empty space can grab a HUD box.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        double mx = event.x();
        double my = event.y();

        // The notification box is drawn after the stats box, so it is on top.
        int[] notification = notificationRect();
        if (contains(notification, mx, my)) {
            beginDrag(DragTarget.NOTIFICATION, notification, mx, my);
            return true;
        }
        int[] stats = statsRect();
        if (contains(stats, mx, my)) {
            beginDrag(DragTarget.STATS, stats, mx, my);
            return true;
        }
        return false;
    }

    private void beginDrag(DragTarget target, int[] rect, double mx, double my) {
        dragTarget = target;
        // Remember where inside the box it was grabbed, so it doesn't jump under the cursor.
        dragOffsetX = (int) Math.round(mx) - rect[0];
        dragOffsetY = (int) Math.round(my) - rect[1];
        hintShownAtMillis = System.currentTimeMillis();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragTarget == DragTarget.NONE) {
            return super.mouseDragged(event, dragX, dragY);
        }

        int[] rect = dragTarget == DragTarget.STATS ? statsRect() : notificationRect();
        int x = Math.max(0, Math.min(this.width - rect[2], (int) Math.round(event.x()) - dragOffsetX));
        int y = Math.max(0, Math.min(this.height - rect[3], (int) Math.round(event.y()) - dragOffsetY));

        if (dragTarget == DragTarget.STATS) {
            config.statsHud.x = x;
            config.statsHud.y = y;
        } else {
            config.notificationHud.x = x;
            config.notificationHud.y = y;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragTarget != DragTarget.NONE) {
            dragTarget = DragTarget.NONE;
            save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private static boolean contains(int[] rect, double x, double y) {
        return x >= rect[0] && x < rect[0] + rect[2] && y >= rect[1] && y < rect[1] + rect[3];
    }

    /** {x, y, width, height} of the stats HUD preview. */
    private int[] statsRect() {
        AutoTotemConfig.StatsHudConfig cfg = config.statsHud;
        return new int[]{
                StatsHud.resolveX(cfg), StatsHud.resolveY(cfg),
                StatsHud.getWidth(this.minecraft, cfg, true), StatsHud.getHeight(cfg)
        };
    }

    /** {x, y, width, height} of the notification HUD preview. */
    private int[] notificationRect() {
        AutoTotemConfig.NotificationHudConfig cfg = config.notificationHud;
        return new int[]{
                NotificationHud.resolveX(cfg, this.width), NotificationHud.resolveY(cfg, this.height),
                NotificationHud.getWidth(cfg), NotificationHud.getHeight(cfg)
        };
    }

    @Override
    public void onClose() {
        AutoTotemClient.saveConfig();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Everything that is not a widget is drawn here. The game calls this before it renders the
     * widgets, so the section boxes and HUD previews end up underneath the buttons.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        int viewTop = VIEW_TOP;
        int viewBottom = this.height - FOOTER_HEIGHT;

        for (Section section : sections) {
            drawSection(graphics, section, viewTop, viewBottom);
        }

        drawStatsPreview(graphics, mouseX, mouseY);
        drawNotificationPreview(graphics, mouseX, mouseY);

        graphics.centeredText(this.font, this.title.getString(), this.width / 2, 8, COLOR_WHITE);
        if (System.currentTimeMillis() - hintShownAtMillis < HINT_VISIBLE_MILLIS || dragTarget != DragTarget.NONE) {
            graphics.centeredText(this.font,
                    Component.translatable("screen.autototem.hint").getString(), this.width / 2, 21, COLOR_OK);
        }

        drawScrollbar(graphics, viewTop, viewBottom);
    }

    private void drawSection(GuiGraphicsExtractor graphics, Section section, int viewTop, int viewBottom) {
        int top = Math.max(section.y, viewTop);
        int bottom = Math.min(section.y + section.height, viewBottom);
        if (bottom <= top) {
            return;
        }

        graphics.fill(section.x, top, section.x + section.width, bottom, 0x80000000);
        // thin border
        graphics.fill(section.x, top, section.x + section.width, top + 1, 0x40FFFFFF);
        graphics.fill(section.x, bottom - 1, section.x + section.width, bottom, 0x40FFFFFF);
        graphics.fill(section.x, top, section.x + 1, bottom, 0x40FFFFFF);
        graphics.fill(section.x + section.width - 1, top, section.x + section.width, bottom, 0x40FFFFFF);

        int headerY = section.y + PADDING;
        if (headerY >= viewTop && headerY + this.font.lineHeight <= viewBottom) {
            graphics.text(this.font, Component.translatable(section.titleKey).getString(),
                    section.x + PADDING, headerY, COLOR_ACCENT);
        }

        for (Row row : section.rows) {
            if (row.kind == RowKind.TEXT && row.visible) {
                drawSoundStatus(graphics, row);
            }
        }
    }

    private void drawSoundStatus(GuiGraphicsExtractor graphics, Row row) {
        String current = AutoTotemSound.hasCustom()
                ? Component.translatable("screen.autototem.sound_current_custom", config.notificationHud.customSound).getString()
                : Component.translatable("screen.autototem.sound_current_default").getString();
        graphics.text(this.font, fit(current, row.width), row.x, row.y + 2, COLOR_MUTED);

        boolean showStatus = !statusKey.isEmpty()
                && System.currentTimeMillis() - statusShownAtMillis < STATUS_VISIBLE_MILLIS;
        if (showStatus) {
            String message = Component.translatable(statusKey, statusArgument).getString();
            graphics.text(this.font, fit(message, row.width), row.x, row.y + 2 + this.font.lineHeight + 1, statusColor);
        }
    }

    /** Shortens text with "..." so it never runs out of its box. */
    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String result = text;
        while (!result.isEmpty() && this.font.width(result + ellipsis) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private void drawStatsPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] rect = statsRect();
        StatsHud.renderAt(graphics, this.minecraft, config.statsHud, rect[0], rect[1], true);
        drawPreviewFrame(graphics, rect, config.statsHud.enabled, DragTarget.STATS, mouseX, mouseY);
    }

    private void drawNotificationPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] rect = notificationRect();
        NotificationHud.renderAt(graphics, this.minecraft, config.notificationHud, rect[0], rect[1],
                config.notificationHud.opacity);
        drawPreviewFrame(graphics, rect, config.notificationHud.enabled, DragTarget.NOTIFICATION, mouseX, mouseY);
    }

    /** Frame colours: yellow while dragging, light blue on hover, white normally, grey when the HUD is off. */
    private void drawPreviewFrame(GuiGraphicsExtractor graphics, int[] rect, boolean hudEnabled, DragTarget target,
                                  int mouseX, int mouseY) {
        int color;
        if (dragTarget == target) {
            color = COLOR_ACCENT;
        } else if (contains(rect, mouseX, mouseY)) {
            color = 0xFF55AAFF;
        } else if (hudEnabled) {
            color = COLOR_WHITE;
        } else {
            color = 0xFF777777;
        }

        if (!hudEnabled) {
            // Dim the box a little so it is obvious that this HUD is currently switched off.
            graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 0x80000000);
        }

        int x = rect[0] - 1;
        int y = rect[1] - 1;
        int w = rect[2] + 2;
        int h = rect[3] + 2;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int viewTop, int viewBottom) {
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = viewBottom - viewTop;
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
        int thumbY = viewTop + (trackHeight - thumbHeight) * scroll / maxScroll;
        int x = this.width - 6;
        graphics.fill(x, viewTop, x + 3, viewBottom, 0x40FFFFFF);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xC0FFFFFF);
    }
}
