package com.resolvecommunity.awaydoomscrollin

internal object InstagramAccountEntryIdentity {
    fun isPossibleSearchAccountSource(id: String): Boolean =
        id.startsWith("row_search_user_") || id.startsWith("row_search_avatar_")

    fun isProfileEntry(ids: Set<String>): Boolean =
        "row_search_user_follow_button" !in ids &&
            ids.any { it == "row_search_user_container" || it == "row_search_avatar_with_ring" }
}
