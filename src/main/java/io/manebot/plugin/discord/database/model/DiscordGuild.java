package io.manebot.plugin.discord.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.Conversation;
import io.manebot.database.model.TimedRow;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "id", unique = true),
                @Index(columnList = "enabled"),
                @Index(columnList = "musicEnabled"),
                @Index(columnList = "defaultConversationId")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"id"})}
)
public class DiscordGuild extends TimedRow {
    @Transient
    private final io.manebot.database.Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int discordGuildId;

    @Column()
    private String id;

    @Column(nullable = true)
    private String displayName;

    @Column()
    private boolean enabled = true;

    @Column()
    private boolean musicEnabled = true;

    @Column()
    private int idleTimeout = 600;

    @ManyToOne(optional = true)
    @JoinColumn(name = "defaultConversationId")
    private Conversation defaultConversation;

    public DiscordGuild(Database database) {
        this.database = database;
    }

    public DiscordGuild(Database database, String id) {
        this(database);

        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        if (this.musicEnabled != musicEnabled) {
            this.musicEnabled = database.execute(s -> {
                DiscordGuild model = s.find(DiscordGuild.class, discordGuildId);
                model.musicEnabled = musicEnabled;
                return musicEnabled;
            });
        }
    }

    public int getIdleTimeout() {
        return Math.max(0, idleTimeout);
    }

    public void setIdleTimeout(int idleTimeout) {
        if (this.idleTimeout != idleTimeout) {
            this.idleTimeout = database.execute(s -> {
                DiscordGuild model = s.find(DiscordGuild.class, discordGuildId);
                model.idleTimeout = idleTimeout;
                return idleTimeout;
            });
        }
    }

    public void remove() {
        database.execute(s -> { s.remove(DiscordGuild.this); });
    }

    public Conversation getDefaultConversation() {
        return defaultConversation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (!this.displayName.equals(displayName)) {
            this.displayName = database.execute(s -> {
                DiscordGuild model = s.find(DiscordGuild.class, discordGuildId);
                model.displayName = displayName;
                return displayName;
            });
        }
    }
}
