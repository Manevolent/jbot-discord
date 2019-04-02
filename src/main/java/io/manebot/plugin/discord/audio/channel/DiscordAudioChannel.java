package io.manebot.plugin.discord.audio.channel;

import io.manebot.conversation.Conversation;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.channel.AudioChannelRegistrant;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.discord.platform.DiscordPlatformUser;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;
import io.manebot.user.UserAssociation;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.VoiceChannel;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DiscordAudioChannel extends AudioChannel {
    private final DiscordGuildConnection connection;
    private final AudioProvider audioProvider;

    /**
     * Active connection state to Discord voice
     */
    private VoiceConnection voiceConnection;
    private VoiceChannel channel;

    public DiscordAudioChannel(
            DiscordGuildConnection connection,
            Mixer mixer,
            AudioProvider provider,
            AudioChannelRegistrant owner) {
        super(mixer, owner);
        this.connection = connection;
        this.audioProvider = provider;
    }

    @Override
    public String getId() {
        return "discord:" + connection.getStringID();
    }

    @Override
    public Platform getPlatform() {
        return connection.getPlatformConnection().getPlatform();
    }

    @Override
    public List<PlatformUser> getListeners() {
        VoiceChannel channel = this.channel;

        if (channel == null)
            return Collections.emptyList();

        return channel.getVoiceStates()
                .map(state -> state.getUserId().asString())
                .toStream()
                .map(id -> connection.getPlatformConnection().getPlatformUser(id))
                .collect(Collectors.toList());
    }

    @Override
    public Conversation getConversation() {
        return connection.getDefaultConversation();
    }

    public void disconnect() {
        if (voiceConnection != null) {
            voiceConnection.disconnect();
            voiceConnection = null;
        }
    }

    @Override
    public AudioChannel.Ownership obtainChannel(UserAssociation association) {
        DiscordPlatformUser platformUser;

        if (!(association.getPlatformUser() instanceof DiscordPlatformUser))
            throw new IllegalArgumentException("User is not obtaining channel from Discord.");

        platformUser = (DiscordPlatformUser) association.getPlatformUser();

        Member member = connection.getMember(platformUser);
        if (member == null)
            throw new IllegalArgumentException("User is not recognized in this guild.");

        VoiceState voiceState = member.getVoiceState().block();
        if (voiceState == null)
            throw new IllegalArgumentException("User is not connected to a voice channel.");

        VoiceChannel channel = voiceState.getChannel().block();
        if (channel == null)
            throw new IllegalArgumentException("User is not connected to a voice channel.");

        AudioChannel.Ownership ownership = this.obtain(association);
        try {
            if (getBlockingPlayers() <= 0 || isIdle()) {
               this.voiceConnection = channel.join(join -> join.setProvider(this.audioProvider)).block();
               this.channel = channel;
            }

            return ownership;
        } catch (Throwable ex) {
            try {
                ownership.close();
            } catch (Exception e) {
                RuntimeException suppressed =
                        new RuntimeException("Problem closing obtained session during exception handler", e);

                suppressed.addSuppressed(ex);
                throw suppressed;
            }

            throw ex;
        }
    }
}
