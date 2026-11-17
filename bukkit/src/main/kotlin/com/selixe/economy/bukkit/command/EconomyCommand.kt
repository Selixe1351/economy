
package com.selixe.economy.bukkit.command

import com.selixe.economy.api.EconomyAPI
import com.selixe.economy.bukkit.EconomyManager
import com.selixe.economy.bukkit.EconomyPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

class EconomyCommand(
    private val plugin: EconomyPlugin,
    private val economy: EconomyAPI,
    private val manager: EconomyManager
) : CommandExecutor, TabCompleter {

    private val miniMessage = MiniMessage.miniMessage()
    private val prefix = "<dark_gray>[<green>Economy</green>]</dark_gray> <gray>"

    private fun CommandSender.reply(message: String) {
        sendMessage(miniMessage.deserialize(prefix + message))
    }

    private fun findPlayer(name: String): CompletableFuture<UUID?> {
        Bukkit.getPlayerExact(name)?.let {
            return CompletableFuture.completedFuture(it.uniqueId)
        }

        Bukkit.getOfflinePlayers().firstOrNull {
            it.name?.equals(name, ignoreCase = true) == true
        }?.let {
            return CompletableFuture.completedFuture(it.uniqueId)
        }

        return manager.findUserIdByName(name)
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

            val targetName: String
            val targetFuture: CompletableFuture<UUID?>

            if (args.size == 1) {
                if (sender !is Player) {
                    sender.reply("<red>Specify a player.")
                    return true
                }

                if (!sender.hasPermission("economy.balance")) {
                    sender.reply("<red>You don't have permission.")
                    return true
                }

                targetName = sender.name
                targetFuture = CompletableFuture.completedFuture(sender.uniqueId)
            } else {
                if (!sender.hasPermission("economy.balance.others")) {
                    sender.reply("<red>You don't have permission.")
                    return true
                }

                targetName = args[1]
                targetFuture = findPlayer(targetName)
            }

            targetFuture.whenComplete { userId, lookupError ->
                if (lookupError != null || userId == null) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.reply(
                            if (lookupError != null) "<red>Failed to look up player."
                            else "<red>Player not found."
                        )
                    })
                    return@whenComplete
                }

                economy.getBalanceAsync(userId).whenComplete { balance, error ->
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (error != null) {
                            sender.reply("<red>Failed to load balance.")
                            return@Runnable
                        }

                        sender.sendMessage(
                            miniMessage.deserialize(
                                "$prefix<white><player><gray>'s balance: <green><amount>",
                                Placeholder.unparsed("player", targetName),
                                Placeholder.unparsed("amount", balance.toString())
                            )
                        )
                    })
                }
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

        val amount = args[2].toLongOrNull()

        if (amount == null || amount < 0 || (action != "set" && amount == 0L)) {
            sender.reply("<red>Invalid amount.")
            return true
        }

        findPlayer(args[1]).whenComplete { userId, lookupError ->
            if (lookupError != null || userId == null) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.reply(
                        if (lookupError != null) "<red>Failed to look up player."
                        else "<red>Player not found."
                    )
                })
                return@whenComplete
            }

            val future = when (action) {
                "add" -> economy.add(userId, amount)
                "subtract" -> economy.subtract(userId, amount)
                else -> economy.set(userId, amount)
            }

            future.whenComplete { success, error ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    when {
                        error != null -> {
                            sender.reply("<red>A database error occurred.")
                            plugin.logger.warning(
                                "Failed to $action balance for ${args[1]}: ${error.message}"
                            )
                        }

                        success -> {
                            sender.sendMessage(
                                miniMessage.deserialize(
                                    "$prefix<green>Successfully updated <white><player><green>'s balance.",
                                    Placeholder.unparsed("player", args[1])
                                )
                            )
                        }

                        else -> sender.reply("<red>Operation rejected. Check the player's funds.")
                    }
                })
            }
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
