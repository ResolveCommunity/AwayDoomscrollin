package com.resolvecommunity.awaydoomscrollin

/** Android-free rectangle sample shared by geometry and input policies. */
internal data class InstagramGeometrySample(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    private fun Int.near(other: Int, tolerance: Int): Boolean =
        kotlin.math.abs(this - other) <= tolerance

    fun approximatelyEquals(other: InstagramGeometrySample, tolerance: Int): Boolean =
        left.near(other.left, tolerance) && top.near(other.top, tolerance) &&
            right.near(other.right, tolerance) && bottom.near(other.bottom, tolerance)
}
