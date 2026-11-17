
package com.selixe.economy.bukkit.command

import com.selixe.economy.api.EconomyAPI
import com.selixe.economy.bukkit.EconomyPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class EconomyCommand(
    private val plugin: EconomyPlugin,
    private val economy: EconomyAPI
) : CommandExecutor, TabCompleter {

    private val miniMessage = MiniMessage.miniMessage()
    private val prefix = "<dark_gray>[<green>Economy</green>]</dark_gray> <gray>"

    private fun CommandSender.reply(message: String) {
        sendMessage(miniMessage.deserialize(prefix + message))
    }

    private fun findPlayer(name: String): OfflinePlayer? {
        Bukkit.getPlayerExact(name)?.let { return it }

        return Bukkit.getOfflinePlayers().firstOrNull {
            it.name?.equals(name, ignoreCase = true) == true
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            sender.reply("<green><bold>Economy Commands</bold>")
            sender.reply("<white>/$label balance [player] <dark_gray>- <gray>View balance")

            if (sender.hasPermission("economy.admin")) {
                sender.reply("<white>/$label add <player> <amount> <gray>Add to balance")
                sender.reply("<white>/$label subtract <player> <amount> <gray>Subtract from balance")
                sender.reply("<white>/$label set <player> <amount> <gray>Set balance")
            }

            return true
        }

        val action = args[0].lowercase()

        if (action == "balance") {
            if (args.size > 2) {
                sender.reply("<red>Usage: /$label balance [player]")
                return true
            }

            val target = if (args.size == 1) {
                if (sender !is Player) {
                    sender.reply("<red>Specify a player.")
                    return true
                }

                if (!sender.hasPermission("economy.balance")) {
                    sender.reply("<red>You don't have permission.")
                    return true
                }

                sender
            } else {
                if (!sender.hasPermission("economy.balance.others")) {
                    sender.reply("<red>You don't have permission.")
                    return true
                }

                findPlayer(args[1])
            }

            if (target == null) {
                sender.reply("<red>Player not found.")
                return true
            }

            economy.getBalanceAsync(target.uniqueId).whenComplete { balance, error ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (error != null) {
                        sender.reply("<red>Failed to load balance.")
                        return@Runnable
                    }

                    sender.sendMessage(
                        miniMessage.deserialize(
                            "$prefix<white><player><gray>'s balance: <green><amount>",
                            Placeholder.unparsed("player", target.name ?: "Unknown"),
                            Placeholder.unparsed("amount", balance.toString())
                        )
                    )
                })
            }

            return true
        }

        if (action !in listOf("add", "subtract", "set")) {
            sender.reply("<red>Unknown command. Use /$label help.")
            return true
        }

        if (!sender.hasPermission("economy.admin")) {
            sender.reply("<red>You don't have permission.")
            return true
        }

        if (args.size != 3) {
            sender.reply("<red>Usage: /$label $action <player> <amount>")
            return true
        }

        val target = findPlayer(args[1])

        if (target == null) {
            sender.reply("<red>Player not found.")
            return true
        }

        val amount = args[2].toLongOrNull()

        if (amount == null || amount < 0 || (action != "set" && amount == 0L)) {
            sender.reply("<red>Invalid amount.")
            return true
        }

        val future = when (action) {
            "add" -> economy.add(target.uniqueId, amount)
            "subtract" -> economy.subtract(target.uniqueId, amount)
            else -> economy.set(target.uniqueId, amount)
        }

        future.whenComplete { success, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                when {
                    error != null -> {
                        sender.reply("<red>A database error occurred.")
                        plugin.logger.warning(
                            "Failed to $action balance for ${target.name}: ${error.message}"
                        )
                    }

                    success -> {
                        sender.sendMessage(
                            miniMessage.deserialize(
                                "$prefix<green>Successfully updated <white><player><green>'s balance.",
                                Placeholder.unparsed("player", target.name ?: "Unknown")
                            )
                        )
                    }

                    else -> sender.reply("<red>Operation rejected. Check the player's funds.")
                }
            })
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        val suggestions = when (args.size) {
            1 -> buildList {
                add("help")

                if (
                    sender.hasPermission("economy.balance") ||
                    sender.hasPermission("economy.balance.others")
                ) {
                    add("balance")
                }

                if (sender.hasPermission("economy.admin")) {
                    addAll(listOf("add", "subtract", "set"))
                }
            }

            2 -> {
                val allowed = when (args[0].lowercase()) {
                    "balance" -> sender.hasPermission("economy.balance.others")
                    "add", "subtract", "set" -> sender.hasPermission("economy.admin")
                    else -> false
                }

                if (allowed) {
                    Bukkit.getOnlinePlayers().map { it.name }
                } else {
                    emptyList()
                }
            }

            3 -> {
                if (
                    args[0].lowercase() in listOf("add", "subtract", "set") &&
                    sender.hasPermission("economy.admin")
                ) {
                    listOf("100", "1000", "10000")
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }

        return suggestions.filter {
            it.startsWith(args.last(), ignoreCase = true)
        }
    }
}
