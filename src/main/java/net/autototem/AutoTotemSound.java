package net.autototem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * Plays the "totem used" sound and manages the user's custom replacement for it.
 *
 * The game's own audio code only reads OGG files from resource packs, so a custom sound is
 * decoded here with javax.sound (WAV, AIFF and AU) into 16-bit PCM and streamed to the engine.
 * The imported file is copied to config/autototem/ so it survives the original being moved.
 */
public final class AutoTotemSound {

    private AutoTotemSound() {
    }

    /** Decoded 16-bit little-endian PCM audio. */
    public record PcmSound(AudioFormat format, byte[] data) {
    }

    /** Outcome of an import attempt; {@code messageKey} is a lang key, {@code argument} fills its %s. */
    public record ImportResult(boolean ok, String messageKey, String argument) {
        static ImportResult success(String fileName) {
            return new ImportResult(true, "screen.autototem.sound_imported", fileName);
        }

        static ImportResult failure(String messageKey) {
            return new ImportResult(false, messageKey, "");
        }
    }

    private static final String STORED_BASE_NAME = "custom_sound";
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("wav", "wave", "aif", "aiff", "au");
    private static final float MAX_SECONDS = 30.0f;
    private static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;

    /** The decoded custom sound, or null while the vanilla sound is in use. */
    private static volatile PcmSound custom;

    private static final class SoundImportException extends Exception {
        private final String messageKey;

        SoundImportException(String messageKey) {
            super(messageKey);
            this.messageKey = messageKey;
        }
    }

    /** Thrown when the system file dialog cannot be used on this machine. */
    public static final class DialogUnavailableException extends Exception {
        DialogUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    public static boolean hasCustom() {
        return custom != null;
    }

    // ---------------------------------------------------------------- loading

    /**
     * Loads the custom sound named in the config. If none is configured but a file called
     * custom_sound.wav (or .aiff/.au) sits in config/autototem/, that one is picked up.
     */
    public static void reload(AutoTotemConfig config) {
        custom = null;

        String configured = config.notificationHud.customSound;
        if (configured != null && !configured.isEmpty()) {
            Path file = soundDir().resolve(Path.of(configured).getFileName().toString());
            if (tryLoad(file)) {
                return;
            }
            config.notificationHud.customSound = "";
            AutoTotemConfig.save(config);
        }

        for (String extension : SUPPORTED_EXTENSIONS) {
            Path file = soundDir().resolve(STORED_BASE_NAME + "." + extension);
            if (Files.isRegularFile(file) && tryLoad(file)) {
                config.notificationHud.customSound = file.getFileName().toString();
                AutoTotemConfig.save(config);
                return;
            }
        }
    }

    private static boolean tryLoad(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            custom = decode(file);
            return true;
        } catch (SoundImportException | IOException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- import / reset

    public static ImportResult importFile(Path source, AutoTotemConfig config) {
        String fileName = source.getFileName().toString();
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            return ImportResult.failure("screen.autototem.sound_err_format");
        }

        try {
            if (Files.size(source) > MAX_SOURCE_BYTES) {
                return ImportResult.failure("screen.autototem.sound_err_long");
            }

            PcmSound decoded = decode(source);

            Files.createDirectories(soundDir());
            Path destination = soundDir().resolve(STORED_BASE_NAME + "." + extension);
            boolean alreadyInPlace = Files.exists(destination) && Files.isSameFile(source, destination);

            deleteStoredFiles(alreadyInPlace ? destination : null);
            if (!alreadyInPlace) {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            custom = decoded;
            config.notificationHud.customSound = destination.getFileName().toString();
            AutoTotemConfig.save(config);
            return ImportResult.success(fileName);
        } catch (SoundImportException e) {
            return ImportResult.failure(e.messageKey);
        } catch (IOException e) {
            return ImportResult.failure("screen.autototem.sound_err_io");
        }
    }

    /** Goes back to the vanilla totem sound and removes the stored copy. */
    public static void useDefault(AutoTotemConfig config) {
        custom = null;
        config.notificationHud.customSound = "";
        deleteStoredFiles(null);
        AutoTotemConfig.save(config);
    }

    private static void deleteStoredFiles(Path keep) {
        for (String extension : SUPPORTED_EXTENSIONS) {
            Path file = soundDir().resolve(STORED_BASE_NAME + "." + extension);
            if (keep != null && file.equals(keep)) {
                continue;
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Leftover file is harmless, the config no longer points at it.
            }
        }
    }

    private static Path soundDir() {
        return AutoTotemConfig.soundDir();
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- decoding

    private static PcmSound decode(Path file) throws SoundImportException, IOException {
        try (InputStream raw = Files.newInputStream(file);
             InputStream buffered = new java.io.BufferedInputStream(raw);
             AudioInputStream source = AudioSystem.getAudioInputStream(buffered)) {

            AudioFormat sourceFormat = source.getFormat();
            int channels = sourceFormat.getChannels();
            float sampleRate = sourceFormat.getSampleRate();
            if (channels < 1 || channels > 2 || sampleRate <= 0) {
                throw new SoundImportException("screen.autototem.sound_err_decode");
            }

            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);

            AudioInputStream pcm;
            if (sourceFormat.matches(target)) {
                pcm = source;
            } else {
                try {
                    pcm = AudioSystem.getAudioInputStream(target, source);
                } catch (IllegalArgumentException e) {
                    throw new SoundImportException("screen.autototem.sound_err_decode");
                }
            }

            long maxBytes = (long) (MAX_SECONDS * sampleRate) * target.getFrameSize();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int read;
            while ((read = pcm.read(chunk)) != -1) {
                out.write(chunk, 0, read);
                if (out.size() > maxBytes) {
                    throw new SoundImportException("screen.autototem.sound_err_long");
                }
            }

            byte[] data = out.toByteArray();
            if (data.length == 0) {
                throw new SoundImportException("screen.autototem.sound_err_decode");
            }
            return new PcmSound(target, data);
        } catch (UnsupportedAudioFileException e) {
            throw new SoundImportException("screen.autototem.sound_err_format");
        }
    }

    // ---------------------------------------------------------------- playing

    /** Plays the current notification sound: the custom one if there is one, vanilla otherwise. */
    public static void play(float volume) {
        Minecraft client = Minecraft.getInstance();
        PcmSound sound = custom;
        if (sound != null) {
            client.getSoundManager().play(new CustomTotemSoundInstance(sound, volume));
        } else {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.0F, volume));
        }
    }

    /**
     * Used by the sound engine mixin. The vanilla pop sound is muted only when a custom sound
     * will be played in its place, and only for the local player's own totem (the sound is
     * played right where the player stands).
     */
    public static boolean shouldMute(SoundInstance sound) {
        AutoTotemConfig config = AutoTotemClient.getConfig();
        if (custom == null || config == null || !config.notificationHud.enabled) {
            return false;
        }
        if (!sound.getIdentifier().equals(SoundEvents.TOTEM_USE.location())) {
            return false;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        double dx = sound.getX() - player.getX();
        double dy = sound.getY() - player.getY();
        double dz = sound.getZ() - player.getZ();
        return dx * dx + dy * dy + dz * dz < 4.0;
    }

    /** A sound with zero volume: the engine refuses to start those. */
    public static SoundInstance silentSound() {
        return SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.0F, 0.0F);
    }

    // ---------------------------------------------------------------- file dialog

    /**
     * Opens the operating system's "open file" dialog and blocks until it is closed.
     * Returns null if the user cancelled. Call this from a background thread.
     *
     * The dialog comes from LWJGL's tinyfd module, which the game already ships; it is looked up
     * by name so the mod still loads if it is ever missing.
     */
    public static Path chooseFile() throws DialogUnavailableException {
        try {
            Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Class<?> pointerBuffer = Class.forName("org.lwjgl.PointerBuffer");
            Method open = dialogs.getMethod("tinyfd_openFileDialog",
                    CharSequence.class, CharSequence.class, pointerBuffer, CharSequence.class, boolean.class);

            Object result = open.invoke(null, "Select a sound file (WAV, AIFF or AU)", "", null, "Audio files", false);
            if (result instanceof String path && !path.isBlank()) {
                return Path.of(path);
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new DialogUnavailableException(e);
        }
    }
}
