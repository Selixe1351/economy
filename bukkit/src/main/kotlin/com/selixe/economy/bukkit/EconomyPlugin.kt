package com.selixe.economy.bukkit

import com.selixe.economy.api.EconomyAPI
import com.selixe.economy.bukkit.command.EconomyCommand
import com.selixe.economy.bukkit.database.MongoManager
import com.selixe.economy.bukkit.listener.EconomyListener
import com.selixe.economy.bukkit.redis.RedisManager
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class EconomyPlugin : JavaPlugin() {

    private lateinit var economyManager: EconomyManager
    private lateinit var economyService: EconomyService

    private lateinit var mongoManager: MongoManager

    private var redisManager: RedisManager? = null

    override fun onEnable() {
        instance = this

        saveDefaultConfig()

        val mongoDatabase = config.getString("mongo.database") ?: error("Mongo database is not set")
        val mongoUri = config.getString("mongo.uri") ?: error("Mongo URI is not set")

        mongoManager = MongoManager(databaseName = mongoDatabase)
        mongoManager.createConnection(uri = mongoUri)

        val redisUri = config.getString("redis.uri")
        val redisChannel = config.getString("redis.channel")

        if (!redisUri.isNullOrBlank() && !redisChannel.isNullOrBlank()) {
            var redis: RedisManager? = null

            try {
                redis = RedisManager(redisUri, redisChannel)
                check(redis.ping()) { "Redis server did not respond to PING" }

                redisManager = redis
                logger.info("Redis initialized.")
            } catch (e: Exception) {
                redis?.close()
                logger.warning("Failed to initialize Redis: ${e.message}")
            }
        } else {
            logger.info("Redis is not configured. Cross-server synchronization disabled.")
        }

        redisManager?.subscribe { message ->
            val userId = runCatching {
                UUID.fromString(message)
            }.getOrNull() ?: return@subscribe

            economyManager.refreshBalance(userId).exceptionally { error ->
                logger.warning("Failed to refresh balance for $userId: ${error.message}")
                null
            }
        }

        economyManager = EconomyManager(mongoManager, redisManager)
        economyService = EconomyService(economyManager)

        economyManager.initialize(economyService)

        server.pluginManager.registerEvents(
            EconomyListener(economyManager),
            this
        )

        val handler = EconomyCommand(this, economyService, economyManager)

        getCommand("economy")?.apply {
            setExecutor(handler)
            tabCompleter = handler
        }

    }

    override fun onDisable() {
        instance = null

        redisManager?.close()

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
