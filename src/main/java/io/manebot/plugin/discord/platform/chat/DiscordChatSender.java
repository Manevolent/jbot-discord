package io.manebot.plugin.discord.platform.chat;



import io.manebot.chat.DefaultChatSender;

import io.manebot.plugin.discord.platform.DiscordPlatformUser;

public class DiscordChatSender extends DefaultChatSender {
    private final DiscordPlatformUser user;
    private final DiscordMessageChannel chat;

    public DiscordChatSender(DiscordPlatformUser user, DiscordMessageChannel chat) {
        super(user, chat);

        this.user = user;
        this.chat = chat;
    }

    @Override
    public DiscordPlatformUser getPlatformUser() {
        return user;
    }

    @Override
    public DiscordMessageChannel getChat() {
        return chat;
    }
}
