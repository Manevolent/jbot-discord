package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.Community;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.Collection;
import java.util.stream.Collectors;

public class DiscordGuildChannel extends BaseDiscordChannel {
    private final TextChannel channel;

    public DiscordGuildChannel(DiscordPlatformConnection platformConnection,
                               TextChannel textChannel) {
        super(platformConnection, textChannel);

        this.channel = textChannel;
    }

    public DiscordGuildConnection getGuildConnection() {
        return getPlatformConnection().getGuildConnection(channel.getGuild());
    }

    @Override
    public Community getCommunity() {
        return getGuildConnection();
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public Collection<String> getPlatformUserIds() {
        return channel.getMembers()
                .stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        return channel.getMembers()
                .stream()
                .map(member -> getPlatformConnection().getPlatformUser(member.getUser()))
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return channel.getName();
    }

    @Override
    public void setName(String name) throws UnsupportedOperationException {
        channel.getManager().setName(name).submit();
    }

    @Override
    public String getTopic() {
        return channel.getTopic();
    }

    @Override
    public void setTopic(String topic) throws UnsupportedOperationException {
        channel.getManager().setTopic(topic).submit();
    }

    @Override
    public void removeMember(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMember(String s) {
        throw new UnsupportedOperationException();
    }

}
