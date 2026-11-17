
package com.selixe.economy.bukkit

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.selixe.economy.bukkit.database.MongoManager
import com.selixe.economy.bukkit.redis.RedisManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.TimeUnit

class EconomyManagerTest {

    private lateinit var mongo: MongoManager
    private lateinit var redis: RedisManager
    private lateinit var collection: MongoCollection<Document>
    private lateinit var manager: EconomyManager

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        mongo = mockk()
        redis = mockk()
        collection = mockk()

        every { mongo.getCollection("balances") } returns collection
        every { redis.publish(any()) } returns 1L

        manager = EconomyManager(mongo, redis)
    }

    @AfterEach
    fun cleanup() {
        manager.shutdown()
    }

    @Test
    fun `add publishes balance update`() {
        every {
            collection.findOneAndUpdate(
                any<Bson>(),
                any<Bson>(),
                any<FindOneAndUpdateOptions>()
            )
        } returns Document("balance", 1500L)

        val result = manager.add(userId, 500L)
            .get(5, TimeUnit.SECONDS)

        assertTrue(result)
        verify(exactly = 1) {
            redis.publish(userId.toString())
        }
    }

    @Test
    fun `subtract publishes when successful`() {
        every {
            collection.findOneAndUpdate(
                any<Bson>(),
                any<Bson>(),
                any<FindOneAndUpdateOptions>()
            )
        } returns Document("balance", 200L)

        val result = manager.subtract(userId, 800L)
            .get(5, TimeUnit.SECONDS)

        assertTrue(result)
        verify(exactly = 1) {
            redis.publish(userId.toString())
        }
    }

    @Test
    fun `subtract does not publish when insufficient funds`() {
        every {
            collection.findOneAndUpdate(
                any<Bson>(),
                any<Bson>(),
                any<FindOneAndUpdateOptions>()
            )
        } returns null

        val result = manager.subtract(userId, 800L)
            .get(5, TimeUnit.SECONDS)

        assertFalse(result)
        verify(exactly = 0) {
            redis.publish(any())
        }
    }

    @Test
    fun `set publishes balance update`() {
        every {
            collection.findOneAndUpdate(
                any<Bson>(),
                any<Bson>(),
                any<FindOneAndUpdateOptions>()
            )
        } returns Document("balance", 5000L)

        val result = manager.set(userId, 5000L)
            .get(5, TimeUnit.SECONDS)

        assertTrue(result)
        verify(exactly = 1) {
            redis.publish(userId.toString())
        }
    }

    @Test
    fun `database failure does not publish`() {
        every {
            collection.findOneAndUpdate(
                any<Bson>(),
                any<Bson>(),
                any<FindOneAndUpdateOptions>()
            )
        } throws RuntimeException("MongoDB unavailable")

        assertThrows(Exception::class.java) {
            manager.add(userId, 500L)
                .get(5, TimeUnit.SECONDS)
        }

        verify(exactly = 0) {
            redis.publish(any())
        }
    }

    @Test
    fun `operations work without Redis`() {
        val managerWithoutRedis = EconomyManager(mongo, null)

        try {
            every {
                collection.findOneAndUpdate(
                    any<Bson>(),
                    any<Bson>(),
                    any<FindOneAndUpdateOptions>()
                )
            } returns Document("balance", 1500L)

            val result = managerWithoutRedis.add(userId, 500L)
                .get(5, TimeUnit.SECONDS)

            assertTrue(result)
        } finally {
            managerWithoutRedis.shutdown()
        }
    }
}
