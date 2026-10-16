package com.resolvecommunity.awaydoomscrollin

/** Native profile gestures take priority over pre-tap Story interception. */
internal object InstagramProfileInteractionPolicy {
    fun exitsStoryViewer(mode: ProtectionMode): Boolean = mode == ProtectionMode.STRICT

    /** Balanced keeps intentional profile and product browsing available. */
    fun blocksProfilePosts(mode: ProtectionMode): Boolean = mode == ProtectionMode.STRICT

    fun allowsProfilePostFallback(mode: ProtectionMode, previousScreen: InstagramScreen): Boolean =
        mode == ProtectionMode.BALANCED && previousScreen == InstagramScreen.PROFILE

    fun interceptsProfileTouches(mode: ProtectionMode): Boolean = when (mode) {
        ProtectionMode.STRICT, ProtectionMode.BALANCED -> false
    }
}
