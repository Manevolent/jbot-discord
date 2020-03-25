package io.manebot.plugin.discord.platform.audio;

import io.manebot.conversation.Conversation;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.channel.AudioChannelRegistrant;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.discord.platform.user.DiscordPlatformUser;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;
import io.manebot.user.UserAssociation;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DiscordAudioChannel extends AudioChannel {
    private final DiscordGuildConnection connection;

    public DiscordAudioChannel(
            DiscordGuildConnection connection,
            Mixer mixer,
            AudioChannelRegistrant owner) {
        super(mixer, owner);

        this.connection = connection;
    }

    @Override
    public String getId() {
        return "discord:" + connection.getId();
    }

    @Override
    public Platform getPlatform() {
        return connection.getPlatform();
    }

    @Override
    public List<PlatformUser> getMembers() {
        VoiceChannel channel = this.connection.getGuild().getAudioManager().getConnectedChannel();
        if (channel == null)
            return Collections.emptyList();

        return channel.getMembers()
                .stream()
                .map(member -> connection.getPlatformConnection().getPlatformUser(member.getUser()))
                .collect(Collectors.toList());
    }

    @Override
    public List<PlatformUser> getListeners() {
        VoiceChannel channel = this.connection.getGuild().getAudioManager().getConnectedChannel();
        if (channel == null)
            return Collections.emptyList();

        return channel.getMembers()
                .stream()
                .map(Member::getVoiceState)
                .filter(Objects::nonNull)
                .filter(guildVoiceState -> !guildVoiceState.isDeafened())
                .map(member -> connection.getPlatformConnection().getPlatformUser(member.getMember().getUser()))
                .collect(Collectors.toList());
    }

    @Override
    public Conversation getConversation() {
        return connection.getDefaultConversation();
    }

    public void disconnect() {
        AudioManager audioManager = this.connection.getGuild().getAudioManager();
        if (audioManager.isConnected())
            audioManager.closeAudioConnection();
    }

    @Override
    public AudioChannel.Ownership obtainChannel(UserAssociation association) {
        DiscordPlatformUser platformUser;

        if (!(association.getPlatformUser() instanceof DiscordPlatformUser))
            throw new IllegalArgumentException("User is not obtaining channel from Discord.");

        platformUser = (DiscordPlatformUser) association.getPlatformUser();

        Member member = connection.getGuild().getMemberById(platformUser.getId());
        if (member == null)
            throw new IllegalArgumentException("User is not recognized in this guild.");

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null)
            throw new IllegalArgumentException("User is not connected to a voice channel.");

        VoiceChannel channel = voiceState.getChannel();
        if (channel == null)
            throw new IllegalArgumentException("User is not connected to a voice channel.");

        AudioChannel.Ownership ownership = this.obtain(association);
        try {
            this.connection.getGuild().getAudioManager().openAudioConnection(channel);

            /*if (getBlockingPlayers() <= 0 || isIdle()) {
                this.channel = channel;
            }*/

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
