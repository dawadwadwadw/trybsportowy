package com.trybsportowy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

// Abstract class (not interface) so the @Transaction replaceAll body is the
// universally-supported form across Room/Kotlin default-method handling.
@Dao
abstract class ComputedScoreCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(row: ComputedScoreCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(rows: List<ComputedScoreCacheEntity>)

    @Query("DELETE FROM readiness_computed_cache")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(rows: List<ComputedScoreCacheEntity>) {
        clear()
        upsertAll(rows)
    }

    @Query(
        "SELECT * FROM readiness_computed_cache " +
            "WHERE dateTimestamp BETWEEN :fromTs AND :toTs ORDER BY dateTimestamp DESC"
    )
    abstract suspend fun getByDateRange(fromTs: Long, toTs: Long): List<ComputedScoreCacheEntity>

    @Query("SELECT * FROM readiness_computed_cache WHERE dateTimestamp = :ts LIMIT 1")
    abstract suspend fun getByDate(ts: Long): ComputedScoreCacheEntity?

    @Query("SELECT * FROM readiness_computed_cache")
    abstract suspend fun getAll(): List<ComputedScoreCacheEntity>

    @Query("SELECT * FROM readiness_computed_cache ORDER BY dateTimestamp DESC LIMIT 1")
    abstract suspend fun getMostRecent(): ComputedScoreCacheEntity?
}
