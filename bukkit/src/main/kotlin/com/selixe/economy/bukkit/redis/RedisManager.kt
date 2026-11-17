package com.selixe.economy.bukkit.redis

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import java.net.URI

class RedisManager(
    uri: String,
    val channel: String
) {

    private var subscriberThread: Thread? = null

    @Volatile
    private var pubSub: JedisPubSub? = null

    private val pool = JedisPool(URI(uri))

    fun publish(message: String): Long {
        return pool.resource.use { jedis ->
            jedis.publish(channel, message)
        }
    }

    fun subscribe(onMessage: (String) -> Unit) {
        subscriberThread = Thread({
            pool.resource.use { jedis ->
                val listener = object : JedisPubSub() {
                    override fun onMessage(channel: String, message: String) {
                        onMessage(message)
                    }
                }

                pubSub = listener
                jedis.subscribe(listener, channel)
            }
        }, "Economy-Synchronization").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        pool.close()
    }

    fun ping(): Boolean {
        return pool.resource.use { jedis ->
            jedis.ping() == "PONG"
        }
    }

}