package com.selixe.economy.bukkit

import com.selixe.economy.api.EconomyAPI
import com.selixe.economy.bukkit.command.EconomyCommand
import com.selixe.economy.bukkit.database.MongoManager
import com.selixe.economy.bukkit.listener.EconomyListener
import org.bukkit.plugin.java.JavaPlugin

class EconomyPlugin : JavaPlugin() {

    private lateinit var economyManager: EconomyManager
    private lateinit var economyService: EconomyService

    private lateinit var mongoManager: MongoManager

    override fun onEnable() {
        instance = this

        saveDefaultConfig()

        val mongoDatabase = config.getString("mongo.database") ?: error("Mongo database is not set")
        val mongoUri = config.getString("mongo.uri") ?: error("Mongo URI is not set")

        mongoManager = MongoManager(databaseName = mongoDatabase)
        mongoManager.createConnection(uri = mongoUri)

        economyManager = EconomyManager(mongoManager)
        economyService = EconomyService(economyManager)

        economyManager.initialize(economyService)

        server.pluginManager.registerEvents(
            EconomyListener(economyManager),
            this
        )

        getCommand("economy")?.setExecutor(EconomyCommand(this, economyService))
    }

    override fun onDisable() {
        instance = null

        if (::economyManager.isInitialized) {
            economyManager.shutdown()
        }

        if (::mongoManager.isInitialized) {
            mongoManager.closeConnection()
        }
    }

    companion object {

        private var instance: EconomyPlugin? = null

        fun api(): EconomyAPI = instance?.economyService ?: error("EconomyPlugin is not initialized")

    }

}
