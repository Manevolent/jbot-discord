package io.manebot.plugin.discord.platform.guild;

import io.manebot.conversation.Conversation;
import io.manebot.platform.Platform;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AudioConnection;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.channel.AudioChannelRegistrant;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.opus.OpusParameters;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.discord.audio.channel.DiscordAudioChannel;
import io.manebot.plugin.discord.audio.channel.DiscordMixerSink;
import io.manebot.plugin.discord.database.model.DiscordGuild;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;

import io.manebot.user.User;
import io.manebot.user.UserAssociation;
import io.manebot.virtual.Virtual;
import net.dv8tion.jda.core.audio.AudioReceiveHandler;
import net.dv8tion.jda.core.audio.CombinedAudio;
import net.dv8tion.jda.core.audio.UserAudio;
import net.dv8tion.jda.core.audio.hooks.ConnectionListener;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.managers.AudioManager;

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
    private final Audio audio;
    private final io.manebot.plugin.audio.api.AudioConnection audioConnection;
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
                                  Audio audio,
                                  AudioConnection audioConnection) {
        this.plugin = plugin;
        this.guildModel = guildModel;
        this.guild = guild;
        this.connection = platformConnection;

        this.audio = audio;
        this.audioConnection = audioConnection;
    }

    public Platform getPlatform() {
        return connection.getPlatform();
    }

    public String getId() {
        return guild.getId();
    }

    public DiscordAudioChannel getAudioChannel() {
        return channel;
    }

    public DiscordMixerSink getMixerSink() {
        return mixerSink;
    }

    public DiscordPlatformConnection getPlatformConnection() {
        return connection;
    }

    public Guild getGuild() {
        return guild;
    }

    public void register() throws Exception {
        synchronized (registerLock) {
            if (registered) return;

            plugin.getLogger().fine("Connecting to guild \"" + guild.getName()
                    + "\" [" + getId() + "] ...");

            // Initialize audio subsystem for this guild.
            if (guildModel.isMusicEnabled()) {
                // Deconstruct audio system
                if (channel != null) {
                    channel.disconnect();

                    audioConnection.unregisterChannel(channel);
                    channel = null;
                }

                // Destroy any old mixers
                if (mixer != null) {
                    mixer.setRunning(false);
                    audioConnection.unregisterMixer(mixer);
                    mixer = null;
                }

                // Create mixer around the sink and audio channel around the mixer
                if (audio != null) {
                    mixer = audio.createMixer(getId(), consumer -> {
                        consumer.addDefaultFilters();
                        consumer.setFormat(48000f, 2);
                    });

                    mixer.addSink(mixerSink = new DiscordMixerSink(
                            DiscordMixerSink.AUDIO_FORMAT,
                            OpusParameters.fromPluginConfiguration(plugin),
                            mixer.getBufferSize()
                    ));

                    audioConnection.registerChannel(channel = new DiscordAudioChannel(this, mixer, this));
                }

                AudioManager audioManager = guild.getAudioManager();

                audioManager.setSendingHandler(mixerSink);

                audioManager.setConnectionListener(new ConnectionListener() {
                    @Override
                    public void onPing(long ping) {

                    }

                    @Override
                    public void onStatusChange(ConnectionStatus connectionStatus) {

                    }

                    @Override
                    public void onUserSpeaking(net.dv8tion.jda.core.entities.User user, boolean speaking) {

                    }
                });

                audioManager.setReceivingHandler(new AudioReceiveHandler() {
                    @Override
                    public boolean canReceiveCombined() {
                        return false;
                    }

                    @Override
                    public boolean canReceiveUser() {
                        return true;
                    }

                    @Override
                    public void handleCombinedAudio(CombinedAudio combinedAudio) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void handleUserAudio(UserAudio userAudio) {
                        //TODO
                    }
                });

                channel.setIdle(true);
            } else {
                channel = null;
            }

            this.registered = true;

            plugin.getLogger().info("Connected to guild \"" + guild.getName()
                    + "\" [" + getId() + "].");
        }
    }

    public void unregister() {
        synchronized (registerLock) {
            if (!registered) return;

            try {
                plugin.getLogger().fine("Disconnecting from guild \"" + guild.getName()
                        + "\" [" + getId() + "] ...");

                // Deconstruct audio system
                if (channel != null) {
                    channel.disconnect();

                    audioConnection.unregisterChannel(channel);
                    channel = null;
                }

                if (mixer != null) {
                    mixer.setRunning(false);
                    audioConnection.unregisterMixer(mixer);
                    mixer = null;
                }
            } finally {
                this.registered = false;
            }

            plugin.getLogger().fine("Disconnected from guild \"" + guild.getName()
                    + "\" [" + getId() + "].");
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
