package net.autototem;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

/**
 * A sound whose audio comes from the user's own file instead of a resource pack.
 * The entry in assets/autototem/sounds.json is only a placeholder (Fabric's sound API
 * lets a sound instance supply its own audio stream).
 */
final class CustomTotemSoundInstance extends AbstractSoundInstance {

    private static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath(AutoTotemClient.MOD_ID, "custom_totem_sound");

    private final AutoTotemSound.PcmSound pcm;

    CustomTotemSoundInstance(AutoTotemSound.PcmSound pcm, float volume) {
        super(SOUND_ID, SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.pcm = pcm;
        this.volume = volume;
        // Relative to the listener at position (0, 0, 0): heard at full volume, like a UI sound.
        this.relative = true;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
        return CompletableFuture.completedFuture(new PcmAudioStream(pcm.format(), pcm.data()));
    }
}
