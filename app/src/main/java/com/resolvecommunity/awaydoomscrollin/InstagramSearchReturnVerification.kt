package com.resolvecommunity.awaydoomscrollin

/** A bounded verification tail after one Back action. Never issues navigation. */
internal class InstagramSearchReturnVerification(private val timeoutMs: Long = 1_000L) {
    private var keyboardOnly: Boolean? = null
    private var deadline = 0L

    fun begin(keyboardWasVisible: Boolean, now: Long) {
        keyboardOnly = keyboardWasVisible
        deadline = now + timeoutMs
    }

    fun needsAnotherObservation(surface: InstagramSearchSurface, keyboardVisible: Boolean, now: Long): Boolean {
        val closingKeyboard = keyboardOnly ?: return false
        val completed = surface == InstagramSearchSurface.EXPLORE || surface == InstagramSearchSurface.HOME ||
            surface == InstagramSearchSurface.INBOX ||
            surface == InstagramSearchSurface.OTHER || surface == InstagramSearchSurface.DEPARTED ||
            (closingKeyboard && surface == InstagramSearchSurface.SEARCH && !keyboardVisible)
        if (completed || now >= deadline) {
            clear()
            return false
        }
        return true
    }

    fun clear() { keyboardOnly = null }
}
