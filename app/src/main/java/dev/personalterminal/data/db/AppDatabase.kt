package dev.personalterminal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun habitTypeToString(t: HabitType): String = t.name
    @TypeConverter fun stringToHabitType(s: String): HabitType = runCatching { HabitType.valueOf(s) }.getOrDefault(HabitType.CHECKBOX)
    @TypeConverter fun scheduleToString(t: ScheduleType): String = t.name
    @TypeConverter fun stringToSchedule(s: String): ScheduleType = runCatching { ScheduleType.valueOf(s) }.getOrDefault(ScheduleType.DAILY)
}

@Database(
    entities = [
        Routine::class, Habit::class, HabitLog::class, ShieldUse::class, Watch::class, WearLog::class, XpEvent::class,
        FocusSession::class, WatchService::class, AccuracyReading::class, Strap::class,
    ],
    version = 3,
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
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun watchServiceDao(): WatchServiceDao
    abstract fun accuracyDao(): AccuracyDao
    abstract fun strapDao(): StrapDao

    /** Wipes every table inside one transaction (used when restoring a backup). */
    suspend fun clearAllData() = withTransaction {
        focusSessionDao().deleteAll()
        watchServiceDao().deleteAll()
        accuracyDao().deleteAll()
        strapDao().deleteAll()
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
        }

        /** 0.3 → 0.3.1: per-habit evening check-in flag. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN checkIn INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 0.2 → 0.3: habit loop + timer sessions + watch tracker tables. Additive only – no data is touched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN negative INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderMinutes INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE habits ADD COLUMN focusMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN breakMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN healthMetric TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN skipReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN mood INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watches ADD COLUMN purchasePrice REAL")
                db.execSQL("ALTER TABLE watches ADD COLUMN purchaseDay INTEGER")
                db.execSQL("ALTER TABLE watches ADD COLUMN currentValue REAL")
                db.execSQL("ALTER TABLE watches ADD COLUMN currency TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watches ADD COLUMN lugWidthMm INTEGER")
                db.execSQL("ALTER TABLE watches ADD COLUMN serviceIntervalMonths INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wear_logs ADD COLUMN strapId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, habitId INTEGER NOT NULL, " +
                        "day INTEGER NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, minutes INTEGER NOT NULL, " +
                        "kind TEXT NOT NULL, completed INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_habitId ON focus_sessions (habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_day ON focus_sessions (day)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watch_services (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, watchId INTEGER NOT NULL, " +
                        "day INTEGER NOT NULL, kind TEXT NOT NULL, cost REAL, notes TEXT NOT NULL, nextDueDay INTEGER, createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(watchId) REFERENCES watches(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_services_watchId ON watch_services (watchId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watch_accuracy (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, watchId INTEGER NOT NULL, " +
                        "measuredAt INTEGER NOT NULL, offsetSeconds REAL NOT NULL, note TEXT NOT NULL, " +
                        "FOREIGN KEY(watchId) REFERENCES watches(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_accuracy_watchId ON watch_accuracy (watchId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS straps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, material TEXT NOT NULL, " +
                        "color TEXT NOT NULL, widthMm INTEGER, watchId INTEGER, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_straps_watchId ON straps (watchId)")
            }
        }
    }
}
