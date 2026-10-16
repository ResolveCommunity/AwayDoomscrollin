package com.resolvecommunity.awaydoomscrollin

internal object InstagramSearchGuardGeometry {
    fun clip(bounds: InstagramGeometrySample, keyboardTop: Int?): InstagramGeometrySample? {
        val bottom = minOf(bounds.bottom, keyboardTop ?: bounds.bottom)
        return bounds.copy(bottom = bottom).takeIf { it.right > it.left && it.bottom > it.top }
    }
}
