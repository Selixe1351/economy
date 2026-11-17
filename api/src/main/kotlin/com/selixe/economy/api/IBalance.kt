package com.selixe.economy.api

import java.util.UUID
import java.util.concurrent.CompletableFuture

interface IBalance {

    val userId: UUID

    fun get(): Long
    fun add(amount: Long): CompletableFuture<Boolean>
    fun subtract(amount: Long): CompletableFuture<Boolean>
    fun set(amount: Long): CompletableFuture<Boolean>

}