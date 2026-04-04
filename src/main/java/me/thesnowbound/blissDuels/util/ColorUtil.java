package me.thesnowbound.blissDuels.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Skript-style color tokens into Bukkit color codes.
 */
public final class ColorUtil {
    private static final Pattern HEX_TOKEN = Pattern.compile("<#{1,2}([A-Fa-f0-9]{6})>");

    private ColorUtil() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String withHex = replaceHexTokens(input);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    public static List<String> color(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }

        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    private static String replaceHexTokens(String input) {
        Matcher matcher = HEX_TOKEN.matcher(input);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }

        matcher.appendTail(out);
        return out.toString();
    }
}
