package com.spdcon.app.config

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Local configuration library. Credentials are encrypted with Android Keystore AES-GCM.
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
        if (oldVersion < 2) {
            db.beginTransaction()
            try {
                db.rawQuery("SELECT id, credential FROM configs", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val stored = cursor.getString(1)
                        if (!CredentialCipher.isEncrypted(stored)) {
                            val values = ContentValues().apply {
                                put("credential", CredentialCipher.encrypt(stored))
                            }
                            db.update("configs", values, "id=?", arrayOf(id.toString()))
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun save(profile: ConfigProfile): Result<Unit> = runCatching {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val values = ContentValues().apply {
            put("name", profile.name)
            put("protocol", profile.protocol.name)
            put("server", profile.server)
            put("port", profile.port)
            put("credential", CredentialCipher.encrypt(profile.credential))
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
                        credential = CredentialCipher.decrypt(cursor.getString(4)),
                        network = cursor.getString(5),
                        security = cursor.getString(6),
                        remark = if (cursor.isNull(7)) null else cursor.getString(7),
                        parameters = parameters
                    )
                }.getOrNull()?.let { add(it) }
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
        private const val DATABASE_VERSION = 2
    }
}
