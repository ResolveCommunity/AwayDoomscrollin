package com.resolvecommunity.awaydoomscrollin

/**
 * Profile chrome can remain mounted behind Stories and Instagram's own sheets.
 * These rules identify the allowed layer without treating the mounted grid as
 * the foreground surface.
 */
internal object InstagramProfileSurfacePolicy {
    /**
     * Exact, visible node ids of Instagram's intentional profile-side layers.
     * These surfaces belong to the user's deliberate workflow (sharing their
     * profile, opening the profile menu, reading About this account); the
     * curtain must yield to every one of them while it is on screen and return
     * as soon as it closes.
     */
    val allowedSurfaceMarkerIds = listOf(
        // Share profile card (full-screen layer, not a bottom sheet)
        "profile_share_card",
        "profile_share_card_share_button",
        "profile_share_card_copy_link_button",
        "profile_share_card_download_button",
        "nametag_container",
        "nametag_outer_container",
        "background_mode_button",
        "background_mode_label",
        "qr_scan_button",
        // Edit profile body
        "edit_profile_fields",
        // Native bottom sheets (profile menu, action sheets)
        "bottom_sheet_container",
        "layout_container_bottom_sheet",
        "bottom_sheet_container_view",
        "action_sheet_container",
        "action_sheet_row_text_view",
        // About this account
        "about_this_account_container",
        "account_transparency_container",
        "account_transparency_root",
        "about_account_fragment"
    )

    /**
     * One decision point for every allowed profile-side layer. The ids are
     * exact matches so always-mounted decorative hosts (for example
     * bottom_sheet_camera_container on the ordinary profile) can never
     * release the curtain by accident.
     */
    // Localized titles (edit/share profile, about this account, highlight
    // picker) are deliberately not used: they matched Turkish/English only.
    // Edit/share/about leave the profile surface entirely (no profile ids), so
    // the profile curtain is released by structure in every language.
    @Suppress("UNUSED_PARAMETER")
    fun isAllowedProfileSurface(visibleIds: Set<String>, titles: Set<String> = emptySet()): Boolean =
        visibleIds.any(allowedSurfaceMarkerIds::contains)


    /**
     * A blocked collection opened from a protected feed's own bookmark button
     * returns to that feed on Back, so its mounted curtain must stay. One
     * opened from the profile menu returns to the menu (an allowed surface).
     */
    fun keepsFeedCoverOnCollectionExit(origin: InstagramScreen,
        mountedCurtain: InstagramScreen?): Boolean =
        (origin == InstagramScreen.EXPLORE || origin == InstagramScreen.HOME_FEED) &&
            mountedCurtain == origin

    /**
     * Saved's own pager/tabs and the Story archive; present in every UI
     * language (captured on device).
     */
    val userCollectionIds = setOf("saved_view_pager", "saved_tab_layout", "saved_content_container",
        "archive_home_fragment_container")

    /**
     * Your activity's Likes/Reposts/Tags/Recently deleted are Bloks grids with
     * no ids at all; their only language-free trait is the grid of image tiles.
     * Strict opens no media, so every such grid is one blocked library.
     */
    fun isIdlessMediaGrid(ids: Set<String>, idlessMediaTiles: Int): Boolean =
        "bloks_container" in ids && idlessMediaTiles >= 3

    fun isIdlessMediaTile(className: String, width: Int, height: Int, windowWidth: Int): Boolean =
        className.endsWith("ImageView") && windowWidth > 0 &&
            width * 4 >= windowWidth && width * 100 <= windowWidth * 55 &&
            height * 10 >= width * 8 && height * 10 <= width * 22

    fun isUserCollection(ids: Set<String>, idlessMediaTiles: Int = 0): Boolean =
        ids.any(userCollectionIds::contains) || isIdlessMediaGrid(ids, idlessMediaTiles)

    fun isAllowedDeparture(sourceIds: Set<String>, sourceLabels: Set<String>): Boolean {
        val opensHighlights = sourceIds.any { id ->
            id.contains("highlight") || id == "highlights_reel_tray_recycler_view"
        }
        val opensProfileMenu = sourceIds.any { id ->
            id in setOf(
                "action_bar_button_more", "profile_header_more_button",
                "right_action_bar_buttons", "overflow_menu_button"
            ) || id.contains("more_options") || id.contains("profile_menu")
        }
        // The profile banner editor and the Threads profile shortcut both live in the
        // profile header above the covered grid. "Threads" is a brand name that
        // Instagram does not translate; no other label is read.
        val opensBannerEditor = sourceIds.any { id -> id.contains("banner") }
        val opensThreadsProfile = sourceLabels.any { label -> label.contains("threads") }
        val opensProfileEditorOrShare = sourceIds.any { id ->
            id.contains("edit_profile") || id.contains("profile_edit") ||
                id.contains("share_profile") || id.contains("profile_share")
        }
        return opensHighlights || opensProfileMenu || opensBannerEditor ||
            opensThreadsProfile || opensProfileEditorOrShare ||
            isAboutAccountDeparture(sourceIds, sourceLabels)
    }

    @Suppress("UNUSED_PARAMETER")
    fun isAboutAccountDeparture(sourceIds: Set<String>, sourceLabels: Set<String>): Boolean =
        sourceIds.any { id ->
            id.contains("about_this_account") || id.contains("account_transparency")
        }

    fun hasAllowedLayer(ids: Set<String>, titles: Set<String> = emptySet()): Boolean {
        // Instagram's ordinary profile has bottom_sheet_camera_container as
        // an always-mounted host. Structural/decorative nodes are now exposed,
        // so substring matching would uncover the profile grid every scan.
        // Exact ids only: see isAllowedProfileSurface.
        return isAllowedProfileSurface(ids, titles)
    }
}
