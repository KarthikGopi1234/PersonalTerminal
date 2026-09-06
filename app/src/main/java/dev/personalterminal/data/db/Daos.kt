package dev.personalterminal.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class HabitWithLogs(
    @Embedded val habit: Habit,
    @Relation(parentColumn = "id", entityColumn = "habitId") val logs: List<HabitLog>,
    @Relation(parentColumn = "id", entityColumn = "habitId") val shields: List<ShieldUse>,
)

data class WearLogWithWatch(
    @Embedded val log: WearLog,
    @Relation(parentColumn = "watchId", entityColumn = "id") val watch: Watch,
)

data class DayCount(val day: Long, val count: Int)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    fun observeActive(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY position, id")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY position, id")
    suspend fun getAll(): List<Habit>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    suspend fun getActive(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<Habit?>

    @Transaction
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    fun observeActiveWithLogs(): Flow<List<HabitWithLogs>>

    @Transaction
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    suspend fun getActiveWithLogs(): List<HabitWithLogs>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeWithLogs(id: Long): Flow<HabitWithLogs?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<Habit>)

    @Update
    suspend fun update(habit: Habit)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM habits")
    suspend fun nextPosition(): Int
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE day = :day")
    fun observeForDay(day: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE day = :day")
    suspend fun getForDay(day: Long): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun get(habitId: Long, day: Long): HabitLog?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY day DESC")
    suspend fun getForHabit(habitId: Long): List<HabitLog>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAll(): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE day BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<HabitLog>>

    @Query("SELECT day, COUNT(*) AS count FROM habit_logs WHERE completed = 1 AND day BETWEEN :from AND :to GROUP BY day")
    fun observeCompletionCounts(from: Long, to: Long): Flow<List<DayCount>>

    @Upsert
    suspend fun upsert(log: HabitLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<HabitLog>)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun delete(habitId: Long, day: Long)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAll()
}

@Dao
interface ShieldDao {
    @Query("SELECT * FROM shield_uses")
    suspend fun getAll(): List<ShieldUse>

    @Query("SELECT * FROM shield_uses WHERE habitId = :habitId")
    suspend fun getForHabit(habitId: Long): List<ShieldUse>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(use: ShieldUse): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(uses: List<ShieldUse>)

    @Query("DELETE FROM shield_uses")
    suspend fun deleteAll()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY position, id")
    fun observeAll(): Flow<List<Routine>>

    @Query("SELECT * FROM routines ORDER BY position, id")
    suspend fun getAll(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: Long): Routine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: Routine): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<Routine>)

    @Update
    suspend fun update(routine: Routine)

    @Delete
    suspend fun delete(routine: Routine)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM watches WHERE archived = 0 ORDER BY brand, model")
    fun observeActive(): Flow<List<Watch>>

    @Query("SELECT * FROM watches ORDER BY brand, model")
    fun observeAll(): Flow<List<Watch>>

    @Query("SELECT * FROM watches")
    suspend fun getAll(): List<Watch>

    @Query("SELECT * FROM watches WHERE id = :id")
    suspend fun getById(id: Long): Watch?

    @Query("SELECT * FROM watches WHERE id = :id")
    fun observeById(id: Long): Flow<Watch?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(watch: Watch): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(watches: List<Watch>)

    @Update
    suspend fun update(watch: Watch)

    @Delete
    suspend fun delete(watch: Watch)

    @Query("DELETE FROM watches")
    suspend fun deleteAll()

    @Query("SELECT watchId, COUNT(*) AS count FROM wear_logs GROUP BY watchId")
    fun observeWearCounts(): Flow<List<WatchCount>>
}

data class WatchCount(val watchId: Long, val count: Int)

@Dao
interface WearLogDao {
    @Transaction
    @Query("SELECT * FROM wear_logs WHERE day = :day ORDER BY createdAt DESC")
    fun observeForDay(day: Long): Flow<List<WearLogWithWatch>>

    @Transaction
    @Query("SELECT * FROM wear_logs WHERE day BETWEEN :from AND :to ORDER BY day DESC, createdAt DESC")
    fun observeRange(from: Long, to: Long): Flow<List<WearLogWithWatch>>

    @Transaction
    @Query("SELECT * FROM wear_logs ORDER BY day DESC, createdAt DESC")
    fun observeAllWithWatch(): Flow<List<WearLogWithWatch>>

    @Transaction
    @Query("SELECT * FROM wear_logs WHERE watchId = :watchId ORDER BY day DESC")
    fun observeForWatch(watchId: Long): Flow<List<WearLogWithWatch>>

    @Query("SELECT * FROM wear_logs")
    suspend fun getAll(): List<WearLog>

    @Query("SELECT * FROM wear_logs WHERE id = :id")
    suspend fun getById(id: Long): WearLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WearLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<WearLog>)

    @Update
    suspend fun update(log: WearLog)

    @Delete
    suspend fun delete(log: WearLog)

    @Query("DELETE FROM wear_logs")
    suspend fun deleteAll()
}

@Dao
interface XpDao {
    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    suspend fun total(): Int

    @Query("SELECT * FROM xp_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<XpEvent>>

    @Query("SELECT * FROM xp_events")
    suspend fun getAll(): List<XpEvent>

    @Query("SELECT COUNT(*) FROM xp_events WHERE habitId = :habitId AND day = :day AND reason = :reason")
    suspend fun countFor(habitId: Long, day: Long, reason: String): Int

    @Insert
    suspend fun insert(event: XpEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<XpEvent>)

    @Query("DELETE FROM xp_events WHERE habitId = :habitId AND day = :day AND reason = :reason")
    suspend fun deleteFor(habitId: Long, day: Long, reason: String)

    @Query("DELETE FROM xp_events")
    suspend fun deleteAll()
}
