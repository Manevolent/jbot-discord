package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.*;

import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.discord.platform.DiscordPlatformUser;

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.Date;
import java.time.Instant;
import java.util.Collection;

import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiscordChatMessage extends AbstractChatMessage {
    private final DiscordPlatformConnection connection;
    private final Message message;
    private final DiscordChatSender sender;

    public DiscordChatMessage(DiscordPlatformConnection connection,
                              DiscordChatSender sender,
                              Message message) {
        super(sender, Date.from(message.getCreationTime().toInstant()));

        this.sender = sender;
        this.connection = connection;
        this.message = message;
    }

    @Override
    public DiscordChatSender getSender() {
        return sender;
    }

    @Override
    public Collection<PlatformUser> getMentions() {
        return message.getMentionedUsers()
                .stream()
                .map(connection::getPlatformUser)
                .collect(Collectors.toList());
    }

    @Override
    public String getMessage() {
        return message.getContentStripped();
    }

    @Override
    public String getRawMessage() {
        return message.getContentRaw();
    }

    @Override
    public Collection<io.manebot.chat.ChatEmbed> getEmbeds() {
        return message.getEmbeds().stream().map(ChatEmbed::new).collect(Collectors.toList());
    }

    @Override
    public void delete() throws UnsupportedOperationException {
        message.delete().complete();
    }

    @Override
    public ChatMessage edit(Consumer<Builder> function) {
        net.dv8tion.jda.core.MessageBuilder builder = new net.dv8tion.jda.core.MessageBuilder();
        function.accept(new MessageBuilder(getSender().getPlatformUser(), getSender().getChat(), builder));
        Message editedMessage = message.editMessage(builder.build()).complete();
        return new DiscordChatMessage(connection, sender, editedMessage);
    }

    @Override
    public boolean wasEdited() {
        return message.isEdited();
    }

    @Override
    public java.util.Date getEditedDate() {
        return wasEdited() ? Date.from(message.getEditedTime().toInstant()) : null;
    }

    private static class ChatEmbed implements io.manebot.chat.ChatEmbed {
        private final MessageEmbed embed;

        private ChatEmbed(MessageEmbed embed) {
            this.embed = embed;
        }

        @Override
        public Color getColor() {
            return embed.getColor();
        }

        @Override
        public String getTitle() {
            return embed.getTitle();
        }

        @Override
        public String getDescription() {
            return embed.getDescription();
        }

        @Override
        public Collection<Field> getFields() {
            return embed.getFields().stream()
                    .map(field -> new Field(field.getName(), field.getValue(), field.isInline()))
                    .collect(Collectors.toList());
        }

        @Override
        public String getFooter() {
            return embed.getFooter() == null ? null : embed.getFooter().getText();
        }

        @Override
        public ImageElement getThumbnail() {
            try {
                return embed.getThumbnail() != null ?
                        new RemoteImage(URI.create(embed.getThumbnail().getUrl()).toURL()) :
                        null;
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * Discord EmbedBuilder.  This translates well-formatted text chat intents to Discord API (JDA) intents, supporting
     * text styles (bold, italics) along the way.  I've already stubbed out certain fields (footer, content, etc) that
     * don't appear to support styles.  At the moment, that seems to be all fields in an embed.
     */
    public static class EmbedBuilder implements io.manebot.chat.ChatEmbed.Builder {
        private final Chat chat;
        private final net.dv8tion.jda.core.EmbedBuilder builder;

        public EmbedBuilder(Chat chat, net.dv8tion.jda.core.EmbedBuilder builder) {
            this.chat = chat;
            this.builder = builder;
        }

        @Override
        public Chat getChat() {
            return chat;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder thumbnail(io.manebot.chat.ChatEmbed.ImageElement imageElement) {
            if (imageElement instanceof io.manebot.chat.ChatEmbed.RemoteImage)
                builder.setThumbnail(((io.manebot.chat.ChatEmbed.RemoteImage) imageElement).getUrl().toExternalForm());
            else
                throw new UnsupportedOperationException("image element is not remote (Discord only supports URLs)");

            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder descriptionRaw(String message) {
            builder.setDescription(message);
            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder title(Consumer<TextBuilder> value) {
            TextBuilder builder = new DefaultTextBuilder(getChat(), TextFormat.BASIC);
            value.accept(builder);
            return titleRaw(builder.build());
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder titleRaw(String title) {
            builder.setTitle(title);
            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder footerRaw(String footer) {
            builder.setFooter(footer, null);
            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder footer(Consumer<TextBuilder> textBuilder) {
            TextBuilder builder = new DefaultTextBuilder(getChat(), TextFormat.BASIC);
            textBuilder.accept(builder);
            return footerRaw(builder.build());
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder timestamp(java.util.Date date) {
            builder.setTimestamp(date.toInstant());
            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder timestamp(Instant instant) {
            builder.setTimestamp(instant);
            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder color(Color color) {
            builder.setColor(color);
            return this;
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder field(String name, Consumer<TextBuilder> value) {
            TextBuilder builder = new DefaultTextBuilder(getChat(), TextFormat.BASIC);
            value.accept(builder);
            return fieldRaw(name, builder.build());
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder field(String name, Consumer<TextBuilder> value, boolean inline) {
            TextBuilder builder = new DefaultTextBuilder(getChat(), TextFormat.BASIC);
            value.accept(builder);
            return fieldRaw(name, builder.build(), inline);
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder fieldRaw(String name, String value) {
            return field(name, value, false);
        }

        @Override
        public io.manebot.chat.ChatEmbed.Builder fieldRaw(String name, String value, boolean inline) {
            if (name == null && value == null)
                builder.addBlankField(inline);
            else
                builder.addField(name, value, inline);

            return this;
        }
    }

    static class MessageBuilder implements ChatMessage.Builder {
        private final DiscordPlatformUser user;
        private final BaseDiscordChannel channel;

        private final net.dv8tion.jda.core.MessageBuilder builder;
        private boolean builtEmbed = false;

        public MessageBuilder(DiscordPlatformUser user,
                              BaseDiscordChannel channel,
                              net.dv8tion.jda.core.MessageBuilder builder) {
            this.user = user;
            this.channel = channel;
            this.builder = builder;
        }

        @Override
        public DiscordPlatformUser getUser() {
            return user;
        }

        @Override
        public BaseDiscordChannel getChat() {
            return channel;
        }

        @Override
        public Builder rawMessage(String message) {
            builder.setContent(message);
            return this;
        }

        @Override
        public ChatMessage.Builder embed(Consumer<io.manebot.chat.ChatEmbed.Builder> function)
                throws UnsupportedOperationException {
            if (this.builtEmbed)
                throw new IllegalStateException("Embed already defined; Discord only accepts one embed per message");

            net.dv8tion.jda.core.EmbedBuilder builder = new net.dv8tion.jda.core.EmbedBuilder();
            function.accept(new EmbedBuilder(channel, builder));
            this.builder.setEmbed(builder.build());

            this.builtEmbed = true;
            return this;
        }

        public Message build() {
            return builder.build();
        }
    }

}
