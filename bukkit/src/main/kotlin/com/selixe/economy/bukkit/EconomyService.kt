package com.selixe.economy.bukkit

import com.selixe.economy.api.IBalance
import com.selixe.economy.api.EconomyAPI
import java.util.UUID
import java.util.concurrent.CompletableFuture

class EconomyService(private val manager: EconomyManager) : EconomyAPI {

    override fun getBalance(userId: UUID): IBalance {
        return manager.getBalance(userId) ?: error("Balance not loaded: $userId")
    }

    override fun add(
        userId: UUID,
        amount: Long
    ): CompletableFuture<Boolean> {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false)
        }

        return manager.add(userId, amount)
    }

    override fun subtract(
        userId: UUID,
        amount: Long
    ): CompletableFuture<Boolean> {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false)
        }

        return manager.subtract(userId, amount)
    }

    override fun set(
        userId: UUID,
        amount: Long
    ): CompletableFuture<Boolean> {
        if (amount < 0) {
            return CompletableFuture.completedFuture(false)
        }

        return manager.set(userId, amount)
    }

}