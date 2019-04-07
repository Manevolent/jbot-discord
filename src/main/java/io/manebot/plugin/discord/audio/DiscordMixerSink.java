package io.manebot.plugin.discord.audio;

import io.manebot.plugin.audio.mixer.output.AbstractOpusMixerSink;
import io.manebot.plugin.audio.opus.OpusParameters;

import net.dv8tion.jda.core.audio.AudioSendHandler;

import javax.sound.sampled.AudioFormat;

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
    public byte[] provide20MsAudio() {
        return DiscordMixerSink.this.provide();
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
