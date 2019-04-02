package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.*;

import io.manebot.platform.PlatformConnection;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.DiscordPlatformUser;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.Date;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiscordChatMessage extends AbstractChatMessage {
    private final PlatformConnection connection;
    private final Message message;
    private final DiscordChatSender sender;

    public DiscordChatMessage(PlatformConnection connection,
                              DiscordChatSender sender,
                              Message message) {
        super(sender, Date.from(message.getTimestamp()));

        this.sender = sender;
        this.connection = connection;
        this.message = message;
    }

    @Override
    public Collection<PlatformUser> getMentions() {
        return message.getUserMentionIds().stream()
                .map(snowflake -> connection.getPlatformUser(snowflake.asString()))
                .collect(Collectors.toList());
    }

    @Override
    public String getMessage() {
        return message.getContent().orElse(null);
    }

    @Override
    public Collection<io.manebot.chat.ChatEmbed> getEmbeds() {
        return message.getEmbeds().stream().map(ChatEmbed::new).collect(Collectors.toList());
    }

    @Override
    public void delete() throws UnsupportedOperationException {
        message.delete();
    }

    @Override
    public ChatMessage edit(Consumer<Builder> function) {
        return new DiscordChatMessage(
                connection,
                sender,
                Objects.requireNonNull(message.edit((spec) -> function.accept(new EditBuilder(
                        sender.getPlatformUser(),
                        sender.getChat(),
                        spec
                ))).block())
        );
    }

    @Override
    public boolean wasEdited() {
        return message.getEditedTimestamp().isPresent();
    }

    @Override
    public java.util.Date getEditedDate() {
        return wasEdited() ? Date.from(message.getTimestamp()) : null;
    }

    private static class ChatEmbed implements io.manebot.chat.ChatEmbed {
        private final Embed embed;

        private ChatEmbed(Embed embed) {
            this.embed = embed;
        }

        @Override
        public Color getColor() {
            return embed.getColor().orElse(null);
        }

        @Override
        public String getTitle() {
            return embed.getTitle().orElse(null);
        }

        @Override
        public String getDescription() {
            return embed.getDescription().orElse(null);
        }

        @Override
        public Collection<Field> getFields() {
            return embed.getFields().stream()
                    .map(field -> new Field(field.getName(), field.getValue(), field.isInline()))
                    .collect(Collectors.toList());
        }

        @Override
        public String getFooter() {
            return embed.getFooter().isPresent() ? embed.getFooter().get().getText() : null;
        }

        @Override
        public ImageElement getThumbnail() {
            try {
                return embed.getThumbnail().isPresent() ?
                        new RemoteImage(URI.create(embed.getThumbnail().get().getUrl()).toURL()) :
                        null;
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public static class EmbedBuilder implements ChatEmbed.Builder {
        private final EmbedCreateSpec spec;

        public EmbedBuilder(EmbedCreateSpec spec) {
            this.spec = spec;
        }

        @Override
        public ChatEmbed.Builder thumbnail(ChatEmbed.ImageElement imageElement) {
            if (imageElement instanceof ChatEmbed.RemoteImage)
                spec.setThumbnail(((ChatEmbed.RemoteImage) imageElement).getUrl().toExternalForm());
            else
                throw new UnsupportedOperationException("image element is not remote (Discord only supports URLs)");

            return this;
        }

        @Override
        public ChatEmbed.Builder title(String s) {
            spec.setTitle(s);
            return this;
        }

        @Override
        public ChatEmbed.Builder description(String s) {
            spec.setDescription(s);
            return this;
        }

        @Override
        public ChatEmbed.Builder footer(String s) {
            spec.setFooter(s, null);
            return this;
        }

        @Override
        public ChatEmbed.Builder timestamp(java.util.Date date) {
            spec.setTimestamp(date.toInstant());
            return this;
        }

        @Override
        public ChatEmbed.Builder timestamp(Instant instant) {
            spec.setTimestamp(instant);
            return this;
        }

        @Override
        public ChatEmbed.Builder color(Color color) {
            spec.setColor(color);
            return this;
        }

        @Override
        public ChatEmbed.Builder field(String name, String value, boolean inline) {
            spec.addField(name, value, inline);
            return this;
        }
    }

    static class CreateBuilder implements ChatMessage.Builder {
        private final DiscordPlatformUser user;
        private final DiscordMessageChannel channel;

        private final MessageCreateSpec spec;

        public CreateBuilder(DiscordPlatformUser user, DiscordMessageChannel channel, MessageCreateSpec spec) {
            this.user = user;
            this.channel = channel;
            this.spec = spec;
        }

        @Override
        public PlatformUser getUser() {
            return user;
        }

        @Override
        public Chat getChat() {
            return channel;
        }

        @Override
        public ChatMessage.Builder message(String s) {
            spec.setContent(s);
            return this;
        }

        @Override
        public ChatMessage.Builder embed(Consumer<ChatEmbed.Builder> function)
                throws UnsupportedOperationException {
            spec.setEmbed((embedSpec) -> {
                function.accept(new EmbedBuilder(embedSpec));
            });

            return this;
        }
    }

    private static class EditBuilder implements ChatMessage.Builder {
        private final DiscordPlatformUser user;
        private final DiscordMessageChannel channel;

        private final MessageEditSpec spec;

        public EditBuilder(DiscordPlatformUser user, DiscordMessageChannel channel, MessageEditSpec spec) {
            this.user = user;
            this.channel = channel;
            this.spec = spec;
        }

        @Override
        public PlatformUser getUser() {
            return user;
        }

        @Override
        public Chat getChat() {
            return channel;
        }

        @Override
        public ChatMessage.Builder message(String s) {
            spec.setContent(s);
            return this;
        }

        @Override
        public ChatMessage.Builder embed(Consumer<ChatEmbed.Builder> function)
                throws UnsupportedOperationException {
            spec.setEmbed((embedSpec) -> {
                function.accept(new EmbedBuilder(embedSpec));
            });

            return this;
        }
    }
}
