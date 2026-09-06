package dev.personalterminal.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** How a habit is tracked. */
enum class HabitType { CHECKBOX, COUNTER, TIMER }

/** Which days a habit is scheduled for. */
enum class ScheduleType { DAILY, WEEKLY, SPECIFIC_DAYS }

/**
 * A group of habits, e.g. "Morning Routine". Habits with `routineId == null` live in the
 * implicit "unsorted" bucket.
 */
@Serializable
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = ">",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "habits",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId")],
)
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: HabitType = HabitType.CHECKBOX,
    /** Counter: target count. Timer: target minutes. Checkbox: 1. */
    val target: Int = 1,
    val unit: String = "",
    val schedule: ScheduleType = ScheduleType.DAILY,
    /** Bitmask Mon=1, Tue=2, Wed=4 … Sun=64 (used for SPECIFIC_DAYS). */
    val daysMask: Int = 127,
    /** For WEEKLY schedule: how many completions per week are required. */
    val timesPerWeek: Int = 3,
    val routineId: Long? = null,
    val color: String = "green",
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = "",
)

/** One row per habit per day. `value` is count for counters, minutes for timers, 1/0 for checkbox. */
@Serializable
@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("day")],
)
data class HabitLog(
    val habitId: Long,
    /** Epoch day (LocalDate.toEpochDay()). */
    val day: Long,
    val value: Int = 0,
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A streak shield that was consumed to bridge a missed day. */
@Serializable
@Entity(
    tableName = "shield_uses",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ShieldUse(
    val habitId: Long,
    val day: Long,
    val usedAt: Long = System.currentTimeMillis(),
)

/** A watch the user owns. */
@Serializable
@Entity(tableName = "watches")
data class Watch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val model: String,
    val nickname: String = "",
    val reference: String = "",
    val movement: String = "",
    val caseSizeMm: Float? = null,
    val color: String = "cyan",
    /** Relative path (inside filesDir/watch_photos) of the profile photo. */
    val photoPath: String? = null,
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Which watch was worn on which day (plus optional wrist-shot). */
@Serializable
@Entity(
    tableName = "wear_logs",
    foreignKeys = [
        ForeignKey(
            entity = Watch::class,
            parentColumns = ["id"],
            childColumns = ["watchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("day"), Index("watchId")],
)
data class WearLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val watchId: Long,
    val day: Long,
    val photoPath: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Persisted XP ledger — one row per reward so XP can be recomputed / audited. */
@Serializable
@Entity(tableName = "xp_events", indices = [Index("day")])
data class XpEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val amount: Int,
    val reason: String,
    @ColumnInfo(defaultValue = "0") val habitId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
