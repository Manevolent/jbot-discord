package io.manebot.plugin.discord.platform;

import io.manebot.chat.Chat;
import io.manebot.database.Database;
import io.manebot.platform.AbstractPlatformConnection;

import io.manebot.platform.Platform;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;

import io.manebot.plugin.audio.AudioPlugin;
import io.manebot.plugin.discord.database.model.DiscordGuild;

import io.manebot.plugin.discord.platform.chat.*;
import io.manebot.plugin.discord.platform.chat.*;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;
import io.manebot.plugin.discord.platform.guild.GuildManager;
import io.manebot.user.UserRegistration;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.lifecycle.ResumeEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import discord4j.core.object.util.Snowflake;

import java.util.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DiscordPlatformConnection extends AbstractPlatformConnection {
    private final Platform platform;
    private final Plugin plugin, audio;
    private final Database database;

    private final GuildManager guildManager;

    private final Map<String, DiscordGuildConnection> guildConnections = new LinkedHashMap<>();

    private AudioPlugin audioPlugin;
    private DiscordClient client;

    public DiscordPlatformConnection(Platform platform,
                                     Plugin plugin,
                                     Plugin audioPlugin,
                                     Database database) {
        this.platform = platform;
        this.plugin = plugin;
        this.guildManager = plugin.getInstance(GuildManager.class);
        this.audio = audioPlugin;

        this.database = database;
    }

    public Platform getPlatform() {
        return platform;
    }

    private DiscordGuildConnection createGuildConnection(Guild connection) {
        DiscordGuild guild = guildManager.getOrCreateGuild(connection.getId().asString());
        return new DiscordGuildConnection(plugin, guild, connection, this, getAudioPlugin());
    }

    private DiscordGuildConnection getGuildConnection(String id) {
        Guild guild = client.getGuildById(Snowflake.of(id)).block();
        if (guild == null) throw new IllegalArgumentException("Unknown guild: " + id);
        return guildConnections.computeIfAbsent(id, key -> createGuildConnection(guild));
    }

    // Connect/Disconnect ==============================================================================================

    @Override
    public void connect() throws PluginException {
        audioPlugin = audio.getInstance(AudioPlugin.class);

        client = new DiscordClientBuilder(plugin.requireProperty("token"))
                .setShardCount(Integer.parseInt(plugin.getProperty("shards", "1")))
                .build();

        client.getEventDispatcher()
                .on(DisconnectEvent.class)
                .subscribe(
                        event -> {
                            Iterator<Map.Entry<String, DiscordGuildConnection>> connectionIterator =
                                    guildConnections.entrySet().iterator();

                            while (connectionIterator.hasNext()) {
                                try {
                                    connectionIterator.next().getValue().unregister();
                                } catch (Exception ex) {
                                    plugin.getLogger().log(Level.WARNING, "Problem disconnecting from guild", ex);
                                }

                                connectionIterator.remove();
                            }
                        },
                        error -> plugin.getLogger().log(Level.WARNING, "Problem handling Discord disconnect event", error)
                );

        client.getEventDispatcher()
                .on(ResumeEvent.class)
                .subscribe(
                        event -> { },
                        error -> plugin.getLogger().log(Level.WARNING, "Problem handling Discord guild creation", error)
                );

        client.getEventDispatcher()
                .on(GuildCreateEvent.class)
                .subscribe(event -> {
                    try {
                        createGuildConnection(event.getGuild()).register();
                    } catch (Throwable e) {
                        plugin.getLogger().log(Level.WARNING, "Problem registering guild connection", e);
                    }
                }, error -> plugin.getLogger().log(Level.WARNING, "Problem handling Discord guild creation", error));

        client.getEventDispatcher()
                .on(MessageCreateEvent.class)
                .parallel()
                .subscribe(event -> {
                    try {
                        Optional<User> author = event.getMessage().getAuthor();
                        if (!author.isPresent()) return;

                        DiscordPlatformUser user = (DiscordPlatformUser)
                                getPlatformUser(author.get().getId().asString());

                        DiscordMessageChannel chat = (DiscordMessageChannel)
                                getChat(event.getMessage().getChannelId().asString());

                        DiscordChatSender chatSender = new DiscordChatSender(user, chat);

                        DiscordChatMessage chatMessage = new DiscordChatMessage(
                                this,
                                chatSender,
                                event.getMessage()
                        );

                        plugin.getBot().getChatDispatcher().executeAsync(chatMessage);
                    } catch (Throwable e) {
                        plugin.getLogger().log(Level.WARNING, "Problem handling Discord message", e);
                    }
                });

        client.getEventDispatcher()
                .on(GuildDeleteEvent.class)
                .subscribe(event -> {
                    try {
                        String id = event.getGuildId().asString();
                        DiscordGuildConnection connection = guildConnections.get(id);
                        if (connection != null)
                            guildConnections.remove(id).unregister();
                    } catch (Throwable e) {
                        plugin.getLogger().log(Level.WARNING, "Problem registering guild connection", e);
                    }
                }, error -> plugin.getLogger().log(Level.WARNING, "Problem registering guild connection", error));

        final CompletableFuture<Boolean> readyFuture = new CompletableFuture<>();

        client.getEventDispatcher()
                .on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();

                    plugin.getLogger().info(
                            "Discord connected as " + self.getUsername() + "#" + self.getDiscriminator() + "."
                    );

                    readyFuture.complete(true);
                }, error -> plugin.getLogger().log(Level.WARNING, "Problem handling Discord guild creation", error));

        client.login().subscribe((v) -> {});

        try {
            readyFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new PluginException(e);
        }

        plugin.getLogger().info("Discord platform connected.");
    }

    @Override
    public void disconnect() {
        if (client.isConnected())
            client.logout().block();

        Iterator<Map.Entry<String, DiscordGuildConnection>> connectionIterator =
                guildConnections.entrySet().iterator();

        while (connectionIterator.hasNext()) {
            connectionIterator.next().getValue().unregister();
            connectionIterator.remove();
        }

        plugin.getLogger().info("Discord platform disconnected.");
    }

    // JBot accessors ==================================================================================================

    @Override
    protected DiscordPlatformUser loadUserById(String id) {
        User user = client.getUserById(Snowflake.of(id)).block();
        return new DiscordPlatformUser(this, user);
    }

    @Override
    protected Chat loadChatById(String id) {
        Channel channel = client.getChannelById(Snowflake.of(id)).block();
        if (channel == null) return null;

        if (channel instanceof TextChannel) {
            return new DiscordGuildChannel(this, (TextChannel) channel);
        } else if (channel instanceof PrivateChannel) {
            return new DiscordPrivateChannel(this, (PrivateChannel) channel);
        } else
            return null;
    }

    @Override
    public DiscordPlatformUser getSelf() {
        User user = client.getSelf().block();
        if (user == null) return null;
        return (DiscordPlatformUser) getPlatformUser(user.getId().asString());
    }

    @Override
    public Collection<String> getPlatformUserIds() {
        return client
                .getUsers().map(user -> user.getId().asString())
                .collectList()
                .blockOptional()
                .orElseThrow(() -> new IllegalArgumentException("Failed to obtain user list"));
    }

    @Override
    public Collection<String> getChatIds() {
        return client.getServiceMediator()
                .getStateHolder()
                .getTextChannelStore()
                .entries()
                .map(channelBean -> Long.toUnsignedString(channelBean.getT2().getId()))
                .collectList()
                .blockOptional()
                .orElseThrow(() -> new IllegalArgumentException("Failed to obtain channel list"));
    }

    @Override
    public UserRegistration getUserRegistration() {
        return plugin.getBot().getDefaultUserRegistration();
    }

    public AudioPlugin getAudioPlugin() {
        return audioPlugin;
    }
}
