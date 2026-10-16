package net.autototem;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * Feeds already decoded 16-bit PCM to the game's sound engine.
 * An empty buffer tells the engine that the sound has finished.
 */
final class PcmAudioStream implements AudioStream {

    private final AudioFormat format;
    private final byte[] data;
    private int position = 0;

    PcmAudioStream(AudioFormat format, byte[] data) {
        this.format = format;
        this.data = data;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int capacity) {
        int frameSize = Math.max(1, format.getFrameSize());
        int length = Math.min(capacity, data.length - position);
        length -= length % frameSize; // never hand over half a sample frame

        ByteBuffer buffer = BufferUtils.createByteBuffer(Math.max(length, 0));
        if (length > 0) {
            buffer.put(data, position, length);
            position += length;
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void close() {
    }
}
