package dev.personalterminal.data

import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.importers.HabiticaImporter
import dev.personalterminal.data.importers.LoopImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportersTest {
    @Test fun `loop csv export with per-habit folders`() {
        val files = mapOf(
            "Habits.csv" to "Position,Name,Question,Description,NumRepetitions,Interval,Color\n001,Meditate,Did you?,calm,1,1,#FF0000\n002,Gym,,,3,7,#00FF00\n",
            "001 Meditate/Checkmarks.csv" to "Date,Value\n2026-09-01,2\n2026-09-02,3\n2026-09-03,0\n",
            "002 Gym/Checkmarks.csv" to "Date,Value\n2026-09-01,2\n",
        )
        val b = LoopImporter.parseCsvFiles(files)
        assertEquals(2, b.habits.size)
        val med = b.habits.first { it.name == "Meditate" }
        assertEquals(2, b.logs.count { it.habitId == med.id })
        assertTrue(b.logs.any { it.habitId == med.id && it.skipped })
        val gym = b.habits.first { it.name == "Gym" }
        assertEquals(dev.personalterminal.data.db.ScheduleType.WEEKLY, gym.schedule); assertEquals(3, gym.timesPerWeek)
    }

    @Test fun `loop numeric habit becomes a counter`() {
        val files = mapOf("Habits.csv" to "Position,Name,Question,Description,NumRepetitions,Interval,Color\n001,Pages,,,1,1,#000\n", "001 Pages/Checkmarks.csv" to "Date,Value\n2026-09-01,20000\n2026-09-02,30000\n")
        val b = LoopImporter.parseCsvFiles(files)
        assertEquals(HabitType.COUNTER, b.habits.single().type)
        assertEquals(30, b.logs.maxOf { it.value })
    }

    @Test fun `habitica dailies and habits`() {
        val json = """{"data":{"tasks":[
            {"type":"daily","text":"Stretch","repeat":{"m":true,"t":true,"w":true,"th":true,"f":true,"s":false,"su":false},"history":[{"date":1756684800000,"completed":true}]},
            {"type":"habit","text":"No sugar","up":false,"down":true,"history":[]},
            {"type":"todo","text":"Taxes"}
        ]}}"""
        val b = HabiticaImporter.parse(json)
        assertEquals(2, b.habits.size)
        val stretch = b.habits.first { it.name == "Stretch" }
        assertEquals(31, stretch.daysMask)
        assertTrue(b.habits.first { it.name == "No sugar" }.negative)
        assertEquals(1, b.warnings.size)
        assertEquals(1, b.logs.size)
    }

    @Test fun `csv parser handles quotes`() {
        val rows = LoopImporter.csv("a,\"b,c\",\"d\"\"e\"\n1,2,3\n")
        assertEquals(listOf("a", "b,c", "d\"e"), rows[0])
        assertEquals(2, rows.size)
    }
}
