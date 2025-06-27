package org.arch.me.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtil {

    /**
     * Send a message with color codes translated
     */
    public static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Send multiple messages
     */
    public static void sendMessages(CommandSender sender, String... messages) {
        for (String message : messages) {
            sendMessage(sender, message);
        }
    }

    /**
     * Send action bar message to player
     */
    public static void sendActionBar(Player player, String message) {
        if (message == null || message.isEmpty()) return;

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
        } catch (Exception e) {
            // Fallback to regular message if action bar fails
            sendMessage(player, message);
        }
    }

    /**
     * Send title and subtitle to player
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (title == null && subtitle == null) return;

        try {
            player.sendTitle(
                    title != null ? ChatColor.translateAlternateColorCodes('&', title) : "",
                    subtitle != null ? ChatColor.translateAlternateColorCodes('&', subtitle) : "",
                    fadeIn, stay, fadeOut
            );
        } catch (Exception e) {
            // Fallback to chat message
            if (title != null) sendMessage(player, title);
            if (subtitle != null) sendMessage(player, subtitle);
        }
    }

    /**
     * Format a message with color codes
     */
    public static String format(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Strip color codes from message
     */
    public static String stripColors(String message) {
        return ChatColor.stripColor(message);
    }

    /**
     * Send colored message with prefix
     */
    public static void sendPrefixedMessage(CommandSender sender, String prefix, String message) {
        sendMessage(sender, prefix + " " + message);
    }

    /**
     * Send error message
     */
    public static void sendError(CommandSender sender, String message) {
        sendMessage(sender, "&c" + message);
    }

    /**
     * Send success message
     */
    public static void sendSuccess(CommandSender sender, String message) {
        sendMessage(sender, "&a" + message);
    }

    /**
     * Send warning message
     */
    public static void sendWarning(CommandSender sender, String message) {
        sendMessage(sender, "&e" + message);
    }

    /**
     * Send info message
     */
    public static void sendInfo(CommandSender sender, String message) {
        sendMessage(sender, "&b" + message);
    }

    /**
     * Center text for chat
     */
    public static String centerText(String text) {
        if (text == null || text.isEmpty()) return "";

        int maxWidth = 80;
        int spaces = (maxWidth - stripColors(text).length()) / 2;

        StringBuilder centered = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            centered.append(" ");
        }
        centered.append(text);

        return centered.toString();
    }
}