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
    fun isAllowedProfileSurface(visibleIds: Set<String>, titles: Set<String> = emptySet()): Boolean {
        if (visibleIds.any(allowedSurfaceMarkerIds::contains)) return true
        if (isHighlightPicker(titles)) return true
        return titles.any { title ->
            val normalized = title.trim().lowercase(java.util.Locale.ROOT)
            normalized == "edit profile" || normalized == "profili düzenle" ||
                normalized == "share profile" || normalized == "profili paylaş" ||
                normalized.contains("about this account") ||
                normalized.contains("bu hesap hakkında")
        }
    }

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

    // Their only stable marker is the action-bar title: the grid itself is
    // Bloks-driven and carries no resource ids. These are user-curated
    // collections in Balanced; Strict still treats them as blocked.
    private val userCollectionTitles = listOf(
        "likes",
        "beğeniler",
        "saved",
        "kaydedilenler",
        "reposts",
        "repost",
        "yeniden paylaşılanlar",
        "yeniden paylaşılan",
        // Saved's typed filters open these secondary library surfaces.
        "all posts",
        "all reels",
        "all videos",
        "tüm gönderiler",
        "tüm reels",
        "tüm videolar"
    )

    /**
     * A blocked collection opened from a protected feed's own bookmark button
     * returns to that feed on Back, so its mounted curtain must stay. One
     * opened from the profile menu returns to the menu (an allowed surface).
     */
    fun keepsFeedCoverOnCollectionExit(origin: InstagramScreen,
        mountedCurtain: InstagramScreen?): Boolean =
        (origin == InstagramScreen.EXPLORE || origin == InstagramScreen.HOME_FEED) &&
            mountedCurtain == origin

    fun isUserCollectionTitle(titles: Set<String>): Boolean =
        titles.any { title ->
            val normalized = title.trim().lowercase(java.util.Locale.ROOT)
            userCollectionTitles.any { candidate ->
                normalized == candidate || normalized.startsWith("$candidate ")
            }
        }

    fun isBlockedCollectionTitle(titles: Set<String>): Boolean =
        isUserCollectionTitle(titles)

    /**
     * The profile menu is a safe settings surface. It is not classified as a
     * feed by the main screen classifier, so a curtain retained from Explore
     * must be released while this page is visible.
     */
    fun isSettingsSurfaceTitle(titles: Set<String>): Boolean =
        titles.any { title ->
            val normalized = title.trim().lowercase(java.util.Locale.ROOT)
            normalized == "settings and activity" ||
                normalized == "ayarlar ve hareketler" ||
                normalized == "close friends" ||
                normalized == "yakın arkadaşlar" ||
                normalized == "accounts center" ||
                normalized == "account center" ||
                normalized == "hesaplar merkezi" ||
                normalized == "hesap merkezi"
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
        // Instagram's ordinary profile has bottom_sheet_camera_container as
        // an always-mounted host. Structural/decorative nodes are now exposed,
        // so substring matching would uncover the profile grid every scan.
        // Exact ids only: see isAllowedProfileSurface.
        return isAllowedProfileSurface(ids, titles)
    }
}
