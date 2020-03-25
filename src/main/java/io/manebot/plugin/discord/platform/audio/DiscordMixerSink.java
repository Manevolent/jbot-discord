package io.manebot.plugin.discord.platform.audio;

import io.manebot.plugin.audio.mixer.output.AbstractOpusMixerSink;
import io.manebot.plugin.audio.opus.OpusParameters;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

public class DiscordMixerSink extends AbstractOpusMixerSink implements AudioSendHandler {
    public DiscordMixerSink(AudioFormat audioFormat,
                            OpusParameters opusParameters,
                            int bufferSizeInBytes) {
        super(audioFormat, opusParameters, bufferSizeInBytes);
    }

    @Override
    public boolean canProvide() {
        return isReady();
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(DiscordMixerSink.this.provide());
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
