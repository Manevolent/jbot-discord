package io.manebot.plugin.discord.platform.chat;

import io.manebot.chat.Chat;
import io.manebot.chat.TextFormat;
import io.manebot.chat.TextStyle;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.discord.platform.user.DiscordPlatformUser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class DiscordTextFormat implements TextFormat {
    public static DiscordTextFormat INSTANCE = new DiscordTextFormat();

    @Override
    public boolean shouldMention(PlatformUser user) {
        return true;
    }

    @Override
    public String mention(Chat target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String mention(PlatformUser user) {
        if (user instanceof DiscordPlatformUser)
            return ((DiscordPlatformUser) user).getUser().getAsMention();
        else
            return TextFormat.super.mention(user);
    }

    @Override
    public String format(String string, EnumSet<TextStyle> styles) {
        if (string.trim().length() <= 0) return string;

        List<TextStyle> list = new ArrayList<>(styles);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < list.size(); i ++)
            builder.append(styleToKey(list.get(i)));

        builder.append(escape(string));


        for (int i = list.size()-1; i >= 0; i --)
            builder.append(styleToKey(list.get(i)));

        return builder.toString();
    }

    private static String styleToKey(TextStyle style) {
        switch (style) {
            case BOLD:
                return "**";
            case ITALICS:
                return "_";
            case STRIKE_THROUGH:
                return "~~";
            case UNDERLINE:
                return "__";
            case CODE_BLOCK:
                return "```";
            case INLINE_CODE:
                return "`";
            default:
                throw new UnsupportedOperationException(style.name());
        }
    }

    @Override
    public String escape(String string) {
        StringBuilder builder = new StringBuilder();

        for (char character : string.toCharArray()) {
            if (!Character.isLetterOrDigit(character) && !Character.isWhitespace(character))
                builder.append('\\');

            builder.append(character);
        }

        return builder.toString();
    }

}
