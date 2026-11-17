package com.selixe.economy.bukkit.listener

import com.selixe.economy.bukkit.EconomyManager
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.concurrent.TimeUnit

class EconomyListener(val economyManager: EconomyManager) : Listener {

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        try {
            economyManager.loadBalance(event.uniqueId)
                .get(5, TimeUnit.SECONDS)

            economyManager.updateUsername(event.uniqueId, event.name)
                .exceptionally { error ->
                    error.printStackTrace()
                    null
                }
        } catch (_: Exception) {
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                Component.text("Failed to load your balance. Please try again.")
            )
        }
    }

    @EventHandler
    fun onDisconnect(event: PlayerQuitEvent) {
        economyManager.unloadBalance(event.player.uniqueId)
    }

}