package io.manebot.plugin.discord.audio.channel;

import io.manebot.plugin.audio.mixer.output.AbstractOpusMixerSink;
import io.manebot.plugin.audio.opus.OpusParameters;
import discord4j.voice.AudioProvider;

import javax.sound.sampled.AudioFormat;

public class DiscordMixerSink extends AbstractOpusMixerSink {
    private final AudioProvider provider = new ProviderAdapter();

    public DiscordMixerSink(AudioFormat audioFormat,
                            OpusParameters opusParameters,
                            int bufferSizeInBytes) {
        super(audioFormat, opusParameters, bufferSizeInBytes);
    }

    /**
     * Gets the Discord4J-friendly provider instance.
     * @return AudioProvider instance.
     */
    public AudioProvider getProvider() {
        return provider;
    }

    private class ProviderAdapter extends AudioProvider {
        @Override
        public boolean provide() {
            if (isReady()) {
                getBuffer().clear().put(DiscordMixerSink.this.provide()).flip();
                return true;
            }

            return false;
        }
    }
}
