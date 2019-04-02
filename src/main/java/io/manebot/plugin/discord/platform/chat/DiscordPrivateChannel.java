package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.ChatMessage;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.discord.platform.DiscordPlatformUser;
import discord4j.core.object.entity.*;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiscordPrivateChannel extends DiscordMessageChannel {
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
    public Collection<PlatformUser> getPlatformUsers() {
        // Find all guild members who have access to view the channel
        return channel.getRecipientIds().stream()
                .map(id -> getPlatformConnection().getPlatformUser(id.asString()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessage sendMessage(Consumer<ChatMessage.Builder> consumer) {
        DiscordPlatformUser self = getPlatformConnection().getSelf();
        return new DiscordChatMessage(
                getPlatformConnection(),
                new DiscordChatSender(self, this),
                Objects.requireNonNull(
                        channel.createMessage(builder ->
                                consumer.accept(new DiscordChatMessage.CreateBuilder(self, this, builder))
                        ).block()
                )
        );
    }
}
