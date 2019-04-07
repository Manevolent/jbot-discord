package io.manebot.plugin.discord;

import io.manebot.artifact.ManifestIdentifier;
import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginLoadException;
import io.manebot.plugin.PluginType;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AudioRegistration;
import io.manebot.plugin.discord.command.DiscordCommand;
import io.manebot.plugin.discord.database.model.DiscordGuild;
import io.manebot.plugin.discord.platform.guild.GuildManager;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.java.PluginEntry;
import io.manebot.virtual.Virtual;

import java.util.logging.Level;

public final class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginLoadException {
        // Require audio
        builder.setType(PluginType.FEATURE);

        // Discord Database
        final Database database = builder.addDatabase("discord", modelConstructor -> modelConstructor
                .addDependency(modelConstructor.getSystemDatabase())
                .registerEntity(DiscordGuild.class)
        );

        // GuildManager database object manager
        builder.setInstance(GuildManager.class, constructor -> new GuildManager(database));

        // Set up Discord platform
        builder.addPlatform(platformBuilder -> {
            platformBuilder.setId("discord").setName("Discord");

            Plugin audioPlugin;
            try {
                audioPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));
            } catch (Throwable e) {
                Virtual.getInstance().getLogger().log(Level.WARNING, "Failed to require audio plugin", e);
                audioPlugin = null;
            }

            Audio audio;
            if (audioPlugin != null) {
                audio = audioPlugin.getInstance(Audio.class);
            } else
                audio = null;

            final DiscordPlatformConnection platformConnection = new DiscordPlatformConnection(
                    platformBuilder.getPlatform(),
                    platformBuilder.getPlugin(),
                    audio
            );

            AudioRegistration registration;
            if (audio != null) {
                registration = audio.createRegistration(
                        platformBuilder.getPlatform(),
                        consumer -> consumer.setConnection(platformConnection.getAudioConnection())
                );
            } else {
                registration = null;
            }

            platformBuilder.setConnection(platformConnection);
        });

        builder.addCommand("discord", future -> new DiscordCommand(future.getPlugin()));
    }
}
