package io.manebot.plugin.discord.command;

import io.manebot.chat.ChatMessage;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.command.executor.chained.argument.CommandArgumentSwitch;
import io.manebot.platform.PlatformConnection;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;
import io.manebot.plugin.discord.platform.DiscordPlatformConnection;
import io.manebot.plugin.discord.platform.guild.DiscordGuildConnection;

public class DiscordCommand extends AnnotatedCommandExecutor {
    private final DiscordPlatformConnection connection;

    public DiscordCommand(Plugin plugin) {
        connection = (DiscordPlatformConnection) plugin.getPlatformById("discord").getConnection();
    }

    @Override
    public String getDescription() {
        return "Manages Discord";
    }

    @Command(description = "Gets Discord status", permission = "discord.status")
    public void info(CommandSender sender, @CommandArgumentLabel.Argument(label = "info") String label)
            throws CommandExecutionException {
        info(sender);
    }

    @Command(description = "Gets Discord status", permission = "discord.status")
    public void info(CommandSender sender) throws CommandExecutionException {
        sender.sendDetails(builder -> builder.name("Discord")
                .item("Client", connection.isConnected() ? "connected": "not connected")
                .item("Users", Integer.toString(connection.getPlatformUsers().size()))
                .item("Channels", Integer.toString(connection.getChats().size()))
        );
    }

    @Command(description = "Lists connected guilds", permission = "discord.guild.list")
    public void guilds(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "guilds") String label,
                       @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        sender.sendList(
                DiscordGuildConnection.class,
                builder -> builder.direct(connection.getGuildConnections()).page(page)
                        .responder((textBuilder, c) ->
                                textBuilder.append(c.getGuild().getName() + " (" + c.getId() + ")")
                        )
        );
    }

    @Command(description = "Gets guild information", permission = "discord.guild.info")
    public void guildInfo(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "guild") String guildLabel,
                       @CommandArgumentLabel.Argument(label = "info") String infoLabel,
                       @CommandArgumentString.Argument(label = "guild ID") String guildId)
            throws CommandExecutionException {
        DiscordGuildConnection connection = this.connection.getGuildConnection(guildId);
        if (connection == null)
            throw new CommandArgumentException("Guild not found.");

        sender.sendDetails(builder -> {
            builder.name("Guild");
            builder.key(connection.getId());

            builder.item("Name", connection.getGuild().getName());
            builder.item("Status", (connection.isRegistered() ? "registered" : "unregistered"));

            if (connection.getAudioChannel() != null)
                builder.item("Audio", "enabled");
            else
                builder.item("Audio", "disabled");
        });
    }

    @Command(description = "Enables audio for a guild", permission = "discord.guild.audio.change")
    public void enableAudio(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "guild") String guildLabel,
                          @CommandArgumentLabel.Argument(label = "enable") String enableLabel,
                          @CommandArgumentLabel.Argument(label = "audio") String audioLabel,
                          @CommandArgumentString.Argument(label = "guild ID") String guildId)
            throws CommandExecutionException {
        DiscordGuildConnection connection = this.connection.getGuildConnection(guildId);
        if (connection == null)
            throw new CommandArgumentException("Guild not found.");

        if (connection.getModel().isMusicEnabled())
            throw new CommandArgumentException("Audio is already enabled for this guild.");

        try {
            connection.registerAudio();
        } catch (Exception ex) {
            throw new CommandExecutionException("Failed to register audio system for guild", ex);
        }

        connection.getModel().setMusicEnabled(true);

        sender.sendMessage("Enabled audio for guild.");
    }

    @Command(description = "Disables audio for a guild", permission = "discord.guild.audio.change")
    public void disableAudio(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "guild") String guildLabel,
                          @CommandArgumentLabel.Argument(label = "disable") String disableLabel,
                          @CommandArgumentLabel.Argument(label = "audio") String audioLabel,
                          @CommandArgumentString.Argument(label = "guild ID") String guildId)
            throws CommandExecutionException {
        DiscordGuildConnection connection = this.connection.getGuildConnection(guildId);
        if (connection == null)
            throw new CommandArgumentException("Guild not found.");

        if (!connection.getModel().isMusicEnabled())
            throw new CommandArgumentException("Audio is already disabled for this guild.");

        try {
            connection.getModel().setMusicEnabled(false);
        } catch (Exception ex) {
            throw new CommandExecutionException("Failed to unregister audio system for guild", ex);
        }

        connection.unregisterAudio();

        sender.sendMessage("Disabled audio for guild.");
    }

}
