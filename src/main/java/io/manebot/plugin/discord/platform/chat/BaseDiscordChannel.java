package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.Chat;
import io.manebot.chat.TextFormat;
import io.manebot.chat.ChatMessage;
import io.manebot.platform.Platform;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class BaseDiscordChannel implements Chat {
    private final DiscordPlatformConnection connection;
    private final MessageChannel channel;

    public BaseDiscordChannel(DiscordPlatformConnection connection, MessageChannel channel) {
        this.connection = connection;
        this.channel = channel;
    }

    public MessageChannel getChannel() {
        return channel;
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
        return channel.getId();
    }

    @Override
    public boolean isConnected() {
        return channel.getJDA().getStatus() == JDA.Status.CONNECTED;
    }

    @Override
    public boolean isBuffered() {
        return true;
    }

    @Override
    public ChatMessage sendMessage(Consumer<ChatMessage.Builder> consumer) {
        net.dv8tion.jda.core.MessageBuilder builder = new net.dv8tion.jda.core.MessageBuilder();
        consumer.accept(new DiscordChatMessage.MessageBuilder(getPlatformConnection().getSelf(), this, builder));
        Message createdMessage = channel.sendMessage(builder.build()).complete();

        return new DiscordChatMessage(
                getPlatformConnection(),
                new DiscordChatSender(getPlatformConnection().getSelf(), this),
                createdMessage
        );
    }

    @Override
    public Collection<ChatMessage> getLastMessages(int max) {
        return channel.getHistory().retrievePast(max).complete().stream().map(message -> new DiscordChatMessage(
                getPlatformConnection(),
                new DiscordChatSender(
                        getPlatformConnection().getPlatformUser(message.getAuthor()),
                        this
                ),
                message
        )).collect(Collectors.toList());
    }

    @Override
    public boolean canChangeTypingStatus() {
        return true;
    }

    @Override
    public void setTyping(boolean typing) {
        if (typing)
            channel.sendTyping().complete();
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
    public boolean canSendEmbeds() {
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

    @Override
    public TextFormat getFormat() {
        return DiscordTextFormat.INSTANCE;
    }
}
