package io.manebot.plugin.discord;

import io.manebot.artifact.ManifestIdentifier;
import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginLoadException;
import io.manebot.plugin.PluginType;
import io.manebot.plugin.discord.database.model.DiscordGuild;
import io.manebot.plugin.discord.platform.guild.GuildManager;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.java.PluginEntry;

public final class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginLoadException {
        // Require audio
        builder.setType(PluginType.FEATURE);
        final Plugin audioPlugin =
                builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));

        // Discord Database
        final Database database = builder.addDatabase("discord", modelConstructor -> modelConstructor
                .addDependency(modelConstructor.getSystemDatabase())
                .registerEntity(DiscordGuild.class)
        );

        // GuildManager database object manager
        builder.setInstance(GuildManager.class, constructor -> new GuildManager(database));

        // Set up Discord platform
        builder.addPlatform(platformBuilder -> platformBuilder
                .setId("discord").setName("Discord")
                .setConnection(new DiscordPlatformConnection(
                        platformBuilder.getPlatform(),
                        platformBuilder.getPlugin(),
                        audioPlugin,
                        database
                ))
        );
    }
}
