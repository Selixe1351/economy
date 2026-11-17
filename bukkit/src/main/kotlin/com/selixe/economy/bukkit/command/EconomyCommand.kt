
package com.selixe.economy.bukkit.command

import com.selixe.economy.api.EconomyAPI
import com.selixe.economy.bukkit.EconomyPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class EconomyCommand(
    private val plugin: EconomyPlugin,
    private val economyAPI: EconomyAPI
) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only.")
            return true
        }

        val balance = economyAPI.getBalance(sender.uniqueId)

        if (args.isEmpty() || args[0].equals("balance", true)) {
            sender.sendMessage("Balance: ${balance.get()}")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("Usage: /economy <add|subtract|set> <amount>")
            return true
        }

        val amount = args[1].toLongOrNull()

        if (amount == null) {
            sender.sendMessage("Invalid amount.")
            return true
        }

        val future = when (args[0].lowercase()) {
            "add" -> balance.add(amount)
            "subtract" -> balance.subtract(amount)
            "set" -> balance.set(amount)
            else -> {
                sender.sendMessage("Unknown operation.")
                return true
            }
        }

        future.whenComplete { success, error ->
            if (!sender.isOnline) return@whenComplete

            sender.server.scheduler.runTask(
                plugin
            ) { ->
                if (error != null) {
                    sender.sendMessage("Database error.")
                } else if (success) {
                    sender.sendMessage("New balance: ${balance.get()}")
                } else {
                    sender.sendMessage("Operation rejected.")
                }
            }
        }

        return true
    }
}
