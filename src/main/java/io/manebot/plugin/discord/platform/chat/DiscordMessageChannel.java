package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.ChatMessage;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;

import discord4j.core.object.entity.MessageChannel;

import java.util.Collection;

public abstract class DiscordMessageChannel extends BaseDiscordChannel {
    private final MessageChannel messageChannel;

    public DiscordMessageChannel(DiscordPlatformConnection platformConnection,
                                 MessageChannel messageChannel) {
        super(platformConnection, messageChannel);

        this.messageChannel = messageChannel;
    }

    @Override
    public Collection<ChatMessage> getLastMessages(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canChangeTypingStatus() {
        return true;
    }

    @Override
    public void setTyping(boolean b) {

    }

    @Override
    public boolean isTyping() {

        return false;
    }

    @Override
    public boolean canSendMessages() {
        return true;
    }

    @Override
    public boolean canSendRichMessages() {
        return true;
    }

    @Override
    public boolean canReceiveMessages() {
        return true;
    }

    @Override
    public boolean canSendEmoji() {
        return true;
    }
}
