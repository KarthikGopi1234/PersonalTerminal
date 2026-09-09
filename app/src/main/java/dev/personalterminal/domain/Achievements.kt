package dev.personalterminal.domain

import dev.personalterminal.data.db.FocusSession
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.data.db.WearLog
import java.time.LocalDate

/** One `man`-page entry. [progress] is 0..1; [unlocked] when the condition is met. */
data class Achievement(
    val id: String,
    val section: String,
    val name: String,
    val synopsis: String,
    val description: String,
    val progress: Float,
    val current: Int,
    val target: Int,
) {
    val unlocked: Boolean get() = progress >= 1f
}

/** Facts the achievement rules are evaluated against (computed once per screen). */
data class AchievementFacts(
    val totalCompletions: Int,
    val bestStreak: Int,
    val longestCurrentStreak: Int,
    val perfectDays: Int,
    val level: Int,
    val totalXp: Int,
    val habitsActive: Int,
    val shieldsUsed: Int,
    val skips: Int,
    val notes: Int,
    val moods: Int,
    val negativeKeptDays: Int,
    val focusMinutes: Int,
    val focusSessions: Int,
    val longestSession: Int,
    val watches: Int,
    val wearDays: Int,
    val wristShots: Int,
    val distinctWatchesWorn: Int,
    val earlyBirdDays: Int,
    val nightOwlDays: Int,
    /** Habit strength: best score across active habits and how many sit at ≥ 90. */
    val bestStrength: Int = 0,
    val strongHabits: Int = 0,
    /** Minimum-version days logged (`[~] min`). */
    val minimumDays: Int = 0,
    /** Habits stacked after another (`after`). */
    val stackedHabits: Int = 0,
) {
    companion object {
        fun from(habits: List<HabitWithLogs>, xp: Int, sessions: List<FocusSession>, wear: List<WearLog>, watches: Int, today: LocalDate = AppClock.today()): AchievementFacts {
            val logs = habits.flatMap { it.logs }
            val streaks = habits.map { Streaks.compute(it.habit, it.logs, it.shields, today) }
            val strengths = habits.filter { !it.habit.archived }.map { Strength.compute(it.habit, it.logs, today, it.shields.map { s -> s.day }.toSet()) }
            val byDay = logs.filter { it.completed && !it.skipped }.groupBy { it.day }
            val perfect = byDay.count { (day, ls) ->
                val d = LocalDate.ofEpochDay(day)
                val due = habits.filter { Schedule.isDue(it.habit, d) && it.logs.none { l -> l.day == day && l.skipped } }
                due.isNotEmpty() && due.all { hwl -> ls.any { it.habitId == hwl.habit.id } }
            }
            fun hourOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).hour
            val doneLogs = logs.filter { it.completed && !it.skipped }
            return AchievementFacts(
                totalCompletions = doneLogs.size,
                bestStreak = streaks.maxOfOrNull { it.best } ?: 0,
                longestCurrentStreak = streaks.maxOfOrNull { it.current } ?: 0,
                perfectDays = perfect,
                level = Progression.levelForXp(xp),
                totalXp = xp,
                habitsActive = habits.count { !it.habit.archived },
                shieldsUsed = habits.sumOf { it.shields.size },
                skips = logs.count { it.skipped },
                notes = logs.count { it.note.isNotBlank() },
                moods = logs.count { it.mood > 0 },
                negativeKeptDays = habits.filter { it.habit.negative }.sumOf { hwl -> hwl.logs.count { it.completed && !it.skipped } },
                focusMinutes = sessions.sumOf { it.minutes },
                focusSessions = sessions.size,
                longestSession = sessions.maxOfOrNull { it.minutes } ?: 0,
                watches = watches,
                wearDays = wear.map { it.day }.distinct().size,
                wristShots = wear.count { it.photoPath != null },
                distinctWatchesWorn = wear.map { it.watchId }.distinct().size,
                earlyBirdDays = doneLogs.filter { hourOf(it.updatedAt) < 7 }.map { it.day }.distinct().size,
                nightOwlDays = doneLogs.filter { hourOf(it.updatedAt) >= 23 }.map { it.day }.distinct().size,
                bestStrength = strengths.maxOfOrNull { it.score } ?: 0,
                strongHabits = strengths.count { it.score >= 90 },
                minimumDays = habits.sumOf { hwl -> hwl.logs.count { l -> !l.skipped && !l.completed && Targets.minimumReached(hwl.habit, l.value, LocalDate.ofEpochDay(l.day)) } },
                stackedHabits = habits.count { !it.habit.archived && it.habit.anchorId > 0L },
            )
        }
    }
}

/** The achievement catalogue, rendered as `man achievements`. */
object Achievements {
    fun evaluate(f: AchievementFacts): List<Achievement> {
        fun a(id: String, section: String, name: String, synopsis: String, desc: String, current: Int, target: Int) =
            Achievement(id, section, name, synopsis, desc, (current.toFloat() / target).coerceIn(0f, 1f), current.coerceAtMost(target), target)
        return listOf(
            // 1 – streaks
            a("streak7", "1 streaks", "week-one", "streak(7)", "Keep any habit going for 7 consecutive scheduled days.", f.bestStreak, 7),
            a("streak30", "1 streaks", "month-of-sundays", "streak(30)", "A 30-day streak on a single habit.", f.bestStreak, 30),
            a("streak100", "1 streaks", "centurion", "streak(100)", "100 days without breaking the chain. Shields count.", f.bestStreak, 100),
            a("streak365", "1 streaks", "uptime-365", "streak(365)", "One full year. The machine never went down.", f.bestStreak, 365),
            a("shield1", "1 streaks", "graceful-degradation", "shield --use", "Spend a shield to bridge a missed day.", f.shieldsUsed, 1),
            // 2 – volume
            a("done10", "2 volume", "hello-world", "done(10)", "Complete 10 habits in total.", f.totalCompletions, 10),
            a("done100", "2 volume", "commit-log", "done(100)", "100 completions logged.", f.totalCompletions, 100),
            a("done1000", "2 volume", "kernel-contributor", "done(1000)", "1,000 completions. That is a lot of ticks.", f.totalCompletions, 1000),
            a("perfect1", "2 volume", "clean-build", "perfect-day(1)", "Every scheduled habit done on one day.", f.perfectDays, 1),
            a("perfect30", "2 volume", "green-ci", "perfect-day(30)", "30 perfect days.", f.perfectDays, 30),
            a("habits5", "2 volume", "package-manager", "habit add ×5", "Keep 5 habits active at once.", f.habitsActive, 5),
            a("strong90", "2 volume", "load-average", "strength(90)", "Bring one habit's strength to 90 %. Strength forgives a miss; it just wants consistency.", f.bestStrength, 90),
            a("strong3", "2 volume", "high-availability", "strength ≥90 ×3", "Three habits at 90 % strength or more at the same time.", f.strongHabits, 3),
            a("min10", "2 volume", "two-minute-rule", "min ×10", "Ten days saved by the minimum version. Showing up beats skipping.", f.minimumDays, 10),
            a("stack1", "2 volume", "pipeline", "after <anchor>", "Stack a habit onto one you already do.", f.stackedHabits, 1),
            // 3 – levels
            a("lvl5", "3 levels", "committer", "level(5)", "Reach level 5.", f.level, 5),
            a("lvl10", "3 levels", "contributor", "level(10)", "Reach level 10.", f.level, 10),
            a("lvl20", "3 levels", "daemon", "level(20)", "Reach level 20.", f.level, 20),
            a("xp10k", "3 levels", "over-nine-thousand", "xp(10000)", "Earn 10,000 XP overall.", f.totalXp, 10_000),
            // 4 – focus
            a("focus1", "4 focus", "first-pomodoro", "timer 25", "Finish one focus session.", f.focusSessions, 1),
            a("focus10h", "4 focus", "deep-work", "focus(600m)", "10 hours of focus logged.", f.focusMinutes, 600),
            a("focus100h", "4 focus", "flow-state", "focus(6000m)", "100 hours of focus. Cal Newport would be proud.", f.focusMinutes, 6000),
            a("session90", "4 focus", "marathon", "session(90m)", "A single session of 90 minutes or more.", f.longestSession, 90),
            // 5 – reflection
            a("note10", "5 reflection", "changelog", "note ×10", "Attach 10 completion notes.", f.notes, 10),
            a("mood30", "5 reflection", "sentiment-analysis", "mood ×30", "Record your mood 30 times.", f.moods, 30),
            a("skip1", "5 reflection", "rest-day", "skip --reason", "Skip a day on purpose instead of breaking the chain.", f.skips, 1),
            a("avoid30", "5 reflection", "abstinence", "avoid(30)", "30 clean days on avoid-habits combined.", f.negativeKeptDays, 30),
            a("early10", "5 reflection", "early-bird", "done <07:00 ×10", "Complete something before 7 am on 10 days.", f.earlyBirdDays, 10),
            a("night10", "5 reflection", "night-owl", "done ≥23:00 ×10", "Complete something after 11 pm on 10 days.", f.nightOwlDays, 10),
            // 6 – watches
            a("watch1", "6 watches", "one-watch-collection", "watch add", "Add your first watch.", f.watches, 1),
            a("watch5", "6 watches", "watch-box", "watch add ×5", "Five watches in the collection.", f.watches, 5),
            a("wear30", "6 watches", "daily-driver", "wear ×30", "Log the watch you wore on 30 days.", f.wearDays, 30),
            a("wear365", "6 watches", "horologist", "wear ×365", "A full year of wear logs.", f.wearDays, 365),
            a("shots10", "6 watches", "wrist-check", "wristshot ×10", "Ten wrist shots in the log.", f.wristShots, 10),
            a("rotation3", "6 watches", "rotation", "wear --distinct 3", "Wear at least 3 different watches.", f.distinctWatchesWorn, 3),
        )
    }
}
