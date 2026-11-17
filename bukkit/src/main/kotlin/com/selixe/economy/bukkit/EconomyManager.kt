package com.selixe.economy.bukkit

import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.selixe.economy.bukkit.database.MongoManager
import com.selixe.economy.bukkit.model.Balance
import com.selixe.economy.bukkit.redis.RedisManager
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class EconomyManager(
    mongoManager: MongoManager,
    private val redisManager: RedisManager? = null
) {

    private lateinit var economyService: EconomyService

    private val collection = mongoManager.getCollection("balances")

    private val executor = Executors.newFixedThreadPool(4)

    fun initialize(service: EconomyService) {
        economyService = service
    }

    private val loading = ConcurrentHashMap<UUID, CompletableFuture<Balance>>()

    private val balances = Caffeine.newBuilder()
        .maximumSize(10_000)
        .build<UUID, Balance>()

    fun getBalance(userId: UUID): Balance? {
        return balances.getIfPresent(userId)
    }

    fun getBalanceAsync(userId: UUID): CompletableFuture<Long> {
        return CompletableFuture.supplyAsync({
            val document = collection.find(
                Filters.eq("_id", userId.toString())
            ).first()

            document?.getLong("balance") ?: 0L
        }, executor)
    }

    fun refreshBalance(userId: UUID): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            val balance = balances.getIfPresent(userId)
                ?: return@runAsync

            val document = collection.find(
                Filters.eq("_id", userId.toString())
            ).first()

            val newBalance = document?.getLong("balance") ?: 0L

            balance.update(newBalance)
        }, executor)
    }

    fun loadBalance(userId: UUID): CompletableFuture<Balance> {
        synchronized(loading) {
            balances.getIfPresent(userId)?.let {
                return CompletableFuture.completedFuture(it)
            }

            loading[userId]?.let {
                return it
            }

            val future = CompletableFuture.supplyAsync({
                val document = collection.find(
                    Filters.eq("_id", userId.toString())
                ).first()

                val amount = document?.getLong("balance") ?: 0L

                val balance = Balance(userId, economyService, amount)

                balances.put(userId, balance)

                balance
            }, executor)

            loading[userId] = future

            future.whenComplete { _, _ ->
                synchronized(loading) {
                    loading.remove(userId, future)
                }
            }

            return future
        }
    }

    fun unloadBalance(userId: UUID) {
        balances.invalidate(userId)
    }

    fun add(userId: UUID, amount: Long): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val document = collection.findOneAndUpdate(
                Filters.eq("_id", userId.toString()),
                Updates.inc("balance", amount),
                FindOneAndUpdateOptions()
                    .upsert(true)
                    .returnDocument(ReturnDocument.AFTER)
            ) ?: error("Failed to update balance")

            val newBalance = document.getLong("balance")

            balances.getIfPresent(userId)?.update(newBalance)

            try {
                redisManager?.publish(userId.toString())
            } catch (e: Exception) {
                error("Failed to publish balance update: ${e.message}")
            }

            true
        }, executor)
    }

    fun subtract(userId: UUID, amount: Long): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val document = collection.findOneAndUpdate(
                Filters.and(
                    Filters.eq("_id", userId.toString()),
                    Filters.gte("balance", amount)
                ),
                Updates.inc("balance", -amount),
                FindOneAndUpdateOptions()
                    .returnDocument(ReturnDocument.AFTER)
            )

            if (document == null) {
                return@supplyAsync false
            }

            val newBalance = document.getLong("balance")

            balances.getIfPresent(userId)?.update(newBalance)

            try {
                redisManager?.publish(userId.toString())
            } catch (e: Exception) {
                error("Failed to publish balance update: ${e.message}")
            }

            true
        }, executor)
    }

    fun set(userId: UUID, amount: Long): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val document = collection.findOneAndUpdate(
                Filters.eq("_id", userId.toString()),
                Updates.set("balance", amount),
                FindOneAndUpdateOptions()
                    .upsert(true)
                    .returnDocument(ReturnDocument.AFTER)
            ) ?: error("Failed to update balance")

            val newBalance = document.getLong("balance")

            balances.getIfPresent(userId)?.update(newBalance)

            try {
                redisManager?.publish(userId.toString())
            } catch (e: Exception) {
                error("Failed to publish balance update: ${e.message}")
            }

            true
        }, executor)
    }

    fun shutdown() {
        executor.shutdown()
        balances.invalidateAll()
    }

}