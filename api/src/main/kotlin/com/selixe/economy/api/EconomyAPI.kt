package com.selixe.economy.api

import java.util.*
import java.util.concurrent.CompletableFuture

interface EconomyAPI {

    fun getBalance(userId: UUID): IBalance

    fun add(userId: UUID, amount: Long): CompletableFuture<Boolean>
    fun subtract(userId: UUID, amount: Long): CompletableFuture<Boolean>
    fun set(userId: UUID, amount: Long): CompletableFuture<Boolean>

}