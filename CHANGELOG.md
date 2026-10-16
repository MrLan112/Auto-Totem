# Changelog

## 2.1.0 (Minecraft 1.21.1)

New
- Equip delay: choose how many ticks to wait before the totem goes to your offhand (0 = as fast as possible).
- Health threshold mode (off by default). The totem is moved to your offhand only when your health drops to the
  threshold you set, and goes back to its slot once you have healed. Optional "equip after first hit" on top of that.
- Custom notification sound: import a WAV, AIFF or AU file from your computer. While it is in use, the normal totem
  sound is muted so the two don't overlap. Volume is adjustable.
- The settings screen is now a single page split into boxes. Both HUDs are visible and draggable at the same time.

Changed
- The stats HUD is off by default. Existing configs are switched off once on first launch of this version.

Fixed
- The dark background of the settings screen was drawn on top of the buttons.
- The HUD "size" setting scaled the box but not the text. Now both scale.
- The key category showed up as a raw translation key in the controls menu.
- The stats HUD used hard-coded Russian units regardless of the game language.
- Respawning or changing dimension could count as a totem being used.
- The auto-equip no longer fires in creative and spectator mode, where it only caused desyncs.

## 2.0.1

- Settings screen, Mod Menu support, stats HUD and notification HUD.
