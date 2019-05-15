package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.ChatMessage;
import io.manebot.chat.Community;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import net.dv8tion.jda.core.entities.PrivateChannel;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DiscordPrivateChannel extends BaseDiscordChannel {
    private final PrivateChannel channel;

    public DiscordPrivateChannel(DiscordPlatformConnection platformConnection,
                                 PrivateChannel channel) {
        super(platformConnection, channel);

        this.channel = channel;
    }

    @Override
    public boolean isPrivate() {
        return true;
    }

    @Override
    public void setName(String s) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTopic() {
        return null;
    }

    @Override
    public void setTopic(String topic) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeMember(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMember(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Community getCommunity() {
        return null;
    }

    @Override
    public Collection<String> getPlatformUserIds() {
        return Collections.unmodifiableCollection(Arrays.asList(
                channel.getUser().getId(),
                getPlatformConnection().getSelf().getId()
        ));
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        return Collections.unmodifiableCollection(Arrays.asList(
                getPlatformConnection().getPlatformUser(channel.getUser()),
                getPlatformConnection().getSelf()
        ));
    }

}
