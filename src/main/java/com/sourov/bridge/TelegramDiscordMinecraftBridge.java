package com.sourov.bridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.kyori.adventure.text.Component;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class TelegramDiscordMinecraftBridge extends JavaPlugin {

    private JDA discord;

    private String telegramToken;
    private String telegramAdminId;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getLogger().info("Telegram + Discord Minecraft Bridge starting...");

        telegramToken = getConfig().getString("telegram.bot-token", "");
        telegramAdminId = getConfig().getString("telegram.admin-id", "");

        if (getConfig().getBoolean("discord.enabled", true)) {
            startDiscord();
        }

        if (getConfig().getBoolean("telegram.enabled", true)) {
            startTelegram();
        }

        getLogger().info("Bridge enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (discord != null) {
            discord.shutdown();
        }

        getLogger().info("Bridge disabled.");
    }

    // =========================================================
    // DISCORD
    // =========================================================

    private void startDiscord() {
        String token = getConfig().getString("discord.bot-token", "");

        if (token.isBlank() || token.startsWith("PUT_")) {
            getLogger().warning("Discord bot token is not configured.");
            return;
        }

        try {
            discord = JDABuilder.createDefault(token)
                    .addEventListeners(new DiscordListener())
                    .build();

            discord.awaitReady();

            discord.updateCommands()
                    .addCommands(
                            Commands.slash("status", "Minecraft server status"),
                            Commands.slash("players", "Show online players"),
                            Commands.slash("tps", "Show server TPS"),
                            Commands.slash("minecraft", "Show Minecraft information"),

                            Commands.slash("say", "Send a message to Minecraft")
                                    .addOption(OptionType.STRING, "message", "Message", true),

                            Commands.slash("broadcast", "Broadcast a message")
                                    .addOption(OptionType.STRING, "message", "Message", true),

                            Commands.slash("kick", "Kick a player")
                                    .addOption(OptionType.STRING, "player", "Player name", true),

                            Commands.slash("ban", "Ban a player")
                                    .addOption(OptionType.STRING, "player", "Player name", true),

                            Commands.slash("whitelist", "Add player to whitelist")
                                    .addOption(OptionType.STRING, "player", "Player name", true),

                            Commands.slash("tp", "Teleport yourself to a player")
                                    .addOption(OptionType.STRING, "player", "Player name", true),

                            Commands.slash("weather", "Change weather")
                                    .addOption(OptionType.STRING, "type", "clear or rain", true),

                            Commands.slash("time", "Change world time")
                                    .addOption(OptionType.STRING, "type", "day or night", true),

                            Commands.slash("op", "Give operator permission to a player")
                                    .addOption(OptionType.STRING, "player", "Player name", true)
                    )
                    .queue();

            getLogger().info("Discord bot connected.");

        } catch (Exception e) {
            getLogger().severe("Discord startup failed: " + e.getMessage());
        }
    }

    private class DiscordListener extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

            String command = event.getName();

            switch (command) {

                case "status":
                    event.reply(getStatus()).queue();
                    break;

                case "players":
                    event.reply(getPlayers()).queue();
                    break;

                case "tps":
                    event.reply(getTPS()).queue();
                    break;

                case "minecraft":
                    event.reply(
                            "🎮 **Minecraft Server**\n\n" +
                            getStatus() + "\n" +
                            getTPS()
                    ).queue();
                    break;

                case "say":
                    String say = event.getOption("message").getAsString();
                    runMinecraftCommand("say " + say);
                    event.reply("✅ Message sent to Minecraft.").queue();
                    break;

                case "broadcast":
                    String broadcast = event.getOption("message").getAsString();
                    runMinecraftCommand("broadcast " + broadcast);
                    event.reply("📢 Broadcast sent.").queue();
                    break;

                case "kick":
                    String kickPlayer = event.getOption("player").getAsString();
                    runMinecraftCommand("kick " + kickPlayer);
                    event.reply("👢 Kicked: `" + kickPlayer + "`").queue();
                    break;

                case "ban":
                    String banPlayer = event.getOption("player").getAsString();
                    runMinecraftCommand("ban " + banPlayer);
                    event.reply("🔨 Banned: `" + banPlayer + "`").queue();
                    break;

                case "whitelist":
                    String whitelistPlayer =
                            event.getOption("player").getAsString();

                    runMinecraftCommand("whitelist add " + whitelistPlayer);

                    event.reply(
                            "✅ Added to whitelist: `" +
                                    whitelistPlayer + "`"
                    ).queue();
                    break;

                case "tp":
                    String tpPlayer =
                            event.getOption("player").getAsString();

                    event.reply(
                            "ℹ️ Discord cannot directly move your Minecraft "
                                    + "character because Discord is not a Minecraft player."
                    ).queue();
                    break;

                case "weather":
                    String weather =
                            event.getOption("type").getAsString().toLowerCase();

                    if (!weather.equals("clear") &&
                            !weather.equals("rain")) {

                        event.reply("❌ Use `clear` or `rain`.").queue();
                        return;
                    }

                    runMinecraftCommand("weather " + weather);
                    event.reply("🌦️ Weather changed to `" + weather + "`.").queue();
                    break;

                case "time":
                    String time =
                            event.getOption("type").getAsString().toLowerCase();

                    if (time.equals("day")) {
                        runMinecraftCommand("time set day");
                    } else if (time.equals("night")) {
                        runMinecraftCommand("time set night");
                    } else {
                        event.reply("❌ Use `day` or `night`.").queue();
                        return;
                    }

                    event.reply("🕐 Time changed to `" + time + "`.").queue();
                    break;

                case "op":
                    String opPlayer =
                            event.getOption("player").getAsString();

                    runMinecraftCommand("op " + opPlayer);

                    event.reply(
                            "👑 OP granted to `" + opPlayer + "`."
                    ).queue();
                    break;

                default:
                    event.reply("❌ Unknown command.").queue();
            }
        }
    }

    // =========================================================
    // MINECRAFT
    // =========================================================

    private String getStatus() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        return "🟢 **Server Online**\n" +
                "👥 Players: `" + online + "/" + max + "`";
    }

    private String getPlayers() {
        List<Player> players =
                Bukkit.getOnlinePlayers().stream().toList();

        if (players.isEmpty()) {
            return "👥 **Online Players**\n\nNo players online.";
        }

        String names = players.stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));

        return "👥 **Online Players (" + players.size() + ")**\n\n" +
                names;
    }

    private String getTPS() {
        double[] tps = Bukkit.getTPS();

        double current = Math.min(tps[0], 20.0);

        return String.format(
                "📊 **TPS:** `%.2f`",
                current
        );
    }

    private void runMinecraftCommand(String command) {
        Bukkit.getScheduler().runTask(
                this,
                () -> Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                )
        );
    }

    // =========================================================
    // TELEGRAM
    // =========================================================

    private void startTelegram() {

        if (telegramToken.isBlank() ||
                telegramToken.startsWith("PUT_")) {

            getLogger().warning(
                    "Telegram bot token is not configured."
            );
            return;
        }

        Thread telegramThread = new Thread(
                this::telegramLoop,
                "Telegram-Minecraft-Bridge"
        );

        telegramThread.setDaemon(true);
        telegramThread.start();

        getLogger().info("Telegram bot started.");
    }

    private void telegramLoop() {

        long offset = 0;

        while (Bukkit.getPluginManager()
                .isPluginEnabled(this)) {

            try {

                String url =
                        "https://api.telegram.org/bot" +
                                telegramToken +
                                "/getUpdates?timeout=25&offset=" +
                                offset;

                HttpURLConnection connection =
                        (HttpURLConnection)
                                URI.create(url)
                                        .toURL()
                                        .openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(35000);

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                String response =
                        reader.lines()
                                .collect(Collectors.joining());

                reader.close();

                String[] updates =
                        response.split("\"update_id\":");

                for (int i = 1; i < updates.length; i++) {

                    String update = updates[i];

                    try {
                        long updateId =
                                Long.parseLong(
                                        update
                                                .split(",")[0]
                                                .replaceAll(
                                                        "[^0-9]",
                                                        ""
                                                )
                                );

                        offset = Math.max(
                                offset,
                                updateId + 1
                        );

                        handleTelegramUpdate(update);

                    } catch (Exception ignored) {
                    }
                }

            } catch (Exception e) {

                getLogger().warning(
                        "Telegram connection error: " +
                                e.getMessage()
                );

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleTelegramUpdate(String update) {

        if (!update.contains("\"message\"")) {
            return;
        }

        String chatId = extractJsonValue(
                update,
                "\"chat\":{\"id\":"
        );

        String text = extractJsonValue(
                update,
                "\"text\":\""
        );

        if (chatId == null || text == null) {
            return;
        }

        String userId = extractJsonValue(
                update,
                "\"from\":{\"id\":"
        );

        if (userId == null) {
            return;
        }

        // Telegram is ADMIN ONLY
        if (!userId.equals(telegramAdminId)) {
            sendTelegram(
                    chatId,
                    "⛔ You are not authorized to use this bot."
            );
            return;
        }

        String command = text.trim();

        if (command.equals("/start")) {

            sendTelegram(
                    chatId,
                    "🤖 Minecraft Bridge\n\n" +
                            "/status\n" +
                            "/players\n" +
                            "/tps\n" +
                            "/say message\n" +
                            "/broadcast message\n" +
                            "/kick player\n" +
                            "/ban player\n" +
                            "/whitelist player\n" +
                            "/weather clear|rain\n" +
                            "/time day|night\n" +
                            "/op player"
            );

        } else if (command.equals("/status")) {

            sendTelegram(chatId, getStatus());

        } else if (command.equals("/players")) {

            sendTelegram(chatId, getPlayers());

        } else if (command.equals("/tps")) {

            sendTelegram(chatId, getTPS());

        } else if (command.startsWith("/say ")) {

            String message =
                    command.substring(5).trim();

            runMinecraftCommand(
                    "say " + message
            );

            sendTelegram(
                    chatId,
                    "✅ Message sent."
            );

        } else if (command.startsWith("/broadcast ")) {

            String message =
                    command.substring(11).trim();

            runMinecraftCommand(
                    "broadcast " + message
            );

            sendTelegram(
                    chatId,
                    "📢 Broadcast sent."
            );

        } else if (command.startsWith("/kick ")) {

            String player =
                    command.substring(6).trim();

            runMinecraftCommand(
                    "kick " + player
            );

            sendTelegram(
                    chatId,
                    "👢 Kicked: " + player
            );

        } else if (command.startsWith("/ban ")) {

            String player =
                    command.substring(5).trim();

            runMinecraftCommand(
                    "ban " + player
            );

            sendTelegram(
                    chatId,
                    "🔨 Banned: " + player
            );

        } else if (command.startsWith("/whitelist ")) {

            String player =
                    command.substring(11).trim();

            runMinecraftCommand(
                    "whitelist add " + player
            );

            sendTelegram(
                    chatId,
                    "✅ Whitelisted: " + player
            );

        } else if (command.startsWith("/weather ")) {

            String weather =
                    command.substring(9).trim();

            if (!weather.equals("clear") &&
                    !weather.equals("rain")) {

                sendTelegram(
                        chatId,
                        "❌ Use /weather clear or /weather rain"
                );
                return;
            }

            runMinecraftCommand(
                    "weather " + weather
            );

            sendTelegram(
                    chatId,
                    "🌦️ Weather changed to " + weather
            );

        } else if (command.startsWith("/time ")) {

            String time =
                    command.substring(6).trim();

            if (time.equals("day")) {

                runMinecraftCommand(
                        "time set day"
                );

            } else if (time.equals("night")) {

                runMinecraftCommand(
                        "time set night"
                );

            } else {

                sendTelegram(
                        chatId,
                        "❌ Use /time day or /time night"
                );
                return;
            }

            sendTelegram(
                    chatId,
                    "🕐 Time changed to " + time
            );

        } else if (command.startsWith("/op ")) {

            String player =
                    command.substring(4).trim();

            runMinecraftCommand(
                    "op " + player
            );

            sendTelegram(
                    chatId,
                    "👑 OP granted to " + player
            );

        } else {

            sendTelegram(
                    chatId,
                    "❌ Unknown command.\nUse /start"
            );
        }
    }

    private String extractJsonValue(
            String json,
            String key
    ) {

        int start = json.indexOf(key);

        if (start == -1) {
            return null;
        }

        start += key.length();

        if (start >= json.length()) {
            return null;
        }

        if (json.charAt(start) == '"') {
            start++;
        }

        int end;

        if (json.charAt(start - 1) == '"') {
            end = json.indexOf('"', start);
        } else {
            end = json.indexOf(',', start);
        }

        if (end == -1) {
            end = json.indexOf('}', start);
        }

        if (end == -1) {
            return null;
        }

        return json.substring(start, end);
    }

        private void sendTelegram(
            String chatId,
            String message
    ) {

        try {

            String encodedMessage =
                    URLEncoder.encode(
                            message,
                            StandardCharsets.UTF_8
                    );

            String url =
                    "https://api.telegram.org/bot" +
                            telegramToken +
                            "/sendMessage?chat_id=" +
                            chatId +
                            "&text=" +
                            encodedMessage;

            HttpURLConnection connection =
                    (HttpURLConnection)
                            URI.create(url)
                                    .toURL()
                                    .openConnection();

            connection.setRequestMethod("GET");
            connection.getResponseCode();

        } catch (Exception e) {

            getLogger().warning(
                    "Telegram send failed: " +
                            e.getMessage()
            );
        }
    }
}
