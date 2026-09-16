package com.resolvecommunity.awaydoomscrollin

/**
 * Profile chrome can remain mounted behind Stories and Instagram's own sheets.
 * These rules identify the allowed layer without treating the mounted grid as
 * the foreground surface.
 */
internal object InstagramProfileSurfacePolicy {
    private val allowedLayerIds = setOf(
        "bottom_sheet_container",
        "layout_container_bottom_sheet",
        "bottom_sheet_container_view",
        "action_sheet_container",
        "action_sheet_row_text_view"
    )

    // The full-screen "Öne çıkanlara ekle" story picker is reached from the profile
    // header, not from the covered media grid. Its action-bar title is the stable signal:
    // while it is foreground the profile curtain must stay released. The match uses the
    // "öne çıkan" stem because the title switches to "N selected" once stories are
    // checked and to edit-screen titles later in the flow; afterwards nothing re-mounts
    // the curtain because the picker has no profile ids.
    private val highlightPickerTitles = listOf(
        "öne çıkan",
        "add to highlights"
    )

    fun isHighlightPicker(titles: Set<String>): Boolean =
        titles.any { title ->
            val normalized = title.trim().lowercase(java.util.Locale.ROOT)
            highlightPickerTitles.any(normalized::contains)
        }

    // Menu collection screens (Saved / Likes / Reposts) are full media grids - the same
    // doomscroll surface class as the profile grid, reached through the profile menu.
    // Their only stable marker is the action-bar title: the grid itself is Bloks-driven
    // and carries no resource ids.
    private val blockedCollectionTitles = listOf(
        "likes",
        "beğeniler",
        "saved",
        "kaydedilenler",
        "repost",
        "yeniden paylaşılan"
    )

    fun isBlockedCollectionTitle(titles: Set<String>): Boolean =
        titles.any { title ->
            val normalized = title.trim().lowercase(java.util.Locale.ROOT)
            blockedCollectionTitles.any(normalized::contains)
        }

    fun isAllowedDeparture(sourceIds: Set<String>, sourceLabels: Set<String>): Boolean {
        val opensHighlights = sourceIds.any { id ->
            id.contains("highlight") || id == "highlights_reel_tray_recycler_view"
        } || sourceLabels.any { label ->
            label.contains("highlight") || label.contains("öne çıkan")
        }
        val opensProfileMenu = sourceIds.any { id ->
            id in setOf(
                "action_bar_button_more", "profile_header_more_button",
                "right_action_bar_buttons", "overflow_menu_button"
            ) || id.contains("more_options") || id.contains("profile_menu")
        } || sourceLabels.any { label ->
            label in setOf("more", "options", "diğer", "seçenekler") ||
                label.contains("more options") || label.contains("diğer seçenek")
        }
        // The profile banner editor and the Threads profile shortcut both live in the
        // profile header above the covered grid.
        val opensBannerEditor = sourceIds.any { id -> id.contains("banner") } ||
            sourceLabels.any { label -> label.contains("banner") }
        val opensThreadsProfile = sourceLabels.any { label -> label.contains("threads") }
        val opensProfileEditorOrShare = sourceIds.any { id ->
            id.contains("edit_profile") || id.contains("profile_edit") ||
                id.contains("share_profile") || id.contains("profile_share")
        } || sourceLabels.any { label ->
            label == "edit profile" || label == "profili düzenle" ||
                label == "share profile" || label == "profili paylaş"
        }
        return opensHighlights || opensProfileMenu || opensBannerEditor ||
            opensThreadsProfile || opensProfileEditorOrShare ||
            isAboutAccountDeparture(sourceIds, sourceLabels)
    }

    fun isAboutAccountDeparture(sourceIds: Set<String>, sourceLabels: Set<String>): Boolean =
        sourceIds.any { id ->
            id.contains("about_this_account") || id.contains("account_transparency")
        } || sourceLabels.any { label ->
            label.contains("about this account") || label.contains("bu hesap hakkında")
        }

    fun hasAllowedLayer(ids: Set<String>, titles: Set<String> = emptySet()): Boolean {
        val hasNativeSheet = ids.any { id ->
            id in allowedLayerIds || id.contains("action_sheet") || id.contains("bottom_sheet")
        }
        val isAboutAccount = ids.any { id ->
            id.contains("about_this_account") || id.contains("account_transparency")
        } || titles.any { title ->
            title.contains("about this account") || title.contains("bu hesap hakkında")
        }
        val isProfileEditorOrShare = titles.any { title ->
            title == "edit profile" || title == "profili düzenle" ||
                title == "share profile" || title == "profili paylaş"
        }
        return hasNativeSheet || isAboutAccount || isProfileEditorOrShare ||
            isHighlightPicker(titles)
    }
}
