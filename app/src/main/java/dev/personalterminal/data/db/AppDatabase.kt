package dev.personalterminal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.withTransaction

class Converters {
    @TypeConverter fun habitTypeToString(t: HabitType): String = t.name
    @TypeConverter fun stringToHabitType(s: String): HabitType = runCatching { HabitType.valueOf(s) }.getOrDefault(HabitType.CHECKBOX)
    @TypeConverter fun scheduleToString(t: ScheduleType): String = t.name
    @TypeConverter fun stringToSchedule(s: String): ScheduleType = runCatching { ScheduleType.valueOf(s) }.getOrDefault(ScheduleType.DAILY)
}

@Database(
    entities = [Routine::class, Habit::class, HabitLog::class, ShieldUse::class, Watch::class, WearLog::class, XpEvent::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun shieldDao(): ShieldDao
    abstract fun routineDao(): RoutineDao
    abstract fun watchDao(): WatchDao
    abstract fun wearLogDao(): WearLogDao
    abstract fun xpDao(): XpDao

    /** Wipes every table inside one transaction (used when restoring a backup). */
    suspend fun clearAllData() = withTransaction {
        wearLogDao().deleteAll()
        watchDao().deleteAll()
        shieldDao().deleteAll()
        habitLogDao().deleteAll()
        habitDao().deleteAll()
        routineDao().deleteAll()
        xpDao().deleteAll()
    }

    companion object {
        const val NAME = "personal_terminal.db"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
        }
    }
}
