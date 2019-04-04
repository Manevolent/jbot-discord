package io.manebot.plugin.discord.platform.guild;

import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginReference;
import io.manebot.plugin.discord.database.model.DiscordGuild;
import io.manebot.virtual.Virtual;

import java.sql.SQLException;

public class GuildManager implements PluginReference {
    private final Database database;

    public GuildManager(Database database) {
        this.database = database;
    }

    public DiscordGuild getGuild(String id) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + DiscordGuild.class.getName() + " x WHERE x.id=:id",
                    DiscordGuild.class
            ).setParameter("id", id)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public DiscordGuild getOrCreateGuild(String id) {
        try {
            return database.executeTransaction(s -> {
                return s.createQuery(
                        "SELECT x FROM " + DiscordGuild.class.getName() + " x WHERE x.id=:id",
                        DiscordGuild.class
                ).setParameter("id", id)
                        .getResultList()
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            DiscordGuild discordGuild = new DiscordGuild(database, id);
                            s.persist(discordGuild);
                            return discordGuild;
                        });
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeGuild(String stringID) {
        DiscordGuild guild = getGuild(stringID);
        if (guild != null) guild.remove();
    }

    @Override
    public void load(Plugin.Future future) {}

    @Override
    public void unload(Plugin.Future future) {}
}
