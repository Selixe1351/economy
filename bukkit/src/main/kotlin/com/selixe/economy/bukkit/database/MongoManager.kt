package com.selixe.economy.bukkit.database

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.UuidRepresentation

class MongoManager(private val databaseName: String = "economy") {

    private lateinit var client: MongoClient

    fun createConnection(uri: String) {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .build()

        client = MongoClients.create(settings)
    }

    fun getCollection(name: String): MongoCollection<Document> {
        check(::client.isInitialized) {
            "MongoDB connection has not been initialized"
        }

        return client.getDatabase(databaseName)
            .getCollection(name)
    }

    fun closeConnection() {
        if (::client.isInitialized) {
            client.close()
        }
    }

}