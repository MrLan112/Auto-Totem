<p align="center"><img src="docs/icon.png" width="128" alt="Auto Totem icon"></p>

# Auto Totem

A small client-side Fabric mod that keeps a Totem of Undying in your offhand so you don't have to fumble with the inventory in the middle of a fight.

- Minecraft: **26.1.x (26.1, 26.1.1, 26.1.2)**
- Loader: Fabric (needs Fabric API)
- Mod Menu: optional, adds a config button
- Client only, nothing to install on the server

Download: [Modrinth](https://modrinth.com/mod/autototemmod) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/just-auto-totem) · Chat: [Discord](https://discord.gg/7qzAw5Cckx) · Bugs: [Issues](https://github.com/MrLan112/Auto-Totem/issues)

## What it does

When your offhand is empty (for example right after a totem pops), the mod finds another totem in your inventory or hotbar and swaps it into the offhand. That's the whole idea; everything else is optional tuning.

Default keys:

| Key | Action |
| --- | --- |
| `O` | Turn the mod on/off |
| `B` | Open the settings screen |

Both can be rebound in the normal Controls menu under "Auto Totem".

## Settings

Everything lives on one screen, split into sections. Both HUD previews are visible at the same time, and you can drag them where you want.

**Auto equip**
- Delay before the totem is equipped, in ticks (0-40, 20 ticks = 1 second). 0 means as fast as possible.

**Health threshold** (off by default)
- When enabled, the totem is only moved to your offhand once your health drops to the chosen number of hearts or lower. When your health recovers, the totem goes back to the slot it came from.
- "Move after the first hit" - the swap also happens right after you take damage, without waiting for the threshold.

**Stats HUD** (off by default)
- Shows how many totems you have left and when the last one was used. Position, size and opacity are adjustable.

**Notification HUD**
- A short "Totem used" message. Position, size and opacity are adjustable.

**Notification sound**
- Plays when a totem is used. By default it's the regular totem sound.
- You can import your own sound file from your computer. Once a custom sound is set, the vanilla totem sound for your own totem is muted so the two don't overlap.
- Volume slider.
- Supported formats: WAV, AIFF, AU, up to 30 seconds. OGG and MP3 are not supported.
- Imported files are copied to `config/autototem/`.

Settings are saved to `config/autototem.json`.

## Good to know

- Throwing a totem out with `Q` is counted as "used", because the mod can only see that the totem count went down.
- Many servers don't like automatic inventory actions. Check the rules before using this on one, you're responsible for what you use.
- In Creative and Spectator the mod does nothing.

## Building

You need JDK 25.

```
./gradlew build
```

On Windows use `gradlew.bat build` or `.\gradlew build`. The jar ends up in `build/libs/`.

To start a dev client: `./gradlew runClient`.

## License

MIT, see [LICENSE](LICENSE).
