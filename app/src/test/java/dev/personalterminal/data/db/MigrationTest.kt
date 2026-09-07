package dev.personalterminal.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Schema guard: the hand-written migrations must produce exactly the schema Room generated for the
 * current version (app/schemas/…/N.json), and existing rows must survive with defaults.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun `1 to 3 keeps data and matches the exported schema`() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO routines (id, name, icon, position, createdAt) VALUES (1, 'morning', '☼', 0, 1)")
            execSQL("INSERT INTO habits (id, name, type, target, unit, schedule, daysMask, timesPerWeek, routineId, color, position, archived, createdAt, notes) VALUES (1, 'stretch', 'CHECKBOX', 1, '', 'DAILY', 127, 3, 1, 'green', 0, 0, 1, '')")
            execSQL("INSERT INTO habit_logs (habitId, day, value, completed, updatedAt) VALUES (1, 20000, 1, 1, 1)")
            execSQL("INSERT INTO watches (id, brand, model, nickname, reference, movement, caseSizeMm, color, photoPath, notes, archived, createdAt) VALUES (1, 'Seiko', 'SPB143', '', '', '6R35', 40.5, 'cyan', NULL, '', 0, 1)")
            execSQL("INSERT INTO wear_logs (id, watchId, day, photoPath, note, createdAt) VALUES (1, 1, 20000, NULL, '', 1)")
            close()
        }
        // validateDroppedTables = true → any difference to 3.json fails the test
        val db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        db.query("SELECT negative, reminderMinutes, healthMetric, checkIn FROM habits WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals(-1, c.getInt(1)); assertEquals("", c.getString(2)); assertEquals(0, c.getInt(3))
        }
        db.query("SELECT skipped, note, mood FROM habit_logs").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals("", c.getString(1)); assertEquals(0, c.getInt(2)) }
        db.query("SELECT serviceIntervalMonths, currency, purchasePrice FROM watches").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals("", c.getString(1)); assertTrue(c.isNull(2)) }
        db.query("SELECT strapId FROM wear_logs").use { c -> assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)) }
        db.close()

        // And Room itself opens the migrated file happily with the real DAOs.
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1, room.habitDao().getAll().size)
            room.habitDao().update(room.habitDao().getById(1)!!.copy(checkIn = true))
            assertTrue(room.habitDao().getById(1)!!.checkIn)
            room.focusSessionDao().insert(FocusSession(habitId = 1, day = 20001, startedAt = 0, endedAt = 0, minutes = 25))
            assertEquals(25, room.focusSessionDao().getAll().single().minutes)
        }
        room.close()
    }

    @Test
    fun `2 to 3 adds the check-in flag with default off`() {
        val name = "migration-2-3.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO habits (id, name, type, target, unit, schedule, daysMask, timesPerWeek, routineId, color, position, archived, createdAt, notes, negative, reminderMinutes, focusMinutes, breakMinutes, healthMetric) VALUES (7, 'read', 'COUNTER', 20, 'pages', 'DAILY', 127, 3, NULL, 'yellow', 0, 0, 1, '', 0, 1260, 0, 0, '')")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 3, true, AppDatabase.MIGRATION_2_3)
        db.query("SELECT checkIn, reminderMinutes FROM habits WHERE id = 7").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals(1260, c.getInt(1)) }
        db.close()
    }
}
