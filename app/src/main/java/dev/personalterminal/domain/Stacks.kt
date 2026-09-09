package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit

/**
 * Habit stacking: `after meditate` anchors a habit to another one. Today renders the chain
 * `meditate → journal → stretch`, greys a follower until its anchor is done, and a follower with
 * `anchorRemind` gets its reminder *when the anchor completes* instead of at a clock time.
 *
 * Pure helpers over the day's [HabitStatus] list; cycles are tolerated (a habit is never its own
 * ancestor when rendered, and [waitingOn] stops after [MAX_DEPTH] hops).
 */
object Stacks {

    private const val MAX_DEPTH = 8

    /** The anchor status for [habit] on this day, or null when none / archived / not due today. */
    fun anchorOf(habit: Habit, all: List<HabitStatus>): HabitStatus? =
        if (habit.anchorId <= 0L || habit.anchorId == habit.id) null else all.firstOrNull { it.habit.id == habit.anchorId && it.isDueToday }

    /**
     * The first *unfinished* anchor up the chain that [hs] is waiting on, or null when the habit
     * is free to go (no anchor, anchor done / skipped, or anchor not due today).
     */
    fun waitingOn(hs: HabitStatus, all: List<HabitStatus>): HabitStatus? {
        var cur = hs
        val seen = mutableSetOf(hs.habit.id)
        repeat(MAX_DEPTH) {
            val anchor = anchorOf(cur.habit, all) ?: return null
            if (anchor.habit.id in seen) return null
            seen += anchor.habit.id
            if (!anchor.completed && !anchor.skipped) return anchor
            cur = anchor
        }
        return null
    }

    /** Habits that follow [anchorId] directly (`after <anchor>`), in Today order. */
    fun followersOf(anchorId: Long, all: List<HabitStatus>): List<HabitStatus> =
        all.filter { it.habit.anchorId == anchorId && it.habit.id != anchorId && it.isDueToday }

    /** `meditate → journal → stretch` – the chain [hs] belongs to, root first. */
    fun chainLabel(hs: HabitStatus, all: List<HabitStatus>): String? {
        val up = ArrayList<String>()
        var cur = hs
        val seen = mutableSetOf(hs.habit.id)
        repeat(MAX_DEPTH) {
            val a = anchorOf(cur.habit, all) ?: return@repeat
            if (a.habit.id in seen) return@repeat
            seen += a.habit.id; up += a.habit.name; cur = a
        }
        val down = ArrayList<String>()
        var next: HabitStatus? = hs
        repeat(MAX_DEPTH) {
            val f = next?.let { n -> followersOf(n.habit.id, all).firstOrNull { it.habit.id !in seen } } ?: return@repeat
            seen += f.habit.id; down += f.habit.name; next = f
        }
        if (up.isEmpty() && down.isEmpty()) return null
        return (up.reversed() + hs.habit.name + down).joinToString(" → ")
    }

    /**
     * Followers of [anchorId] that should be nudged now that the anchor was just completed:
     * due today, not done, not skipped, opted into `anchorRemind`.
     */
    fun toNudge(anchorId: Long, all: List<HabitStatus>): List<HabitStatus> =
        followersOf(anchorId, all).filter { it.habit.anchorRemind && !it.completed && !it.skipped }

    /** Sort key for Today's "stack order": anchors before their followers, otherwise stable. */
    fun depth(habit: Habit, byId: Map<Long, Habit>): Int {
        var d = 0; var cur = habit
        val seen = mutableSetOf(habit.id)
        while (cur.anchorId > 0L && cur.anchorId !in seen && d < MAX_DEPTH) {
            cur = byId[cur.anchorId] ?: break
            seen += cur.id; d++
        }
        return d
    }
}
