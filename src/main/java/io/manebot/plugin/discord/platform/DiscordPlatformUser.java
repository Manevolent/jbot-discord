package io.manebot.plugin.discord.platform;

import io.manebot.chat.Chat;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformConnection;
import io.manebot.platform.PlatformUser;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.User;

import java.util.Collection;

public class DiscordPlatformUser implements PlatformUser {
    private final DiscordPlatformConnection connection;
    private final User user;

    public DiscordPlatformUser(DiscordPlatformConnection connection, User user) {
        this.connection = connection;
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Platform getPlatform() {
        return connection.getPlatform();
    }

    @Override
    public PlatformConnection getConnection() {
        return connection;
    }

    @Override
    public String getId() {
        return user.getId().asString();
    }

    @Override
    public String getNickname() {
        return user.getUsername();
    }

    @Override
    public void setNickname(String nickname) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSelf() {
        return user.isBot();
    }

    @Override
    public boolean isIgnored() {
        return false;
    }

    @Override
    public void setIgnored(boolean ignored) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Chat> getChats() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Chat getPrivateChat() {
        PrivateChannel privateChannel = user.getPrivateChannel().block();
        if (privateChannel == null) return null;
        return connection.getChat(privateChannel.getId().asString());
    }

    @Override
    public Status getStatus() {
        return Status.UNKNOWN;
    }
}
