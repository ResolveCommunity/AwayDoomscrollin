package com.resolvecommunity.awaydoomscrollin

/**
 * Shared explanations keep setup and the app's protection guide in sync.
 * Texts live in the string resources (scope_*, mode_summary_*); every
 * function takes the language, the app's own by default.
 */
internal object ProtectionCopy {
    /** What both modes share, then what each mode adds; setup shows them as separate blocks. */
    data class ModeScope(val both: String, val balanced: String, val strict: String)

    private fun t(language: AppLanguage, id: Int) = AppText.getIn(language, id)

    fun instagramScope(language: AppLanguage = AppText.language()) = ModeScope(
        both = t(language, R.string.scope_ig_both),
        balanced = t(language, R.string.scope_ig_balanced),
        strict = t(language, R.string.scope_ig_strict)
    )

    fun youtubeScope(language: AppLanguage = AppText.language()) = ModeScope(
        both = t(language, R.string.scope_yt_both),
        balanced = t(language, R.string.scope_yt_balanced),
        strict = t(language, R.string.scope_yt_strict)
    )

    /** Plain-text form for dialogs and bullet lists, one paragraph per part. */
    fun joined(scope: ModeScope, language: AppLanguage = AppText.language()): String =
        t(language, R.string.scope_label_both) + " " + scope.both + "\n\n" +
            t(language, R.string.scope_label_balanced) + " " + scope.balanced + "\n\n" +
            t(language, R.string.scope_label_strict) + " " + scope.strict

    fun instagram(language: AppLanguage = AppText.language()) = joined(instagramScope(language), language)

    fun youtube(language: AppLanguage = AppText.language()) = joined(youtubeScope(language), language)

    fun tiktok(language: AppLanguage = AppText.language()) = t(language, R.string.scope_tiktok)

    /** TikTok has one protection setting: what is stopped and what stays open. */
    data class SingleScope(val stopped: String, val open: String)

    fun tiktokScope(language: AppLanguage = AppText.language()) = SingleScope(
        stopped = t(language, R.string.scope_tiktok_stopped),
        open = t(language, R.string.scope_tiktok_open)
    )

    /**
     * One line under the mode picker, in setup and in the Apps tab: only what
     * the selected mode changes. The full scope lives in the scope dialog.
     */
    fun modeSummary(app: ProtectedApp, mode: ProtectionMode, language: AppLanguage = AppText.language()): String =
        when (app) {
            ProtectedApp.INSTAGRAM -> when (mode) {
                ProtectionMode.BALANCED -> t(language, R.string.mode_summary_ig_balanced)
                ProtectionMode.STRICT -> t(language, R.string.mode_summary_ig_strict)
            }
            ProtectedApp.YOUTUBE -> when (mode) {
                ProtectionMode.BALANCED -> t(language, R.string.mode_summary_yt_balanced)
                ProtectionMode.STRICT -> t(language, R.string.mode_summary_yt_strict)
            }
            ProtectedApp.TIKTOK -> ""
        }

    /** Applies to every app; shown once under the per-app scope. */
    fun detectionNote(language: AppLanguage = AppText.language()) = t(language, R.string.scope_detection_note)
}
