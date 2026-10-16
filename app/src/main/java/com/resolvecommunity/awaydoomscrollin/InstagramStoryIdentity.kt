package com.resolvecommunity.awaydoomscrollin

/** Story viewer IDs observed on-device. A story tray or generic pager is not a viewer. */
internal object InstagramStoryIdentity {
    fun isViewer(ids: Set<String>): Boolean = isViewer(ids::contains)

    // Stop at the first conclusive ID instead of querying every node over accessibility IPC.
    fun isViewer(hasVisibleId: (String) -> Boolean): Boolean =
        hasVisibleId("reel_viewer_root") || hasVisibleId("story_viewer_container") ||
            (hasVisibleId("reel_viewer_progress_bar") &&
                (hasVisibleId("reel_viewer_media_container") || hasVisibleId("reel_viewer_header_container"))) ||
            (hasVisibleId("reel_item_toolbar_container") && hasVisibleId("reel_viewer_media_container"))

    /**
     * Label of a tapped avatar that opens someone's Story ("nasa_es'in görülen
     * hikayesi", "nasa's story, unseen"). It travels with the click event, so
     * no remote query is needed. Own-story and creation entries ("Hikayen",
     * "Hikayene ekle", "Your story", "Add to story") are not Story launches.
     */
    fun isStoryLaunchLabel(label: String?): Boolean {
        val text = label.orEmpty().lowercase(java.util.Locale.ROOT)
        if (text.isBlank()) return false
        if (listOf("ekle", "add to", "your story", "hikayen", "hikâyen", "create", "oluştur")
                .any(text::contains)) return false
        return text.contains("hikayesi") || text.contains("hikâyesi") ||
            text.contains("'s story") || text.contains("’s story") ||
            text.contains("story by") || text.contains("unseen story")
    }

    /** The Story viewer opens in Instagram's modal activity window. */
    fun isStoryViewerWindowClass(className: CharSequence?): Boolean =
        className?.toString()?.endsWith("ModalActivity") == true
}
