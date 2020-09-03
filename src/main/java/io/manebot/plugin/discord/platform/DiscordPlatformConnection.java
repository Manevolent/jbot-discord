package io.manebot.plugin.discord.platform;

import io.manebot.chat.Chat;

import io.manebot.chat.Community;
import io.manebot.event.Event;
import io.manebot.platform.AbstractPlatformConnection;

import io.manebot.platform.Platform;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AbstractAudioConnection;
import io.manebot.plugin.audio.api.AudioConnection;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.event.channel.AudioChannelUserJoinEvent;
import io.manebot.plugin.audio.event.channel.AudioChannelUserLeaveEvent;
import io.manebot.plugin.discord.database.model.DiscordGuild;

import io.manebot.plugin.discord.platform.chat.*;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;
import io.manebot.plugin.discord.platform.guild.GuildManager;
import io.manebot.plugin.discord.platform.user.DiscordPlatformUser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.util.*;

import java.util.concurrent.Executors;

import java.util.logging.Level;

import java.util.stream.Collectors;

public class DiscordPlatformConnection
        extends AbstractPlatformConnection {

    private final AudioConnection audioConnection;

    private final Platform platform;
    private final Plugin plugin;
    private final Audio audio;
    private final GuildManager guildManager;

    private final Map<String, DiscordGuildConnection> guildConnections = new LinkedHashMap<>();

    private JDA client;

    public DiscordPlatformConnection(Platform platform,
                                     Plugin plugin,
                                     Audio audio) {
        this.audioConnection = new DiscordAudioConnection(audio);

        this.audio = audio;

        this.platform = platform;
        this.plugin = plugin;
        this.guildManager = plugin.getInstance(GuildManager.class);
    }

    public Platform getPlatform() {
        return platform;
    }

    public Audio getAudio() {
        return audio;
    }

    private DiscordGuildConnection createGuildConnection(Guild connection) {
        DiscordGuild guild = guildManager.getOrCreateGuild(connection.getId());
        return new DiscordGuildConnection(plugin, guild, connection, this, audio, audioConnection);
    }

    public DiscordGuildConnection getGuildConnection(String id) {
        Guild guild = client.getGuildById(id);
        if (guild == null) throw new IllegalArgumentException("Unknown guild: " + id);
        return getGuildConnection(guild);
    }

    public DiscordGuildConnection getGuildConnection(Guild guild) {
        return guildConnections.computeIfAbsent(guild.getId(), key -> createGuildConnection(guild));
    }

    public AudioConnection getAudioConnection() {
        return audioConnection;
    }

    // Connect/Disconnect ==============================================================================================

    @Override
    public void connect() throws PluginException {
        try {
            client = new JDABuilder(plugin.requireProperty("token"))
                    .useSharding(
                            Integer.parseInt(plugin.getProperty("shardId", "0")),
                            Integer.parseInt(plugin.getProperty("totalShards", "1"))
                    )
                    .setCallbackPool(Executors.newCachedThreadPool(), true)
                    .setAutoReconnect(Boolean.parseBoolean(plugin.getProperty("autoReconnect", "true")))
                    .setMaxReconnectDelay(Integer.parseInt(plugin.getProperty("maxReconnectDelay", "900")))
                    .setIdle(Boolean.parseBoolean(plugin.getProperty("idle", "false")))
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onReady(ReadyEvent event) {
                            for (Guild guild : event.getJDA().getGuilds()) {
                                try {
                                    getGuildConnection(guild).register();
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

                                DiscordPlatformUser user = getPlatformUser(author);

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
                                getGuildConnection(event.getGuild()).register();
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

                        @Override
                        public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
                            DiscordGuildConnection guildConnection = getGuildConnection(event.getGuild());
                            GuildVoiceState voiceState = event.getVoiceState();
                            AudioChannel channel = guildConnection.getAudioChannel();
                            PlatformUser platformUser = getPlatformUser(event.getMember().getUser());

                            if (event.getChannelJoined().equals(voiceState.getChannel())) {
                                platform.getPlugin().getBot().getEventDispatcher()
                                        .executeAsync(new AudioChannelUserJoinEvent(this, audio, channel, platformUser));
                            }
                        }

                        @Override
                        public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
                            DiscordGuildConnection guildConnection = getGuildConnection(event.getGuild());
                            GuildVoiceState voiceState = event.getVoiceState();
                            AudioChannel channel = guildConnection.getAudioChannel();
                            PlatformUser platformUser = getPlatformUser(event.getMember().getUser());

                            if (event.getChannelLeft().equals(voiceState.getChannel())) {
                                platform.getPlugin().getBot().getEventDispatcher()
                                        .executeAsync(new AudioChannelUserLeaveEvent(this, audio, channel, platformUser));
                            }
                        }

                        @Override
                        public void onException(ExceptionEvent event) {
                            plugin.getLogger().log(Level.WARNING, "Problem occurred in JDA", event.getCause());
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

    @Override
    protected Community loadCommunityById(String id) {
        return guildConnections.get(id);
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
    public Collection<Chat> getChats() {
        return Collections.unmodifiableCollection(
                client.getTextChannels()
                        .stream()
                        .map(this::getChat)
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
    public Collection<String> getCommunityIds() {
        return Collections.unmodifiableCollection(
                client.getGuilds()
                        .stream()
                        .map(ISnowflake::getId)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Collection<Community> getCommunities() {
        return guildConnections.values()
                .stream()
                .map(connection -> (Community) connection).collect(Collectors.toList());
    }

    public List<DiscordGuildConnection> getGuildConnections() {
        return new ArrayList<>(guildConnections.values());
    }

    private class DiscordAudioConnection extends AbstractAudioConnection {
        private DiscordAudioConnection(Audio audio) {
            super(audio);
        }

        @Override
        public AudioChannel getChannel(Chat chat) {
            if (chat instanceof DiscordGuildChannel) {
                DiscordGuildConnection connection = ((DiscordGuildChannel) chat).getGuildConnection();

                if (connection == null || !connection.isRegistered()) {
                    return null;
                }

                return connection.getAudioChannel();
            } else return null;
        }

        @Override
        public boolean isConnected() {
            return super.isConnected() && DiscordPlatformConnection.this.isConnected();
        }
    }
}
