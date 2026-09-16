package com.resolvecommunity.awaydoomscrollin

/** Inbox-only evidence: thread headers/media previews do not qualify. */
internal object InstagramInboxPolicy {
    fun isDirectNavigation(id: String, description: String): Boolean =
        id == "direct_tab" || (id == "action_bar_button_action" &&
            description.trim().lowercase().let { it == "direct" || it.startsWith("direct,") ||
                it == "mesajlar" || it == "messages" })

    fun isInbox(listVisible: Boolean, headerVisible: Boolean, reelVisible: Boolean): Boolean =
        listVisible && headerVisible && !reelVisible

    /**
     * Instagram keeps the inbox hierarchy mounted while a conversation slides
     * over it. Only a list which still spans the display is a safe inbox; a
     * translated or clipped list belongs to an in-progress transition.
     */
    fun isStableInboxLayout(
        listBounds: InstagramGeometrySample,
        displayBounds: InstagramGeometrySample
    ): Boolean {
        val displayWidth = displayBounds.right - displayBounds.left
        val listWidth = listBounds.right - listBounds.left
        if (displayWidth <= 0 || listWidth <= 0) return false
        val edgeTolerance = maxOf(8, displayWidth / 50)
        return listWidth * 10 >= displayWidth * 9 &&
            listBounds.left <= displayBounds.left + edgeTolerance &&
            listBounds.right >= displayBounds.right - edgeTolerance
    }

    /**
     * A click inside the verified inbox list is allowed to open the thread, but
     * it must pre-arm the next screen. Header controls are deliberately rejected
     * even if Instagram reports an oversized source rectangle.
     */
    fun isThreadOpenIntent(
        sourceIds: Set<String>,
        sourceBounds: InstagramGeometrySample?,
        listBounds: InstagramGeometrySample?,
        sameWindow: Boolean
    ): Boolean {
        if (!sameWindow || sourceBounds == null || listBounds == null) return false
        val centerX = sourceBounds.left + (sourceBounds.right - sourceBounds.left) / 2
        val centerY = sourceBounds.top + (sourceBounds.bottom - sourceBounds.top) / 2
        if (centerX !in listBounds.left until listBounds.right ||
            centerY !in listBounds.top until listBounds.bottom) return false
        val isHeaderControl = sourceIds.any { id ->
            listOf("search", "camera", "settings", "compose", "action_bar", "tab_bar")
                .any(id::contains)
        }
        val sourceWidth = sourceBounds.right - sourceBounds.left
        val listWidth = listBounds.right - listBounds.left
        val explicitThreadRow = sourceIds.any { id ->
            id.contains("thread_row") || id.contains("row_thread") ||
                id.contains("inbox_thread")
        }
        val fullWidthRow = listWidth > 0 && sourceWidth * 4 >= listWidth * 3
        return !isHeaderControl && (explicitThreadRow || fullWidthRow)
    }
}
