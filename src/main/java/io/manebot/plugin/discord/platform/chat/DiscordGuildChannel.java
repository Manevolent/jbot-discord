package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.ChatMessage;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.discord.platform.DiscordPlatformUser;
import discord4j.core.object.entity.*;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

public class DiscordGuildChannel extends DiscordMessageChannel {
    private final TextChannel textChannel;

    public DiscordGuildChannel(DiscordPlatformConnection platformConnection,
                               TextChannel textChannel) {
        super(platformConnection, textChannel);

        this.textChannel = textChannel;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public String getName() {
        return textChannel.getName();
    }

    @Override
    public void setName(String s) throws UnsupportedOperationException {
        textChannel.edit(spec -> spec.setName(s)).block();
    }

    @Override
    public String getTopic() {
        return textChannel.getTopic().orElse(null);
    }

    @Override
    public void setTopic(String topic) throws UnsupportedOperationException {
        textChannel.edit(spec -> spec.setTopic(topic)).block();
    }

    @Override
    public void removeMember(String s) {
        textChannel.getGuild()
                .flatMap(guild -> guild.getMemberById(Snowflake.of(s)))
                .flatMap(Member::kick).block();
    }

    @Override
    public void addMember(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<ChatMessage> getLastMessages(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        // Find all guild members who have access to view the channel
        return textChannel.getGuild()
                .flatMapMany(Guild::getMembers)
                .filterWhen(member -> textChannel
                        .getEffectivePermissions(member.getId())
                        .map(permissions -> permissions.contains(Permission.VIEW_CHANNEL))
                ).map(member -> getPlatformConnection().getPlatformUser(member.getId().asString()))
                .collectList()
                .block();
    }

    @Override
    public ChatMessage sendMessage(Consumer<ChatMessage.Builder> consumer) {
        DiscordPlatformUser self = getPlatformConnection().getSelf();
        return new DiscordChatMessage(
                getPlatformConnection(),
                new DiscordChatSender(self, this),
                Objects.requireNonNull(
                        textChannel.createMessage(builder ->
                                consumer.accept(new DiscordChatMessage.CreateBuilder(self, this, builder))
                        ).block()
                )
        );
    }
}
