package com.resolvecommunity.awaydoomscrollin

internal object InstagramNotificationShadePolicy {
    fun canReuseShadeWindow(suspended: Boolean, currentWindow: Int, verifiedWindow: Int): Boolean =
        suspended && currentWindow >= 0 && verifiedWindow >= 0 && currentWindow == verifiedWindow
    fun returnedDuringProbe(suspended: Boolean, currentPackage: String?): Boolean =
        suspended && currentPackage == "com.instagram.android"
    fun useCurrentInstagramRoot(reportedSystemUi: Boolean, hasInstagramSession: Boolean): Boolean =
        reportedSystemUi && hasInstagramSession
    fun isProtectedFeedOrigin(screen: InstagramScreen): Boolean =
        screen == InstagramScreen.HOME_FEED || screen == InstagramScreen.EXPLORE
    // Opaque Samsung SystemUI panels omit the covered application entirely.
    // An explicitly different app is never equivalent to an omitted app.
    fun hasInstagramContext(foremostApp: String?, previouslyInstagram: Boolean): Boolean =
        foremostApp == "com.instagram.android" ||
            (foremostApp == null && previouslyInstagram)

    fun canRetain(enabled: Boolean, screen: InstagramScreen, curtainAttached: Boolean,
        instagramUnderneath: Boolean, shadeVisible: Boolean): Boolean =
        enabled && curtainAttached && instagramUnderneath && shadeVisible &&
            isProtectedFeedOrigin(screen)
}
