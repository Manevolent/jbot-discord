package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.DefaultChatSender;

import io.manebot.plugin.discord.platform.user.DiscordPlatformUser;

public class DiscordChatSender extends DefaultChatSender {
    private final DiscordPlatformUser user;
    private final BaseDiscordChannel chat;

    public DiscordChatSender(DiscordPlatformUser user, BaseDiscordChannel chat) {
        super(user, chat);

        this.user = user;
        this.chat = chat;
    }

    @Override
    public DiscordPlatformUser getPlatformUser() {
        return user;
    }

    @Override
    public BaseDiscordChannel getChat() {
        return chat;
    }
}
