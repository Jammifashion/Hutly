package com.example.game.physics

enum class GlassStatus {
    EMPTY,
    FILLING,
    PERFECT,      // ±5% of target line
    GOOD,         // ±15% of target line
    UNDERFILLED,  // Released too early
    OVERFLOW      // Exceeded glass capacity (Verschüttet!)
}

data class Glass3D(
    val index: Int,
    val localAngleRad: Float,
    var targetFill: Float = 0.70f,
    var currentFill: Float = 0.0f,
    var status: GlassStatus = GlassStatus.EMPTY,
    var worldX: Float = 0f,
    var worldY: Float = 0f,
    var worldZ: Float = 0f,
    var screenX: Float = 0f,
    var screenY: Float = 0f,
    var screenRadius: Float = 0f,
    var screenHeight: Float = 0f,
    var depthZ: Float = 0f,
    val openingRadius: Float = 18f,
    val height: Float = 36f,
    var isAligned: Boolean = false,
    var isCollidingWithStream: Boolean = false,
    var liquidColor: Long = 0xFFFFB300, // Golden schnapps
    var hasSignaledTargetHaptic: Boolean = false
) {
    fun reset(newTargetFill: Float) {
        targetFill = newTargetFill
        currentFill = 0.0f
        status = GlassStatus.EMPTY
        isAligned = false
        isCollidingWithStream = false
        hasSignaledTargetHaptic = false
    }

    val isFinished: Boolean
        get() = status != GlassStatus.EMPTY && status != GlassStatus.FILLING
}

data class CollisionResult(
    val isPouring: Boolean,
    val hitGlassIndex: Int,
    val isDirectHit: Boolean,
    val isRimHit: Boolean,
    val isSpill: Boolean,
    val distanceToGlassCenter: Float,
    val alignmentRatio: Float, // 1.0f = perfect center, 0.0f = on the rim edge
    val streamImpactX: Float,
    val streamImpactY: Float,
    val streamImpactZ: Float,
    val hitGlass: Glass3D? = null,
    val message: String? = null
)

data class StreamDroplet(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var radius: Float,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f
)

data class SplashParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var size: Float,
    var alpha: Float,
    var color: Long = 0xFFFFD54F,
    var isGlassBottom: Boolean = false,
    var sparkle: Boolean = false
)

data class BottomSplashRipple(
    var x: Float,
    var y: Float,
    var z: Float,
    var radius: Float,
    var maxRadius: Float,
    var alpha: Float,
    var color: Long = 0xFFFFE082
)
