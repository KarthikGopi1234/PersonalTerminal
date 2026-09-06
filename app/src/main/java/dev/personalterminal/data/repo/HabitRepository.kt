package dev.personalterminal.data.repo

import dev.personalterminal.data.db.AppDatabase
import dev.personalterminal.data.db.DayCount
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.data.db.Routine
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.ShieldUse
import dev.personalterminal.data.db.XpEvent
import dev.personalterminal.domain.DaySummary
import dev.personalterminal.domain.HabitStatus
import dev.personalterminal.domain.Progression
import dev.personalterminal.domain.RoutineGroup
import dev.personalterminal.domain.Schedule
import dev.personalterminal.domain.StreakInfo
import dev.personalterminal.domain.Streaks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Single source of truth for habits, logs, streaks, shields and XP.
 * All mutations go through here so the widget, the UI and the backup module see consistent data.
 */
class HabitRepository(private val db: AppDatabase) {

    private val habitDao = db.habitDao()
    private val logDao = db.habitLogDao()
    private val shieldDao = db.shieldDao()
    private val routineDao = db.routineDao()
    private val xpDao = db.xpDao()

    /** Emits whenever a mutation happened – lets the widget and other non-Flow consumers refresh. */
    val mutations = MutableStateFlow(0L)

    // ------------------------------------------------------------------ observation

    fun observeRoutines(): Flow<List<Routine>> = routineDao.observeAll()
    fun observeHabits(): Flow<List<Habit>> = habitDao.observeAll()
    fun observeHabit(id: Long): Flow<Habit?> = habitDao.observeById(id)
    fun observeHabitWithLogs(id: Long): Flow<HabitWithLogs?> = habitDao.observeWithLogs(id)
    fun observeTotalXp(): Flow<Int> = xpDao.observeTotal()
    fun observeRecentXp(limit: Int = 20): Flow<List<XpEvent>> = xpDao.observeRecent(limit)
    fun observeCompletionCounts(from: LocalDate, to: LocalDate): Flow<List<DayCount>> =
        logDao.observeCompletionCounts(from.toEpochDay(), to.toEpochDay())
    fun observeLogsRange(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        logDao.observeRange(from.toEpochDay(), to.toEpochDay())

    /** Full day summary (grouped by routine) for [date]. Recomputes whenever anything changes. */
    fun observeDay(date: LocalDate): Flow<DaySummary> =
        combine(habitDao.observeActiveWithLogs(), routineDao.observeAll(), xpDao.observeTotal()) { habits, routines, xp ->
            buildSummary(date, habits, routines, xp)
        }

    suspend fun daySummary(date: LocalDate): DaySummary =
        buildSummary(date, habitDao.getActiveWithLogs(), routineDao.getAll(), xpDao.total())

    private fun buildSummary(date: LocalDate, habits: List<HabitWithLogs>, routines: List<Routine>, xp: Int): DaySummary {
        val epoch = date.toEpochDay()
        val statuses = habits.map { hwl -> toStatus(hwl, date, epoch) }
        val byRoutine = statuses.groupBy { it.habit.routineId }
        val groups = buildList {
            routines.forEach { r -> byRoutine[r.id]?.let { add(RoutineGroup(r, it)) } }
            byRoutine[null]?.let { add(RoutineGroup(null, it)) }
            // habits pointing to a deleted routine (shouldn't happen thanks to SET_NULL, but be safe)
            byRoutine.keys.filterNotNull().filter { id -> routines.none { it.id == id } }
                .forEach { add(RoutineGroup(null, byRoutine[it]!!)) }
        }
        val totalCompletions = habits.sumOf { h -> h.logs.count { it.completed } }
        val shieldsUsed = habits.sumOf { it.shields.size }
        return DaySummary(
            date = date,
            groups = groups,
            shieldsAvailable = Progression.shieldsAvailable(totalCompletions, shieldsUsed),
            totalXp = xp,
        )
    }

    private fun toStatus(hwl: HabitWithLogs, date: LocalDate, epoch: Long): HabitStatus {
        val log = hwl.logs.firstOrNull { it.day == epoch }
        val streak = Streaks.compute(hwl.habit, hwl.logs, hwl.shields, date)
        val weekStart = Schedule.weekStart(date).toEpochDay()
        val weekCount = if (hwl.habit.schedule == ScheduleType.WEEKLY)
            hwl.logs.count { it.completed && it.day in weekStart..(weekStart + 6) } else 0
        val due = when (hwl.habit.schedule) {
            ScheduleType.WEEKLY -> weekCount < hwl.habit.timesPerWeek || log?.completed == true
            else -> Schedule.isDue(hwl.habit, date)
        }
        return HabitStatus(hwl.habit, log, streak, due, weekCount)
    }

    suspend fun streakFor(habitId: Long, date: LocalDate = LocalDate.now()): StreakInfo? {
        val habit = habitDao.getById(habitId) ?: return null
        return Streaks.compute(habit, logDao.getForHabit(habitId), shieldDao.getForHabit(habitId), date)
    }

    // ------------------------------------------------------------------ routines

    suspend fun saveRoutine(routine: Routine): Long {
        val id = if (routine.id == 0L) routineDao.insert(routine.copy(position = routineDao.getAll().size))
        else { routineDao.update(routine); routine.id }
        bump(); return id
    }

    suspend fun deleteRoutine(routine: Routine) { routineDao.delete(routine); bump() }

    suspend fun moveRoutine(routine: Routine, delta: Int) {
        val all = routineDao.getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == routine.id }
        val target = (idx + delta).coerceIn(0, all.lastIndex)
        if (idx < 0 || idx == target) return
        val item = all.removeAt(idx); all.add(target, item)
        all.forEachIndexed { i, r -> if (r.position != i) routineDao.update(r.copy(position = i)) }
        bump()
    }

    // ------------------------------------------------------------------ habits

    suspend fun saveHabit(habit: Habit): Long {
        val id = if (habit.id == 0L) habitDao.insert(habit.copy(position = habitDao.nextPosition()))
        else { habitDao.update(habit); habit.id }
        bump(); return id
    }

    suspend fun deleteHabit(habit: Habit) { habitDao.delete(habit); bump() }

    suspend fun setArchived(habit: Habit, archived: Boolean) { habitDao.update(habit.copy(archived = archived)); bump() }

    suspend fun moveHabit(habit: Habit, delta: Int) {
        val siblings = habitDao.getAll().filter { it.routineId == habit.routineId }.toMutableList()
        val idx = siblings.indexOfFirst { it.id == habit.id }
        val target = (idx + delta).coerceIn(0, siblings.lastIndex)
        if (idx < 0 || idx == target) return
        val item = siblings.removeAt(idx); siblings.add(target, item)
        siblings.forEachIndexed { i, h -> if (h.position != i) habitDao.update(h.copy(position = i)) }
        bump()
    }

    // ------------------------------------------------------------------ logging

    /** Toggle a checkbox habit (or fully complete/un-complete any habit). */
    suspend fun toggle(habitId: Long, date: LocalDate = LocalDate.now()) {
        val habit = habitDao.getById(habitId) ?: return
        val epoch = date.toEpochDay()
        val existing = logDao.get(habitId, epoch)
        val nowCompleted = !(existing?.completed ?: false)
        val value = if (nowCompleted) habit.target.coerceAtLeast(1) else 0
        logDao.upsert(HabitLog(habitId, epoch, value, nowCompleted))
        afterChange(habit, epoch, existing?.completed == true, nowCompleted, date)
    }

    /** Increment/decrement a counter or add minutes to a timer habit. */
    suspend fun addValue(habitId: Long, delta: Int, date: LocalDate = LocalDate.now()) {
        val habit = habitDao.getById(habitId) ?: return
        val epoch = date.toEpochDay()
        val existing = logDao.get(habitId, epoch)
        val newValue = ((existing?.value ?: 0) + delta).coerceAtLeast(0)
        val completed = newValue >= habit.target.coerceAtLeast(1)
        if (newValue == 0 && !completed) logDao.delete(habitId, epoch)
        else logDao.upsert(HabitLog(habitId, epoch, newValue, completed))
        afterChange(habit, epoch, existing?.completed == true, completed, date)
    }

    suspend fun setValue(habitId: Long, value: Int, date: LocalDate = LocalDate.now()) {
        val habit = habitDao.getById(habitId) ?: return
        val epoch = date.toEpochDay()
        val existing = logDao.get(habitId, epoch)
        val v = value.coerceAtLeast(0)
        val completed = if (habit.type == HabitType.CHECKBOX) v > 0 else v >= habit.target.coerceAtLeast(1)
        if (v == 0) logDao.delete(habitId, epoch) else logDao.upsert(HabitLog(habitId, epoch, v, completed))
        afterChange(habit, epoch, existing?.completed == true, completed, date)
    }

    private suspend fun afterChange(habit: Habit, epoch: Long, wasCompleted: Boolean, isCompleted: Boolean, date: LocalDate) {
        if (!wasCompleted && isCompleted) {
            awardOnce(habit.id, epoch, Progression.REASON_COMPLETE, Progression.XP_COMPLETE)
            val streak = Streaks.compute(habit, logDao.getForHabit(habit.id), shieldDao.getForHabit(habit.id), date)
            if (streak.current > 0 && streak.current % 7 == 0) {
                awardOnce(habit.id, epoch, Progression.REASON_STREAK, Progression.XP_STREAK_WEEK)
            }
        } else if (wasCompleted && !isCompleted) {
            xpDao.deleteFor(habit.id, epoch, Progression.REASON_COMPLETE)
            xpDao.deleteFor(habit.id, epoch, Progression.REASON_STREAK)
        }
        // Perfect-day bonus (habitId 0 = day-level event)
        val summary = daySummary(date)
        val hasPerfect = xpDao.countFor(0, epoch, Progression.REASON_PERFECT_DAY) > 0
        if (summary.isPerfect && !hasPerfect) {
            xpDao.insert(XpEvent(day = epoch, amount = Progression.XP_PERFECT_DAY, reason = Progression.REASON_PERFECT_DAY))
        } else if (!summary.isPerfect && hasPerfect) {
            xpDao.deleteFor(0, epoch, Progression.REASON_PERFECT_DAY)
        }
        bump()
    }

    private suspend fun awardOnce(habitId: Long, day: Long, reason: String, amount: Int) {
        if (xpDao.countFor(habitId, day, reason) == 0) {
            xpDao.insert(XpEvent(day = day, amount = amount, reason = reason, habitId = habitId))
        }
    }

    // ------------------------------------------------------------------ shields

    /** Spend a shield to bridge the gap on [day] for [habitId]. Returns false when no shield available. */
    suspend fun useShield(habitId: Long, day: LocalDate): Boolean {
        val summary = daySummary(LocalDate.now())
        if (summary.shieldsAvailable <= 0) return false
        val inserted = shieldDao.insert(ShieldUse(habitId, day.toEpochDay()))
        bump()
        return inserted != -1L
    }

    // ------------------------------------------------------------------ misc

    suspend fun allHabits(): List<Habit> = habitDao.getAll()
    suspend fun habit(id: Long): Habit? = habitDao.getById(id)
    suspend fun routine(id: Long): Routine? = routineDao.getById(id)

    suspend fun isEmpty(): Boolean = habitDao.getAll().isEmpty()

    /** Seeds a starter set of routines/habits for first launch. */
    suspend fun seedDefaults() {
        if (!isEmpty()) return
        val morning = routineDao.insert(Routine(name = "morning", icon = "☼", position = 0))
        val focus = routineDao.insert(Routine(name = "deep work", icon = "λ", position = 1))
        val evening = routineDao.insert(Routine(name = "evening", icon = "☾", position = 2))
        habitDao.insertAll(
            listOf(
                Habit(name = "meditate", type = HabitType.TIMER, target = 10, unit = "min", routineId = morning, color = "purple", position = 0),
                Habit(name = "drink water", type = HabitType.COUNTER, target = 8, unit = "cups", routineId = morning, color = "cyan", position = 1),
                Habit(name = "stretch", type = HabitType.CHECKBOX, routineId = morning, color = "green", position = 2),
                Habit(name = "focus session", type = HabitType.TIMER, target = 50, unit = "min", routineId = focus, color = "orange", position = 3),
                Habit(name = "commit code", type = HabitType.CHECKBOX, routineId = focus, color = "green", position = 4,
                    schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31),
                Habit(name = "read", type = HabitType.COUNTER, target = 20, unit = "pages", routineId = evening, color = "yellow", position = 5),
                Habit(name = "workout", type = HabitType.CHECKBOX, routineId = null, color = "red", position = 6,
                    schedule = ScheduleType.WEEKLY, timesPerWeek = 3),
            ),
        )
        bump()
    }

    private fun bump() { mutations.value = System.currentTimeMillis() }

    suspend fun awaitFirstDay(date: LocalDate) = observeDay(date).first()
}
