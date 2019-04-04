package io.manebot.plugin.discord.platform;

import io.manebot.chat.Chat;
import io.manebot.database.Database;
import io.manebot.platform.AbstractPlatformConnection;

import io.manebot.platform.Platform;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;

import io.manebot.plugin.audio.AudioPlugin;
import io.manebot.plugin.discord.database.model.DiscordGuild;

import io.manebot.plugin.discord.platform.chat.*;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;
import io.manebot.plugin.discord.platform.guild.GuildManager;
import io.manebot.user.UserRegistration;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.core.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.util.*;

import java.util.concurrent.Executors;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DiscordPlatformConnection extends AbstractPlatformConnection {
    private final Platform platform;
    private final Plugin plugin, audio;
    private final Database database;

    private final GuildManager guildManager;

    private final Map<String, DiscordGuildConnection> guildConnections = new LinkedHashMap<>();

    private AudioPlugin audioPlugin;
    private JDA client;

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
        DiscordGuild guild = guildManager.getOrCreateGuild(connection.getId());
        return new DiscordGuildConnection(plugin, guild, connection, this, getAudioPlugin());
    }

    private DiscordGuildConnection getGuildConnection(String id) {
        Guild guild = client.getGuildById(id);
        if (guild == null) throw new IllegalArgumentException("Unknown guild: " + id);
        return guildConnections.computeIfAbsent(id, key -> createGuildConnection(guild));
    }

    // Connect/Disconnect ==============================================================================================

    @Override
    public void connect() throws PluginException {
        audioPlugin = audio.getInstance(AudioPlugin.class);

        Logger.getLogger("discord4j.rest").setLevel(Level.FINEST);

        try {
            client = new JDABuilder(plugin.requireProperty("token"))
                    .useSharding(
                            Integer.parseInt(plugin.getProperty("shardId", "0")),
                            Integer.parseInt(plugin.getProperty("totalShards", "1"))
                    )
                    .setCallbackPool(Executors.newCachedThreadPool(), true)
                    .setAudioEnabled(Boolean.parseBoolean(plugin.getProperty("audio", "true")))
                    .setAutoReconnect(Boolean.parseBoolean(plugin.getProperty("autoReconnect", "true")))
                    .setCorePoolSize(Integer.parseInt(plugin.getProperty("poolSize", "5")))
                    .setMaxReconnectDelay(Integer.parseInt(plugin.getProperty("maxReconnectDelay", "900")))
                    .setIdle(Boolean.parseBoolean(plugin.getProperty("idle", "false")))
                    .setCompressionEnabled(Boolean.parseBoolean(plugin.getProperty("compression", "true")))
                    .addEventListener(new ListenerAdapter() {
                        @Override
                        public void onReady(ReadyEvent event) {
                            for (Guild guild : event.getJDA().getGuilds()) {
                                try {
                                    createGuildConnection(guild).register();
                                } catch (Exception e) {
                                    plugin.getLogger().log(
                                            Level.WARNING,
                                            "Problem registering guild " + guild.getId(),
                                            e
                                    );
                                }
                            }

                            plugin.getLogger().info(
                                    "Connected to discord as " +
                                    event.getJDA().getSelfUser().getName() + "."
                            );
                        }

                        @Override
                        public void onMessageReceived(MessageReceivedEvent event) {
                            try {
                                User author = event.getMessage().getAuthor();

                                if (author.isBot()) return;

                                DiscordPlatformUser user = (DiscordPlatformUser)
                                        getPlatformUser(author.getId());

                                BaseDiscordChannel chat = getChat(event.getMessage().getChannel());

                                DiscordChatSender chatSender = new DiscordChatSender(user, chat);

                                DiscordChatMessage chatMessage = new DiscordChatMessage(
                                        DiscordPlatformConnection.this,
                                        chatSender,
                                        event.getMessage()
                                );

                                plugin.getBot().getChatDispatcher().executeAsync(chatMessage);
                            } catch (Throwable e) {
                                plugin.getLogger().log(Level.WARNING, "Problem handling Discord message", e);
                            }
                        }

                        @Override
                        public void onGuildAvailable(GuildAvailableEvent event) {
                            try {
                                createGuildConnection(event.getGuild()).register();
                            } catch (Throwable e) {
                                plugin.getLogger().log(Level.WARNING, "Problem registering guild connection", e);
                            }
                        }

                        @Override
                        public void onGuildUnavailable(GuildUnavailableEvent event) {
                            try {
                                DiscordGuildConnection connection = guildConnections.remove(event.getGuild().getId());
                                if (connection != null) connection.unregister();
                            } catch (Throwable e) {
                                plugin.getLogger().log(Level.WARNING, "Problem unregistering guild connection", e);
                            }
                        }
                    })
                    .build()
                    .awaitReady();
        } catch (LoginException e) {
            throw new PluginException("Failed to login to Discord", e);
        } catch (InterruptedException e) {
            throw new PluginException(e);
        }

        plugin.getLogger().info("Discord platform connected.");
    }

    @Override
    public void disconnect() {
        Iterator<Map.Entry<String, DiscordGuildConnection>> connectionIterator =
                guildConnections.entrySet().iterator();

        while (connectionIterator.hasNext()) {
            connectionIterator.next().getValue().unregister();
            connectionIterator.remove();
        }

        client.shutdownNow();

        plugin.getLogger().info("Discord platform disconnected.");
    }

    // JBot accessors ==================================================================================================

    private DiscordPlatformUser loadUser(User user) {
        Objects.requireNonNull(user);
        return new DiscordPlatformUser(this, user);
    }

    @Override
    protected DiscordPlatformUser loadUserById(String id) {
        return loadUser(client.getUserById(id));
    }

    private Chat loadChat(MessageChannel channel) {
        Objects.requireNonNull(channel);

        if (channel instanceof TextChannel) {
            return new DiscordGuildChannel(this, (TextChannel) channel);
        } else if (channel instanceof PrivateChannel) {
            return new DiscordPrivateChannel(this, (PrivateChannel) channel);
        } else
            throw new UnsupportedOperationException("Unsupported channel class: " + channel.getClass().getName());
    }

    @Override
    protected Chat loadChatById(String id) {
        return loadChat(client.getTextChannelById(id));
    }


    public DiscordPlatformUser getPlatformUser(User user) {
        return (DiscordPlatformUser) super.getCachedUserById(user.getId(), (key) -> loadUser(user));
    }

    public BaseDiscordChannel getChat(MessageChannel chat) {
        return (BaseDiscordChannel) super.getCachedChatById(chat.getId(), (key) -> loadChat(chat));
    }

    @Override
    public DiscordPlatformUser getSelf() {
        return getPlatformUser(client.getSelfUser());
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        return Collections.unmodifiableCollection(
                client.getUsers()
                        .stream()
                        .map(this::getPlatformUser)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Collection<String> getPlatformUserIds() {
        return Collections.unmodifiableCollection(
                client.getUsers()
                .stream()
                .map(ISnowflake::getId)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Collection<String> getChatIds() {
        return Collections.unmodifiableCollection(
                client.getTextChannels()
                        .stream()
                        .map(ISnowflake::getId)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Collection<Chat> getChats() {
        return Collections.unmodifiableCollection(
                client.getTextChannels()
                        .stream()
                        .map(this::getChat)
                        .collect(Collectors.toList())
        );
    }

    public AudioPlugin getAudioPlugin() {
        return audioPlugin;
    }
}
