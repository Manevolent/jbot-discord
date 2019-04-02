package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.Chat;
import io.manebot.platform.Platform;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import discord4j.core.object.entity.Channel;

public abstract class BaseDiscordChannel implements Chat {
    private final DiscordPlatformConnection connection;
    private final Channel channel;

    public BaseDiscordChannel(DiscordPlatformConnection connection, Channel channel) {
        this.connection = connection;
        this.channel = channel;
    }

    @Override
    public DiscordPlatformConnection getPlatformConnection() {
        return connection;
    }

    @Override
    public Platform getPlatform() {
        return connection.getPlatform();
    }

    @Override
    public String getId() {
        return channel.getId().asString();
    }

    @Override
    public boolean isConnected() {
        return channel.getClient().isConnected();
    }

    @Override
    public boolean isBuffered() {
        return true;
    }
}
