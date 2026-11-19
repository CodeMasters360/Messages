package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.SpamMessage

@Dao
interface SpamMessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: SpamMessage): Long

    @Query("SELECT * FROM spam_messages ORDER BY date DESC")
    fun getAll(): List<SpamMessage>

    @Query("SELECT COUNT(*) FROM spam_messages")
    fun getCount(): Int

    @Query("DELETE FROM spam_messages WHERE id IN (:ids)")
    fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM spam_messages WHERE date < :timestamp")
    fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM spam_messages")
    fun deleteAll()
}
