package com.example.game.physics

import kotlin.math.*
import kotlin.random.Random

/**
 * 3D Physics and Collision Engine for "Hut ist gut".
 * Computes rotation of the Sombrero hat, positions of the 11 shot glasses on the brim,
 * simulates the falling liquid stream from the bottle nozzle, and computes exact geometric
 * collision detection between the stream and the opening of each glass.
 */
class HatPhysicsEngine {

    companion object {
        const val NUM_GLASSES = 11
        const val BRIM_RADIUS = 145f
        const val GLASS_TRACK_RADIUS = 115f
        const val GLASS_OPENING_RADIUS = 19f
        const val GLASS_BOTTOM_RADIUS = 14f
        const val GLASS_HEIGHT = 38f
        const val RIM_PLANE_Y = 16f

        // Pouring nozzle is positioned overhead at the front of the hat track
        const val NOZZLE_X = 0f
        const val NOZZLE_Y = 78f
        const val NOZZLE_Z = GLASS_TRACK_RADIUS

        // Pouring speed
        const val FILL_RATE_PER_SEC = 0.52f // fills 100% in ~1.9 seconds
    }

    // 11 Shot glasses
    val glasses: List<Glass3D> = List(NUM_GLASSES) { i ->
        val angle = i * (2f * PI.toFloat() / NUM_GLASSES)
        Glass3D(
            index = i,
            localAngleRad = angle,
            targetFill = 0.70f,
            currentFill = 0f,
            status = GlassStatus.EMPTY,
            openingRadius = GLASS_OPENING_RADIUS,
            height = GLASS_HEIGHT
        )
    }

    // Hat rotation state
    var currentRotationAngle: Float = 0f
    var baseAngularVelocity: Float = 0.40f // rad/s
    var isManualJogging: Boolean = false
    var manualJogVelocity: Float = 0f

    // Bottle & Stream state
    var isPouring: Boolean = false
    var bottleTiltAngle: Float = 0f // 0 = upright, ~50 deg = tilted
    private val targetTiltAngle: Float
        get() = if (isPouring) 52f else 8f

    // Active particles
    val streamDroplets = mutableListOf<StreamDroplet>()
    val splashParticles = mutableListOf<SplashParticle>()
    val bottomSplashRipples = mutableListOf<BottomSplashRipple>()

    // Current collision feedback
    var lastCollisionResult: CollisionResult = CollisionResult(
        isPouring = false,
        hitGlassIndex = -1,
        isDirectHit = false,
        isRimHit = false,
        isSpill = false,
        distanceToGlassCenter = 999f,
        alignmentRatio = 0f,
        streamImpactX = NOZZLE_X,
        streamImpactY = RIM_PLANE_Y,
        streamImpactZ = NOZZLE_Z
    )
        private set

    // Stream wobble for dynamic fluid look
    private var streamWobblePhase = 0f

    fun resetRound(roundNumber: Int, targetFill: Float) {
        // Rotation speeds up each round
        baseAngularVelocity = 0.38f + (roundNumber - 1) * 0.08f
        isPouring = false
        bottleTiltAngle = 8f
        streamDroplets.clear()
        splashParticles.clear()
        bottomSplashRipples.clear()

        // Assign slightly varying colorful liqueur per round
        val liqueurColors = listOf(
            0xFFFFB300, // Golden Tequila
            0xFF00E676, // Herbal Mint
            0xFFFF1744, // Berry Schnapps
            0xFF2979FF, // Blue Curacao
            0xFFFF8A80, // Pink Grapefruit
            0xFFFFD700  // Golden Elixir
        )
        val roundColor = liqueurColors[(roundNumber - 1) % liqueurColors.size]

        glasses.forEachIndexed { idx, glass ->
            // Variation in target fill per glass around round target ±4%
            val jitter = ((idx % 3) - 1) * 0.03f
            val finalTarget = (targetFill + jitter).coerceIn(0.55f, 0.85f)
            glass.reset(finalTarget)
            glass.liquidColor = roundColor
        }
        updateGlassPositions()
    }

    /**
     * Advances the physics simulation by deltaTime seconds.
     */
    fun update(
        deltaTime: Float,
        onOverflow: (Glass3D) -> Unit = {},
        onTargetFillReached: ((Glass3D) -> Unit)? = null
    ) {
        val dt = deltaTime.coerceIn(0.001f, 0.05f)

        // 1. Update turntable rotation
        if (isManualJogging) {
            currentRotationAngle += manualJogVelocity * dt
            manualJogVelocity *= (1f - 4f * dt).coerceAtLeast(0f)
        } else {
            currentRotationAngle += baseAngularVelocity * dt
        }
        currentRotationAngle %= (2f * PI.toFloat())

        // 2. Smooth bottle tilting
        val tiltSpeed = 180f // degrees per sec
        if (bottleTiltAngle < targetTiltAngle) {
            bottleTiltAngle = min(targetTiltAngle, bottleTiltAngle + tiltSpeed * dt)
        } else if (bottleTiltAngle > targetTiltAngle) {
            bottleTiltAngle = max(targetTiltAngle, bottleTiltAngle - tiltSpeed * dt)
        }

        // 3. Update 3D World Positions of all 11 Glasses
        updateGlassPositions()

        // 4. Compute stream physics and collision
        updateStreamAndCollision(dt, onOverflow, onTargetFillReached)

        // 5. Update splash particles
        updateParticles(dt)
    }

    private fun updateGlassPositions() {
        for (glass in glasses) {
            val worldAngle = currentRotationAngle + glass.localAngleRad
            glass.worldX = GLASS_TRACK_RADIUS * sin(worldAngle)
            glass.worldZ = GLASS_TRACK_RADIUS * cos(worldAngle)
            glass.worldY = RIM_PLANE_Y

            // Alignment check: how close is this glass to the front pouring spot (0, RIM_PLANE_Y, GLASS_TRACK_RADIUS)?
            val distToPourSpot = sqrt(
                (glass.worldX - NOZZLE_X).pow(2) +
                (glass.worldZ - NOZZLE_Z).pow(2)
            )
            glass.isAligned = distToPourSpot <= GLASS_OPENING_RADIUS * 1.25f
        }
    }

    private fun updateStreamAndCollision(
        dt: Float,
        onOverflow: (Glass3D) -> Unit,
        onTargetFillReached: ((Glass3D) -> Unit)? = null
    ) {
        // Stream micro wobble
        streamWobblePhase += dt * 15f
        val wobbleX = if (isPouring) sin(streamWobblePhase) * 1.2f else 0f
        val wobbleZ = if (isPouring) cos(streamWobblePhase * 0.8f) * 1.0f else 0f

        val streamImpactX = NOZZLE_X + wobbleX
        val streamImpactY = RIM_PLANE_Y
        val streamImpactZ = NOZZLE_Z + wobbleZ

        // Reset per-glass collision flags
        glasses.forEach { it.isCollidingWithStream = false }

        if (!isPouring && bottleTiltAngle < 15f) {
            lastCollisionResult = CollisionResult(
                isPouring = false,
                hitGlassIndex = -1,
                isDirectHit = false,
                isRimHit = false,
                isSpill = false,
                distanceToGlassCenter = 999f,
                alignmentRatio = 0f,
                streamImpactX = streamImpactX,
                streamImpactY = streamImpactY,
                streamImpactZ = streamImpactZ
            )
            return
        }

        // Find the closest glass opening to the stream impact point
        var closestGlass: Glass3D? = null
        var minDistance = Float.MAX_VALUE

        for (glass in glasses) {
            val dist = sqrt(
                (glass.worldX - streamImpactX).pow(2) +
                (glass.worldZ - streamImpactZ).pow(2)
            )
            if (dist < minDistance) {
                minDistance = dist
                closestGlass = glass
            }
        }

        val directHit = closestGlass != null && minDistance <= GLASS_OPENING_RADIUS
        val rimHit = closestGlass != null && !directHit && minDistance <= (GLASS_OPENING_RADIUS * 1.55f)
        val spill = !directHit && !rimHit

        var message: String? = null
        var alignmentRatio = 0f

        if (closestGlass != null) {
            alignmentRatio = (1.0f - (minDistance / GLASS_OPENING_RADIUS)).coerceIn(0f, 1f)

            if (directHit) {
                closestGlass.isCollidingWithStream = true
                if (closestGlass.status == GlassStatus.EMPTY || closestGlass.status == GlassStatus.FILLING) {
                    closestGlass.status = GlassStatus.FILLING
                    closestGlass.currentFill += FILL_RATE_PER_SEC * dt

                    // Calculate contact surface height inside the glass (from bottom to rising liquid level)
                    val glassBottomY = RIM_PLANE_Y - GLASS_HEIGHT
                    val currentSurfaceY = glassBottomY + (closestGlass.currentFill * GLASS_HEIGHT)

                    // Generate lively splash particles & ripple animations on contact with glass bottom / liquid surface
                    spawnGlassBottomSplashes(
                        glass = closestGlass,
                        surfaceY = currentSurfaceY,
                        impactX = streamImpactX,
                        impactZ = streamImpactZ,
                        liquidColor = closestGlass.liquidColor
                    )

                    // Generate bubbles inside the glass
                    if (Random.nextFloat() < 0.25f) {
                        spawnBubbleInGlass(closestGlass)
                    }

                    // Trigger haptic sweet spot when target fill line is crossed
                    if (!closestGlass.hasSignaledTargetHaptic && closestGlass.currentFill >= (closestGlass.targetFill - 0.04f)) {
                        closestGlass.hasSignaledTargetHaptic = true
                        onTargetFillReached?.invoke(closestGlass)
                    }

                    // Check for overflow
                    if (closestGlass.currentFill >= 1.05f && closestGlass.status != GlassStatus.OVERFLOW) {
                        closestGlass.status = GlassStatus.OVERFLOW
                        spawnHeavySplashes(streamImpactX, streamImpactY, streamImpactZ, 25)
                        onOverflow(closestGlass)
                    }
                }
                message = if (alignmentRatio > 0.75f) "Perfekt ausgerichtet!" else "Im Glas!"
            } else if (rimHit) {
                closestGlass.isCollidingWithStream = true
                // Rim hit: partial liquid enters, droplets splash outwards
                if (closestGlass.status == GlassStatus.EMPTY || closestGlass.status == GlassStatus.FILLING) {
                    closestGlass.status = GlassStatus.FILLING
                    closestGlass.currentFill += (FILL_RATE_PER_SEC * 0.35f) * dt
                }
                spawnRimSplashes(streamImpactX, streamImpactY, streamImpactZ, 2)
                message = "Am Rand! Vorsicht!"
            } else if (spill) {
                // Liquid hits the hat surface / table
                spawnSpillSplashes(streamImpactX, streamImpactY, streamImpactZ, 3)
                message = "Verschüttet! Daneben!"
            }
        }

        // Spawn stream flow particles
        if (isPouring) {
            spawnStreamDroplets(streamImpactX, streamImpactZ)
        }

        lastCollisionResult = CollisionResult(
            isPouring = isPouring,
            hitGlassIndex = if (directHit || rimHit) closestGlass?.index ?: -1 else -1,
            isDirectHit = directHit,
            isRimHit = rimHit,
            isSpill = spill,
            distanceToGlassCenter = minDistance,
            alignmentRatio = alignmentRatio,
            streamImpactX = streamImpactX,
            streamImpactY = streamImpactY,
            streamImpactZ = streamImpactZ,
            hitGlass = closestGlass,
            message = message
        )
    }

    private fun spawnStreamDroplets(targetX: Float, targetZ: Float) {
        val count = 2
        for (k in 0 until count) {
            val progress = Random.nextFloat()
            val startY = NOZZLE_Y
            val currentY = startY - progress * (startY - RIM_PLANE_Y)
            val currentX = NOZZLE_X + (targetX - NOZZLE_X) * progress + (Random.nextFloat() - 0.5f) * 1.5f
            val currentZ = NOZZLE_Z + (targetZ - NOZZLE_Z) * progress + (Random.nextFloat() - 0.5f) * 1.5f

            if (streamDroplets.size < 40) {
                streamDroplets.add(
                    StreamDroplet(
                        x = currentX,
                        y = currentY,
                        z = currentZ,
                        vx = (Random.nextFloat() - 0.5f) * 4f,
                        vy = -180f,
                        vz = (Random.nextFloat() - 0.5f) * 4f,
                        radius = 2.2f + Random.nextFloat() * 1.2f,
                        alpha = 0.9f,
                        life = 0.3f
                    )
                )
            }
        }
    }

    /**
     * Spawns lively micro-splash droplet particles and impact ripple animations
     * when the incoming schnapps stream strikes the glass bottom or rising liquid surface.
     */
    private fun spawnGlassBottomSplashes(
        glass: Glass3D,
        surfaceY: Float,
        impactX: Float,
        impactZ: Float,
        liquidColor: Long
    ) {
        val glassBottomY = RIM_PLANE_Y - GLASS_HEIGHT
        val isAtBottom = (surfaceY - glassBottomY) < 3.5f // Contact directly with the solid glass bottom

        // More energetic and widespread splash droplets on hard glass bottom impact
        val particleCount = if (isAtBottom) 3 else 2
        for (i in 0 until particleCount) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            // Radial outward velocity, bounded by glass bottom radius
            val speed = if (isAtBottom) 12f + Random.nextFloat() * 24f else 8f + Random.nextFloat() * 16f
            val upwardSpeed = if (isAtBottom) 26f + Random.nextFloat() * 34f else 18f + Random.nextFloat() * 26f

            // Color palette: glistening droplets tinted with schnapps color + sparkling white/bright highlight
            val colorChoices = listOf(
                liquidColor,
                0xFFFFF59D, // Warm amber foam
                0xFFFFFDE7, // Creamy froth
                0xFFFFFFFF  // Specular gleam
            )
            val splashColor = colorChoices[Random.nextInt(colorChoices.size)]

            if (splashParticles.size < 60) {
                splashParticles.add(
                    SplashParticle(
                        x = impactX + (Random.nextFloat() - 0.5f) * 1.5f,
                        y = surfaceY + 0.5f,
                        z = impactZ + (Random.nextFloat() - 0.5f) * 1.5f,
                        vx = cos(angle) * speed,
                        vy = upwardSpeed,
                        vz = sin(angle) * speed,
                        size = if (isAtBottom) 2.2f + Random.nextFloat() * 2.2f else 1.8f + Random.nextFloat() * 1.6f,
                        alpha = 1.0f,
                        color = splashColor,
                        isGlassBottom = true,
                        sparkle = Random.nextFloat() < 0.35f
                    )
                )
            }
        }

        // Spawn contact ripple animation on the glass bottom plane
        if (bottomSplashRipples.size < 10 && Random.nextFloat() < 0.40f) {
            bottomSplashRipples.add(
                BottomSplashRipple(
                    x = impactX,
                    y = surfaceY,
                    z = impactZ,
                    radius = 1.0f,
                    maxRadius = GLASS_BOTTOM_RADIUS * 0.95f,
                    alpha = 0.85f,
                    color = liquidColor
                )
            )
        }
    }

    private fun spawnBubbleInGlass(glass: Glass3D) {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val r = Random.nextFloat() * (GLASS_OPENING_RADIUS * 0.7f)
        val bx = glass.worldX + r * cos(angle)
        val bz = glass.worldZ + r * sin(angle)
        val fillHeight = glass.currentFill * GLASS_HEIGHT
        val by = (RIM_PLANE_Y - GLASS_HEIGHT) + fillHeight

        splashParticles.add(
            SplashParticle(
                x = bx,
                y = by,
                z = bz,
                vx = (Random.nextFloat() - 0.5f) * 6f,
                vy = 8f + Random.nextFloat() * 12f,
                vz = (Random.nextFloat() - 0.5f) * 6f,
                size = 2.5f + Random.nextFloat() * 2.0f,
                alpha = 0.85f,
                color = 0xFFFFF59D
            )
        )
    }

    private fun spawnRimSplashes(x: Float, y: Float, z: Float, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 25f + Random.nextFloat() * 35f
            splashParticles.add(
                SplashParticle(
                    x = x,
                    y = y + 1f,
                    z = z,
                    vx = cos(angle) * speed,
                    vy = 20f + Random.nextFloat() * 30f,
                    vz = sin(angle) * speed,
                    size = 2.5f + Random.nextFloat() * 1.8f,
                    alpha = 1.0f,
                    color = 0xFFFFCA28
                )
            )
        }
    }

    private fun spawnSpillSplashes(x: Float, y: Float, z: Float, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 30f + Random.nextFloat() * 50f
            splashParticles.add(
                SplashParticle(
                    x = x,
                    y = y - 4f,
                    z = z,
                    vx = cos(angle) * speed,
                    vy = 15f + Random.nextFloat() * 25f,
                    vz = sin(angle) * speed,
                    size = 3.0f + Random.nextFloat() * 2.5f,
                    alpha = 1.0f,
                    color = 0xFFFF7043
                )
            )
        }
    }

    private fun spawnHeavySplashes(x: Float, y: Float, z: Float, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 40f + Random.nextFloat() * 70f
            splashParticles.add(
                SplashParticle(
                    x = x,
                    y = y + 5f,
                    z = z,
                    vx = cos(angle) * speed,
                    vy = 35f + Random.nextFloat() * 45f,
                    vz = sin(angle) * speed,
                    size = 4f + Random.nextFloat() * 3.5f,
                    alpha = 1.0f,
                    color = 0xFFFF5722
                )
            )
        }
    }

    private fun updateParticles(dt: Float) {
        // Update stream droplets
        val dropletIterator = streamDroplets.iterator()
        while (dropletIterator.hasNext()) {
            val d = dropletIterator.next()
            d.y += d.vy * dt
            d.x += d.vx * dt
            d.z += d.vz * dt
            d.life -= dt * 3.5f
            d.alpha = (d.life / 0.3f).coerceIn(0f, 1f)
            if (d.life <= 0f || d.y <= RIM_PLANE_Y - 5f) {
                dropletIterator.remove()
            }
        }

        // Update splash particles
        val splashIterator = splashParticles.iterator()
        while (splashIterator.hasNext()) {
            val p = splashIterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            if (p.isGlassBottom) {
                // Gravity pulls droplet back down into glass
                p.vy -= 120f * dt
                // Horizontal drag inside the glass
                p.vx *= (1f - dt * 2.5f)
                p.vz *= (1f - dt * 2.5f)
                p.alpha -= dt * 2.8f
            } else {
                p.vy -= 90f * dt // Gravity
                p.alpha -= dt * 2.2f
            }
            if (p.alpha <= 0f || p.y < -30f) {
                splashIterator.remove()
            }
        }

        // Update bottom splash ripples
        val rippleIterator = bottomSplashRipples.iterator()
        while (rippleIterator.hasNext()) {
            val r = rippleIterator.next()
            r.radius += dt * 28f
            r.alpha -= dt * 2.8f
            if (r.alpha <= 0f || r.radius >= r.maxRadius) {
                rippleIterator.remove()
            }
        }
    }

    /**
     * Evaluates a glass when pouring stops or the glass moves away.
     */
    fun evaluateGlassFill(glass: Glass3D): GlassStatus {
        if (glass.currentFill < 0.15f) {
            glass.status = GlassStatus.EMPTY
            glass.currentFill = 0f
            return GlassStatus.EMPTY
        }

        if (glass.currentFill > 1.05f) {
            glass.status = GlassStatus.OVERFLOW
            return GlassStatus.OVERFLOW
        }

        val diff = abs(glass.currentFill - glass.targetFill)
        val status = when {
            diff <= 0.05f -> GlassStatus.PERFECT
            diff <= 0.15f -> GlassStatus.GOOD
            else -> GlassStatus.UNDERFILLED
        }
        glass.status = status
        return status
    }

    /**
     * Manually nudges the turntable (e.g. user drags or taps jog buttons)
     */
    fun jogTurntable(deltaAngle: Float) {
        currentRotationAngle += deltaAngle
        currentRotationAngle %= (2f * PI.toFloat())
        updateGlassPositions()
    }
}
