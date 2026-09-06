package dev.personalterminal.ui.navigation

object Routes {
    const val TODAY = "today"
    const val HABITS = "habits"
    const val HABIT_EDIT = "habit/edit?id={id}&routineId={routineId}"
    const val HABIT_DETAIL = "habit/{id}"
    const val ROUTINES = "routines"
    const val TIMER = "timer?habitId={habitId}"
    const val TIMELINE = "timeline"
    const val WATCHES = "watches"
    const val WATCH_EDIT = "watch/edit?id={id}"
    const val WATCH_DETAIL = "watch/{id}"
    const val WEAR_LOG = "wear/log?day={day}"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"

    fun habitEdit(id: Long = 0, routineId: Long? = null) = "habit/edit?id=$id&routineId=${routineId ?: -1}"
    fun habitDetail(id: Long) = "habit/$id"
    fun timer(habitId: Long = 0) = "timer?habitId=$habitId"
    fun watchEdit(id: Long = 0) = "watch/edit?id=$id"
    fun watchDetail(id: Long) = "watch/$id"
    fun wearLog(day: Long) = "wear/log?day=$day"
}

/** Bottom "tab bar" rendered like a tmux status line. */
enum class Tab(val route: String, val label: String, val index: Int) {
    TODAY(Routes.TODAY, "today", 0),
    HABITS(Routes.HABITS, "habits", 1),
    TIMER(Routes.timer(), "timer", 2),
    WATCH(Routes.WATCHES, "watch", 3),
    PROFILE(Routes.PROFILE, "profile", 4),
}
