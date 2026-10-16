package com.resolvecommunity.awaydoomscrollin

/** Messaging uses native touch handling; mode restrictions belong to the viewer. */
internal object InstagramDmInteractionPolicy {
    /** Strict keeps the conversation usable but consumes shared-media taps. */
    fun blocksSharedMedia(mode: ProtectionMode): Boolean = mode == ProtectionMode.STRICT

    fun prioritizesChangedViewer(mode: ProtectionMode, verifiedConversation: Boolean,
        changedWindow: Boolean): Boolean =
        mode == ProtectionMode.STRICT && verifiedConversation && changedWindow

    fun interceptsChatTouches(mode: ProtectionMode): Boolean = when (mode) {
        ProtectionMode.STRICT, ProtectionMode.BALANCED -> false
    }

    fun mayGuardEntry(screen: InstagramScreen, mainReelsTab: Boolean): Boolean =
        mainReelsTab || screen != InstagramScreen.DIRECT_MESSAGES
}
