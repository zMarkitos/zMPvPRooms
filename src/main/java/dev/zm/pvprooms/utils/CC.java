package dev.zm.pvprooms.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CC {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public static String translate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = translateHex(text);
        text = ChatColor.translateAlternateColorCodes('&', text);

        if (shouldParseMiniMessage(text)) {
            try {
                Component component = MINI_MESSAGE.deserialize(text);
                return LEGACY_SERIALIZER.serialize(component);
            } catch (Exception ignored) {
                // Text can contain angle-bracket placeholders like <name>; keep legacy text.
                return text;
            }
        }

        return text;
    }

    private static boolean shouldParseMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (!(text.contains("<") && text.contains(">"))) {
            return false;
        }

        // Avoid parsing legacy-formatted strings in MiniMessage.
        return !text.contains("\u00A7");
    }

    private static String translateHex(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(builder, net.md_5.bungee.api.ChatColor.of("#" + color).toString());
        }
        return matcher.appendTail(builder).toString();
    }

    public static List<String> translate(List<String> textList) {
        if (textList == null) {
            return null;
        }
        return textList.stream().map(CC::translate).collect(Collectors.toList());
    }

    public static Component parse(String text) {
        if (text == null) {
            return Component.empty();
        }
        String translated = translate(text);
        return LEGACY_SERIALIZER.deserialize(translated);
    }
}
