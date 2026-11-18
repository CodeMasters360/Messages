package org.fossify.messages.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The "Blocked messages" folder: SMS that were stopped by a filter rule are kept here instead of
 * the telephony inbox, so they never show up as conversations or notifications.
 */
class BlockedMessagesDb private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    data class Item(
        val id: Long,
        val address: String,
        val subject: String,
        val body: String,
        val date: Long,
        val rule: String,
        val subscriptionId: Int
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (_id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL, " +
                "subject TEXT, body TEXT, date INTEGER, rule TEXT, subscription_id INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    @Synchronized
    fun insert(
        address: String,
        subject: String,
        body: String,
        date: Long,
        rule: String,
        subscriptionId: Int
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("address", address)
            put("subject", subject)
            put("body", body)
            put("date", date)
            put("rule", rule)
            put("subscription_id", subscriptionId)
        }
        db.insert(TABLE, null, values)
        db.execSQL(
            "DELETE FROM $TABLE WHERE _id NOT IN " +
                "(SELECT _id FROM $TABLE ORDER BY _id DESC LIMIT $MAX_ROWS)"
        )
    }

    @Synchronized
    fun list(): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.query(
            TABLE,
            arrayOf("_id", "address", "subject", "body", "date", "rule", "subscription_id"),
            null, null, null, null, "_id DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Item(
                        id = c.getLong(0),
                        address = c.getString(1).orEmpty(),
                        subject = c.getString(2).orEmpty(),
                        body = c.getString(3).orEmpty(),
                        date = c.getLong(4),
                        rule = c.getString(5).orEmpty(),
                        subscriptionId = c.getInt(6)
                    )
                )
            }
        }
        return out
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "_id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }

    companion object {
        private const val DB_NAME = "blocked_messages.db"
        private const val DB_VERSION = 1
        private const val TABLE = "blocked"
        private const val MAX_ROWS = 1000

        @Volatile
        private var instance: BlockedMessagesDb? = null

        fun get(context: Context): BlockedMessagesDb {
            return instance ?: synchronized(this) {
                instance ?: BlockedMessagesDb(context.applicationContext).also { instance = it }
            }
        }
    }
}
