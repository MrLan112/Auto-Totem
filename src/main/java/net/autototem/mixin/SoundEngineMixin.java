package net.autototem.mixin;

import net.autototem.AutoTotemSound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mutes the vanilla totem sound for the local player while a custom sound is in use.
 * Instead of cancelling the call we swap in a zero-volume sound, which the engine skips on its own.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @ModifyVariable(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private SoundInstance autototem$muteVanillaTotemSound(SoundInstance sound) {
        return AutoTotemSound.shouldMute(sound) ? AutoTotemSound.silentSound() : sound;
    }
}
