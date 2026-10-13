package com.spdcon.app.config

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Small local-only configuration library. Credentials stay on-device; this is not encrypted
 * storage, so the app must not describe it as a secure vault.
 */
class ConfigLibraryStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE configs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                protocol TEXT NOT NULL,
                server TEXT NOT NULL,
                port INTEGER NOT NULL,
                credential TEXT NOT NULL,
                network TEXT NOT NULL,
                security TEXT NOT NULL,
                remark TEXT,
                parameters TEXT NOT NULL,
                UNIQUE(protocol, server, port, name)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No destructive migration is needed for version 1. Future schema changes must migrate.
    }

    fun save(profile: ConfigProfile): Result<Unit> = runCatching {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val values = ContentValues().apply {
            put("name", profile.name)
            put("protocol", profile.protocol.name)
            put("server", profile.server)
            put("port", profile.port)
            put("credential", profile.credential)
            put("network", profile.network)
            put("security", profile.security)
            put("remark", profile.remark)
            put("parameters", JSONObject(profile.parameters).toString())
        }
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT id FROM configs WHERE protocol=? AND server=? AND port=? AND name=?",
            arrayOf(profile.protocol.name, profile.server, profile.port.toString(), profile.name)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        if (existing == null) {
            db.insertOrThrow("configs", null, values)
        } else {
            db.update("configs", values, "id=?", arrayOf(existing.toString()))
        }
    }

    fun loadAll(): List<ConfigProfile> = readableDatabase.rawQuery(
        "SELECT name, protocol, server, port, credential, network, security, remark, parameters FROM configs ORDER BY name COLLATE NOCASE",
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                runCatching {
                    val parametersJson = JSONObject(cursor.getString(8))
                    val parameters = buildMap {
                        val keys = parametersJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, parametersJson.optString(key))
                        }
                    }
                    ConfigProfile(
                        name = cursor.getString(0),
                        protocol = ConfigProtocol.valueOf(cursor.getString(1)),
                        server = cursor.getString(2),
                        port = cursor.getInt(3),
                        credential = cursor.getString(4),
                        network = cursor.getString(5),
                        security = cursor.getString(6),
                        remark = if (cursor.isNull(7)) null else cursor.getString(7),
                        parameters = parameters
                    )
                }.getOrNull()?.let(::add)
            }
        }
    }

    fun delete(profile: ConfigProfile): Int = writableDatabase.delete(
        "configs",
        "protocol=? AND server=? AND port=? AND name=?",
        arrayOf(profile.protocol.name, profile.server, profile.port.toString(), profile.name)
    )

    companion object {
        private const val DATABASE_NAME = "spdcon-configs.db"
        private const val DATABASE_VERSION = 1
    }
}
