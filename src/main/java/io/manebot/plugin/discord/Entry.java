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
    public Plugin instantiate(Plugin.Builder builder) throws PluginLoadException {
        // Require audio
        builder.type(PluginType.FEATURE);
        final Plugin audioPlugin =
                builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));

        // Discord Database
        final Database database = builder.database("discord", modelConstructor -> modelConstructor
                .depend(modelConstructor.getSystemDatabase())
                .registerEntity(DiscordGuild.class)
                .define()
        );

        // GuildManager database object manager
        builder.instance(GuildManager.class, constructor -> new GuildManager(database));

        // Set up Discord platform
        builder.platform(platformBuilder -> platformBuilder
                .withId("discord").withName("Discord")
                .withConnection(new DiscordPlatformConnection(
                        platformBuilder.getPlatform(),
                        platformBuilder.getPlugin(),
                        audioPlugin,
                        database
                ))
                .build()
        );

        // Commands
        return builder.build();
    }
}
