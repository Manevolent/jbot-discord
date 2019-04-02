package io.manebot.plugin.discord.platform.guild;

import io.manebot.conversation.Conversation;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.audio.AudioPlugin;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.channel.AudioChannelRegistrant;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.opus.OpusParameters;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.discord.audio.channel.DiscordAudioChannel;
import io.manebot.plugin.discord.audio.channel.DiscordMixerSink;
import io.manebot.plugin.discord.database.model.DiscordGuild;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.discord.platform.DiscordPlatformUser;
import io.manebot.user.User;
import io.manebot.user.UserAssociation;
import io.manebot.virtual.Virtual;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.util.Snowflake;

import java.util.Collections;
import java.util.List;

import java.util.stream.Collectors;

/**
 * Represents a connection to a specific authorized Guild in Discord.
 */
public class DiscordGuildConnection implements AudioChannelRegistrant {
    private final Plugin plugin;
    private final DiscordGuild guildModel;
    private final Guild guild;
    private final DiscordPlatformConnection connection;
    private final AudioPlugin audioPlugin;
    private final Object voiceConnectionLock = new Object();

    private final Object registerLock = new Object();
    private boolean registered = false;

    private Sleeper sleeper; // used to time the channel going to sleep
    private Mixer mixer;
    private DiscordMixerSink mixerSink;
    private DiscordAudioChannel channel;

    public DiscordGuildConnection(Plugin plugin,
                                  DiscordGuild guildModel,
                                  Guild guild,
                                  DiscordPlatformConnection platformConnection,
                                  AudioPlugin audioPlugin) {
        this.plugin = plugin;
        this.guildModel = guildModel;
        this.guild = guild;
        this.connection = platformConnection;
        this.audioPlugin = audioPlugin;
    }

    public DiscordAudioChannel getAudioChannel() {
        return channel;
    }

    public DiscordMixerSink getMixerSink() { return mixerSink; }

    public String getStringID() {
        return guild.getId().asString();
    }

    public DiscordPlatformConnection getPlatformConnection() {
        return connection;
    }

    public void register() throws Exception {
        synchronized (registerLock) {
            if (registered) return;

            plugin.getLogger().fine("Connecting to guild \"" + guild.getName()
                    + "\" [" + guild.getId().asString() + "] ...");

            // Initialize audio subsystem for this guild.
            if (guildModel.isMusicEnabled()) {
                mixerSink = new DiscordMixerSink(
                        DiscordMixerSink.AUDIO_FORMAT,
                        OpusParameters.fromPluginConfiguration(plugin),
                        audioPlugin.getBufferSize(DiscordMixerSink.AUDIO_FORMAT)
                );

                // Deconstruct audio system
                if (channel != null) {
                    channel.disconnect();

                    audioPlugin.unregisterChannel(channel);
                    channel = null;
                }

                // Destroy any old mixers
                if (mixer != null) {
                    mixer.setRunning(false);
                    audioPlugin.unregisterMixer(mixer);
                    mixer = null;
                }

                // Create mixer around the sink
                mixer = audioPlugin.createMixer("discord:" + guild.getId().asString(), mixerSink);

                audioPlugin.registerChannel(channel = new DiscordAudioChannel(this, mixer, mixerSink.getProvider(), this));

                channel.setIdle(true);
            } else {
                channel = null;
            }

            this.registered = true;

            plugin.getLogger().info("Connected to guild \"" + guild.getName()
                    + "\" [" + guild.getId().asString() + "].");
        }
    }

    public void unregister() {
        synchronized (registerLock) {
            if (!registered) return;

            try {
                plugin.getLogger().fine("Disconnecting from guild \"" + guild.getName()
                        + "\" [" + guild.getId().asString() + "] ...");

                // Deconstruct audio system
                if (channel != null) {
                    channel.disconnect();

                    audioPlugin.unregisterChannel(channel);
                    channel = null;
                }

                if (mixer != null) {
                    mixer.setRunning(false);
                    audioPlugin.unregisterMixer(mixer);
                    mixer = null;
                }
            } finally {
                this.registered = false;
            }

            plugin.getLogger().fine("Disconnected from guild \"" + guild.getName()
                    + "\" [" + guild.getId().asString() + "].");
        }
    }

    public void onJoin() {
        synchronized (voiceConnectionLock) {
            voiceConnectionLock.notifyAll();
        }
    }

    private Sleeper schedule() {
        return schedule(guildModel.getIdleTimeout() * 1000);
    }

    private Sleeper schedule(long millis) {
        if (sleeper != null && !sleeper.isDone()) sleeper.cancel();

        Virtual.getInstance()
                .create(sleeper = new Sleeper(System.currentTimeMillis() + millis))
                .start();

        return sleeper;
    }

    public Conversation getDefaultConversation() {
        return guildModel.getDefaultConversation();
    }

    public User resolveUser(User user) {
        throw new UnsupportedOperationException();
    }

    public List<User> getListeners() {
        return Collections.unmodifiableList(channel
                .getRegisteredListeners().stream()
                .map(UserAssociation::getUser)
                .collect(Collectors.toList())
        );
    }

    // JBot audio system events ========================================================================================

    @Override
    public void onPlayerStarted(AudioChannel audioChannel, AudioPlayer audioPlayer) {
        // Do nothing
    }

    @Override
    public void onPlayerStopped(AudioChannel audioChannel, AudioPlayer audioPlayer) {
        // Do nothing
    }

    @Override
    public void onChannelActivated(AudioChannel audioChannel) {
        // Do nothing
    }

    @Override
    public void onChannelPassivated(AudioChannel audioChannel) {
        schedule();
    }

    @Override
    public void onChannelSleep(AudioChannel channel) {
        this.channel.disconnect();
    }

    @Override
    public void onChannelWake(AudioChannel channel) {
        schedule();
    }

    public Member getMember(DiscordPlatformUser platformUser) {
        return guild.getMemberById(Snowflake.of(platformUser.getId())).block();
    }

    private class Sleeper implements Runnable {
        private final long sleepTime;
        private final Object lock = new Object();
        private volatile boolean cancel = false;
        private volatile boolean done = false;

        private Sleeper(long sleepTime) {
            this.sleepTime = sleepTime;
        }

        @Override
        public void run() {
            synchronized (lock) {
                while (!cancel) {
                    try {
                        lock.wait(Math.max(0, sleepTime - System.currentTimeMillis()));
                        break;
                    } catch (InterruptedException e) {
                        Thread.yield();
                    }
                }

                if (channel != null && !cancel && channel.getState() == AudioChannel.State.WAITING)
                    channel.setIdle(true);

                sleeper = null;

                done = true;
            }
        }

        void cancel() {
            synchronized (lock) {
                cancel = true;
                lock.notifyAll();
            }
        }

        boolean isDone() {
            return done;
        }
    }
}
