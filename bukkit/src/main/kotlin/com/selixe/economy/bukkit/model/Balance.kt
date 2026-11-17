package com.selixe.economy.bukkit.model

import com.selixe.economy.api.IBalance
import com.selixe.economy.bukkit.EconomyService
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

class Balance(override val userId: UUID, private val economyService: EconomyService, initialBalance: Long = 0L) : IBalance {

    private val value: AtomicLong = AtomicLong(initialBalance)

    override fun get(): Long {
        return value.get()
    }

    override fun add(amount: Long): CompletableFuture<Boolean> {
        return economyService.add(userId, amount)
    }

    override fun subtract(amount: Long): CompletableFuture<Boolean> {
        return economyService.subtract(userId, amount)
    }

    override fun set(amount: Long): CompletableFuture<Boolean> {
        return economyService.set(userId, amount)
    }

    internal fun update(newBalance: Long) {
        value.set(newBalance)
    }

}