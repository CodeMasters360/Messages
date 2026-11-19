package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message that was classified as spam on arrival (blocked number, blocked keyword
 * or unknown sender) and was therefore NOT written to the telephony provider.
 * It lives only in the app database until the user restores or deletes it.
 */
@Entity(
    tableName = "spam_messages",
    indices = [Index(value = ["date"]), Index(value = ["address"])]
)
data class SpamMessage(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "subject") val subject: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Int,
    @ColumnInfo(name = "reason") val reason: Int,
    @ColumnInfo(name = "is_mms") val isMms: Boolean = false
)
