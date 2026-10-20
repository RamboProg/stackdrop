package com.stackdrop.game.game

/** Theme unlock rules that do not depend on storage, kept apart so they can be unit tested. */
object ThemeRules {

    /**
     * Premium-only themes follow the premium purchase alone. Every other theme is free, bought with
     * gems, or earned by reaching its milestone ([milestoneBest] is the best in that mode).
     */
    fun isUnlocked(theme: Theme, bought: Boolean, milestoneBest: Int, premium: Boolean): Boolean {
        if (theme.premiumOnly) return premium
        if (theme.cost == 0 || bought) return true
        val m = theme.milestone ?: return false
        return milestoneBest >= m.score
    }

    /** One locked theme anyone may use today; it rotates with the local day number. Never a premium theme. */
    fun freeToday(themes: List<Theme>, day: Long, owned: (Theme) -> Boolean): Theme? {
        val locked = themes.filter { it.cost > 0 && !it.premiumOnly && !owned(it) }
        if (locked.isEmpty()) return null
        return locked[day.mod(locked.size.toLong()).toInt()]
    }

    /** Themes whose milestone was crossed by this run in [modeId]. */
    fun newlyReached(themes: List<Theme>, modeId: String, previousBest: Int, score: Int): List<Theme> =
        themes.filter {
            val m = it.milestone
            m != null && m.modeId == modeId && previousBest < m.score && score >= m.score
        }

    /** Short hint for a theme's milestone, e.g. "or reach 25 in Tower". */
    fun milestoneHint(m: Milestone): String =
        "or reach ${m.score} in ${m.modeId.replaceFirstChar { it.uppercase() }}"
}
