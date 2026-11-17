package com.selixe.economy.bukkit

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture

class EconomyServiceTest {

    private val manager = mockk<EconomyManager>()
    private val service = EconomyService(manager)
    private val userId = UUID.randomUUID()

    @Test
    fun `negative deposits are rejected`() {
        val result = service.add(userId, -100).join()

        assertFalse(result)

        verify(exactly = 0) {
            manager.add(any(), any())
        }
    }

    @Test
    fun `valid deposits are forwarded to manager`() {
        every {
            manager.add(userId, 100L)
        } returns CompletableFuture.completedFuture(true)

        val result = service.add(userId, 100L).join()

        assertTrue(result)

        verify(exactly = 1) {
            manager.add(userId, 100L)
        }
    }

    @Test
    fun `setting balance to zero is allowed`() {
        every {
            manager.set(userId, 0L)
        } returns CompletableFuture.completedFuture(true)

        val result = service.set(userId, 0L).join()

        assertTrue(result)

        verify(exactly = 1) {
            manager.set(userId, 0L)
        }
    }

    @Test
    fun `negative withdrawals are rejected`() {
        val result = service.subtract(userId, -100).join()

        assertFalse(result)

        verify(exactly = 0) {
            manager.subtract(any(), any())
        }
    }

    @Test
    fun `valid withdrawals are forwarded to manager`() {
        every {
            manager.subtract(userId, 100L)
        } returns CompletableFuture.completedFuture(true)

        val result = service.subtract(userId, 100L).join()

        assertTrue(result)

        verify(exactly = 1) {
            manager.subtract(userId, 100L)
        }
    }

    @Test
    fun `negative balance values are rejected`() {
        val result = service.set(userId, -100L).join()

        assertFalse(result)

        verify(exactly = 0) {
            manager.set(any(), any())
        }
    }
}