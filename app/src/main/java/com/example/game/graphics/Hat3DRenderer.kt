package com.example.game.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.game.physics.Glass3D
import com.example.game.physics.GlassStatus
import com.example.game.physics.HatPhysicsEngine
import com.example.state.HatTier
import kotlin.math.*

/**
 * Three.js-style Camera Presets tailored for vertical mobile smartphone screens.
 */
enum class CameraPreset(
    val displayName: String,
    val shortName: String,
    val pitchDeg: Float,
    val cameraDistance: Float,
    val focalLength: Float,
    val zoomMultiplier: Float,
    val verticalCenterOffset: Float
) {
    // Zoomed-in Close-up perspective: makes the Sombrero and 11 shot glasses fill the screen with maximum visibility
    CLOSE_UP(
        displayName = "Nahaufnahme (Fokus)",
        shortName = "Nah",
        pitchDeg = 44.0f,
        cameraDistance = 400f,
        focalLength = 520f,
        zoomMultiplier = 1.38f,
        verticalCenterOffset = 0.52f
    ),

    // Standard overview: balanced full view of the hat, turntable, and overhead bottle
    STANDARD(
        displayName = "Standard",
        shortName = "Normal",
        pitchDeg = 41.5f,
        cameraDistance = 450f,
        focalLength = 500f,
        zoomMultiplier = 1.18f,
        verticalCenterOffset = 0.54f
    ),

    // Top-down Party view: steeper look-down angle into all 11 glass cavities
    TOP_DOWN(
        displayName = "Aufsicht",
        shortName = "Aufsicht",
        pitchDeg = 55.0f,
        cameraDistance = 470f,
        focalLength = 520f,
        zoomMultiplier = 1.25f,
        verticalCenterOffset = 0.51f
    )
}

/**
 * High-definition 3D Renderer for "Hut ist gut".
 * Renders the Sombrero party hat, 11 shot glasses with liquid & fill lines,
 * procedural fluid stream, collision feedback halos, bottle, and party lighting.
 * Automatically adapts its scale and 3D camera to fill the screen dynamically so the hat
 * and shot glasses are large, clear, and easily visible on mobile devices.
 */
class Hat3DRenderer {

    // Current camera parameters
    private var pitchRad = 44.0f * (PI.toFloat() / 180f)
    private var cosPitch = cos(pitchRad)
    private var sinPitch = sin(pitchRad)
    private var cameraDistance = 400f
    private var cameraFocalLength = 520f

    /**
     * Updates the internal 3D perspective camera matrix.
     */
    fun configureCamera(pitchDeg: Float, distance: Float, focalLength: Float) {
        pitchRad = pitchDeg * (PI.toFloat() / 180f)
        cosPitch = cos(pitchRad)
        sinPitch = sin(pitchRad)
        cameraDistance = distance
        cameraFocalLength = focalLength
    }

    // 3D Projection helper
    fun project3D(
        x: Float,
        y: Float,
        z: Float,
        centerX: Float,
        centerY: Float,
        scaleMultiplier: Float
    ): Triple<Float, Float, Float> {
        val xRot = x
        val yRot = y * cosPitch - z * sinPitch
        val zRot = y * sinPitch + z * cosPitch

        val distance = (zRot + cameraDistance).coerceAtLeast(100f)
        val perspective = cameraFocalLength / distance
        val scale = perspective * scaleMultiplier

        val sx = centerX + xRot * scale
        val sy = centerY - yRot * scale
        return Triple(sx, sy, zRot)
    }

    fun drawScene(
        drawScope: DrawScope,
        physics: HatPhysicsEngine,
        hatTier: HatTier,
        zoom: Float = 1.0f,
        cameraPreset: CameraPreset = CameraPreset.CLOSE_UP,
        customPitchDeg: Float? = null,
        customDistance: Float? = null
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        // Configure camera matrix
        val targetPitch = customPitchDeg ?: cameraPreset.pitchDeg
        val targetDist = customDistance ?: cameraPreset.cameraDistance
        configureCamera(targetPitch, targetDist, cameraPreset.focalLength)

        // Calculate dynamic adaptive scene scale so the party hat fills ~92% of available width
        val brimWorldDiameter = HatPhysicsEngine.BRIM_RADIUS * 2f // 290 units
        val scaleByWidth = (width * 0.94f) / brimWorldDiameter
        val scaleByHeight = (height * 0.86f) / 250f
        val baseUnitScale = min(scaleByWidth, scaleByHeight).coerceAtLeast(0.45f) * cameraPreset.zoomMultiplier
        val effectiveScale = baseUnitScale * zoom

        val centerX = width / 2f
        val centerY = height * cameraPreset.verticalCenterOffset

        // 1. Draw Turntable Base
        drawTurntable(drawScope, centerX, centerY, effectiveScale)

        // 2. Project all 11 glasses and determine depth sorting
        for (glass in physics.glasses) {
            val (sx, sy, depth) = project3D(glass.worldX, glass.worldY, glass.worldZ, centerX, centerY, effectiveScale)
            glass.screenX = sx
            glass.screenY = sy
            glass.depthZ = depth

            // Screen scale at this depth
            val dist = (depth + cameraDistance).coerceAtLeast(100f)
            val scale = (cameraFocalLength / dist) * effectiveScale
            glass.screenRadius = glass.openingRadius * scale
            glass.screenHeight = glass.height * scale
        }

        // Separate glasses into background (depth < 20, behind crown) and foreground (depth >= 20)
        val sortedGlasses = physics.glasses.sortedBy { it.depthZ }
        val backGlasses = sortedGlasses.filter { it.depthZ < 20f }
        val frontGlasses = sortedGlasses.filter { it.depthZ >= 20f }

        // 3. Draw Hat Brim (Back & Floor)
        drawHatBrim(drawScope, centerX, centerY, physics.currentRotationAngle, hatTier, effectiveScale)

        // 4. Draw Background Glasses (behind the central crown)
        for (glass in backGlasses) {
            drawShotGlass(drawScope, glass, effectiveScale, isFront = false)
        }

        // 5. Draw Central Sombrero Crown (Egg-shaped dome with zigzag relief)
        drawHatCrown(drawScope, centerX, centerY, physics.currentRotationAngle, hatTier, effectiveScale)

        // 6. Draw Foreground Glasses (in front of the central crown)
        for (glass in frontGlasses) {
            drawShotGlass(drawScope, glass, effectiveScale, isFront = true)
        }

        // 7. Draw Active Collision Reticle & Alignment Feedback on the active front spot
        drawCollisionFeedback(drawScope, physics, centerX, centerY, effectiveScale)

        // 8. Draw Liquid Stream and Splash Droplets
        drawLiquidStream(drawScope, physics, centerX, centerY, effectiveScale)

        // 9. Draw Suspended Bottle at the top
        drawSuspendedBottle(drawScope, physics, centerX, centerY, effectiveScale)
    }

    private fun drawTurntable(
        drawScope: DrawScope,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        val (bx, by, _) = project3D(0f, -22f, 0f, centerX, centerY, scale)
        val rx = 180f * scale
        val ry = rx * sinPitch * 1.05f

        // Turntable shadow
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x66000000), Color(0x00000000)),
                center = Offset(bx, by + 12f * scale),
                radius = rx * 1.2f
            ),
            topLeft = Offset(bx - rx * 1.2f, by + 12f * scale - ry * 1.2f),
            size = Size(rx * 2.4f, ry * 2.4f)
        )

        // Turntable metallic rim
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF212121), Color(0xFF455A64)),
                start = Offset(bx - rx, by - ry),
                end = Offset(bx + rx, by + ry)
            ),
            topLeft = Offset(bx - rx, by - ry),
            size = Size(rx * 2f, ry * 2f)
        )

        // Turntable glowing LED party groove
        drawScope.drawOval(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFFD54F),
                    Color(0xFFFF4081),
                    Color(0xFF00E5FF),
                    Color(0xFF76FF03),
                    Color(0xFFFFD54F)
                ),
                center = Offset(bx, by)
            ),
            topLeft = Offset(bx - rx * 0.94f, by - ry * 0.94f),
            size = Size(rx * 1.88f, ry * 1.88f),
            style = Stroke(width = (4.0f * (scale / 2f)).coerceIn(3.0f, 9f))
        )
    }

    private fun drawHatBrim(
        drawScope: DrawScope,
        centerX: Float,
        centerY: Float,
        rotationAngle: Float,
        hatTier: HatTier,
        scale: Float
    ) {
        val (bx, by, _) = project3D(0f, 0f, 0f, centerX, centerY, scale)
        val outerR = HatPhysicsEngine.BRIM_RADIUS * scale
        val outerRy = outerR * sinPitch

        val baseColor = Color(hatTier.primaryColor)
        val shadeColor = Color(hatTier.secondaryColor)
        val accentColor = Color(hatTier.accentColor)

        // Outer Sombrero Brim Dish (Deep rich gradient with specular lighting)
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0.96f),
                    shadeColor.copy(alpha = 0.98f),
                    if (hatTier.isMetallic) Color(0xFFFFE082) else accentColor.copy(alpha = 0.92f)
                ),
                center = Offset(bx, by - 20f * scale),
                radius = outerR
            ),
            topLeft = Offset(bx - outerR, by - outerRy),
            size = Size(outerR * 2f, outerRy * 2f)
        )

        // Brim Outer Lip Highlight (Shiny molded plastic rim edge)
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.75f),
                    accentColor,
                    Color.White.copy(alpha = 0.40f),
                    shadeColor
                ),
                start = Offset(bx - outerR, by - outerRy),
                end = Offset(bx + outerR, by + outerRy)
            ),
            topLeft = Offset(bx - outerR, by - outerRy),
            size = Size(outerR * 2f, outerRy * 2f),
            style = Stroke(width = (6.0f * (scale / 2f)).coerceIn(3.5f, 14f))
        )

        // Embossed Festive Zig-Zag pattern on brim
        val zigPath = Path()
        val numZigs = 22
        val zigRadius = outerR * 0.82f
        for (i in 0..numZigs) {
            val a = rotationAngle + (i.toFloat() / numZigs) * 2f * PI.toFloat()
            val rOffset = if (i % 2 == 0) 10f * scale else -10f * scale
            val r = zigRadius + rOffset
            val zx = bx + r * cos(a)
            val zy = by + (r * sinPitch) * sin(a)
            if (i == 0) zigPath.moveTo(zx, zy) else zigPath.lineTo(zx, zy)
        }
        zigPath.close()

        drawScope.drawPath(
            path = zigPath,
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.6f), accentColor.copy(alpha = 0.8f))
            ),
            style = Stroke(width = (3.5f * (scale / 2f)).coerceIn(2.5f, 8f))
        )

        // Glass coaster ring track inside the brim dish
        val trackR = HatPhysicsEngine.GLASS_TRACK_RADIUS * scale
        val trackRy = trackR * sinPitch
        drawScope.drawOval(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0x33FFFFFF),
                    Color(0x66FFFFFF),
                    Color(0x33FFFFFF)
                ),
                center = Offset(bx, by)
            ),
            topLeft = Offset(bx - trackR, by - trackRy),
            size = Size(trackR * 2f, trackRy * 2f),
            style = Stroke(
                width = (2.5f * (scale / 2f)).coerceIn(2f, 6f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * scale, 8f * scale), 0f)
            )
        )
    }

    private fun drawHatCrown(
        drawScope: DrawScope,
        centerX: Float,
        centerY: Float,
        rotationAngle: Float,
        hatTier: HatTier,
        scale: Float
    ) {
        val (crownBaseX, crownBaseY, _) = project3D(0f, 8f, 0f, centerX, centerY, scale)
        val crownRadius = 55f * scale
        val crownHeight = 98f * scale
        val crownRy = crownRadius * sinPitch

        val baseColor = Color(hatTier.primaryColor)
        val shadeColor = Color(hatTier.secondaryColor)
        val accentColor = Color(hatTier.accentColor)

        // Crown shadow
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x77000000), Color(0x00000000)),
                center = Offset(crownBaseX, crownBaseY),
                radius = crownRadius * 1.15f
            ),
            topLeft = Offset(crownBaseX - crownRadius * 1.15f, crownBaseY - crownRy * 1.15f),
            size = Size(crownRadius * 2.3f, crownRy * 2.3f)
        )

        // Egg-shaped tall Sombrero Crown
        val crownPath = Path().apply {
            moveTo(crownBaseX - crownRadius, crownBaseY)
            cubicTo(
                crownBaseX - crownRadius * 0.95f, crownBaseY - crownHeight * 0.45f,
                crownBaseX - crownRadius * 0.70f, crownBaseY - crownHeight * 0.90f,
                crownBaseX, crownBaseY - crownHeight
            )
            cubicTo(
                crownBaseX + crownRadius * 0.70f, crownBaseY - crownHeight * 0.90f,
                crownBaseX + crownRadius * 0.95f, crownBaseY - crownHeight * 0.45f,
                crownBaseX + crownRadius, crownBaseY
            )
            cubicTo(
                crownBaseX + crownRadius * 0.5f, crownBaseY + crownRy * 0.8f,
                crownBaseX - crownRadius * 0.5f, crownBaseY + crownRy * 0.8f,
                crownBaseX - crownRadius, crownBaseY
            )
            close()
        }

        // Metallic/glossy plastic gradient for the crown
        val crownBrush = Brush.linearGradient(
            colors = if (hatTier.isMetallic) {
                listOf(
                    Color(0xFFFFF9C4),
                    baseColor,
                    Color(0xFFFFD54F),
                    shadeColor,
                    Color(0xFFFFE082)
                )
            } else {
                listOf(
                    baseColor,
                    accentColor,
                    baseColor,
                    shadeColor
                )
            },
            start = Offset(crownBaseX - crownRadius, crownBaseY - crownHeight),
            end = Offset(crownBaseX + crownRadius * 0.8f, crownBaseY)
        )
        drawScope.drawPath(path = crownPath, brush = crownBrush)

        // Specular 3D Highlight curve on the crown
        val sheenPath = Path().apply {
            moveTo(crownBaseX - crownRadius * 0.35f, crownBaseY - crownHeight * 0.85f)
            cubicTo(
                crownBaseX - crownRadius * 0.45f, crownBaseY - crownHeight * 0.5f,
                crownBaseX - crownRadius * 0.45f, crownBaseY - crownHeight * 0.25f,
                crownBaseX - crownRadius * 0.30f, crownBaseY - crownHeight * 0.05f
            )
        }
        drawScope.drawPath(
            path = sheenPath,
            color = Color.White.copy(alpha = if (hatTier.isMetallic) 0.65f else 0.42f),
            style = Stroke(width = (8f * (scale / 2f)).coerceIn(4f, 16f), cap = StrokeCap.Round)
        )

        // Embossed Zig-Zag Bands around the Crown
        val numCrownZigs = 14
        val bandY1 = crownBaseY - crownHeight * 0.32f
        val bandY2 = crownBaseY - crownHeight * 0.62f

        for (bandY in listOf(bandY1, bandY2)) {
            val bandR = crownRadius * (1f - (crownBaseY - bandY) / (crownHeight * 1.4f))
            val bandRy = bandR * sinPitch
            val zigCrownPath = Path()
            for (i in 0..numCrownZigs) {
                val a = rotationAngle + (i.toFloat() / numCrownZigs) * 2f * PI.toFloat()
                val offset = if (i % 2 == 0) 5f * scale else -5f * scale
                val px = crownBaseX + bandR * cos(a)
                val py = bandY + bandRy * sin(a) + offset
                if (i == 0) zigCrownPath.moveTo(px, py) else zigCrownPath.lineTo(px, py)
            }
            drawScope.drawPath(
                path = zigCrownPath,
                color = Color.White.copy(alpha = 0.55f),
                style = Stroke(width = (3.5f * (scale / 2f)).coerceIn(2f, 7f), cap = StrokeCap.Round)
            )
        }

        // Sombrero Hat Band (Ribbon around the crown base)
        val bandBaseR = crownRadius * 1.02f
        val bandBaseRy = bandBaseR * sinPitch
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFE91E63), Color(0xFFFF5722), Color(0xFFFFC107)),
                start = Offset(crownBaseX - bandBaseR, crownBaseY),
                end = Offset(crownBaseX + bandBaseR, crownBaseY)
            ),
            topLeft = Offset(crownBaseX - bandBaseR, crownBaseY - bandBaseRy * 0.6f),
            size = Size(bandBaseR * 2f, bandBaseRy * 1.2f),
            style = Stroke(width = (7f * (scale / 2f)).coerceIn(4f, 15f))
        )
    }

    private fun drawShotGlass(
        drawScope: DrawScope,
        glass: Glass3D,
        scale: Float,
        isFront: Boolean
    ) {
        val sx = glass.screenX
        val sy = glass.screenY
        val sr = glass.screenRadius
        val sh = glass.screenHeight

        val glassRy = sr * sinPitch
        val glassTopY = sy - sh / 2f
        val glassBaseY = sy + sh / 2f

        // Coaster / Pedestal Base
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (glass.isAligned) Color(0x9900E5FF) else Color(0x33000000),
                    Color(0x00000000)
                ),
                center = Offset(sx, glassBaseY),
                radius = sr * 1.6f
            ),
            topLeft = Offset(sx - sr * 1.6f, glassBaseY - glassRy * 1.6f),
            size = Size(sr * 3.2f, glassRy * 3.2f)
        )

        // Glass Body Silhouette Path (Tapered heavy-bottom tumbler)
        val glassPath = Path().apply {
            moveTo(sx - sr, glassTopY)
            lineTo(sx - sr * 0.82f, glassBaseY)
            cubicTo(
                sx - sr * 0.4f, glassBaseY + glassRy * 0.8f,
                sx + sr * 0.4f, glassBaseY + glassRy * 0.8f,
                sx + sr * 0.82f, glassBaseY
            )
            lineTo(sx + sr, glassTopY)
            cubicTo(
                sx + sr * 0.5f, glassTopY - glassRy * 0.9f,
                sx - sr * 0.5f, glassTopY - glassRy * 0.9f,
                sx - sr, glassTopY
            )
            close()
        }

        // Transparent Glass Fill with Refraction Gradient
        val glassBrush = Brush.linearGradient(
            colors = listOf(
                Color(0x44FFFFFF),
                Color(0x18B2EBF2),
                Color(0x30E0F7FA),
                Color(0x55B2EBF2)
            ),
            start = Offset(sx - sr, glassTopY),
            end = Offset(sx + sr, glassBaseY)
        )
        drawScope.drawPath(path = glassPath, brush = glassBrush)

        // Heavy Glass Bottom (Solid glass base block)
        val baseThickness = sh * 0.22f
        val bottomBaseY = glassBaseY - baseThickness
        val bottomPath = Path().apply {
            moveTo(sx - sr * 0.85f, bottomBaseY)
            lineTo(sx - sr * 0.82f, glassBaseY)
            cubicTo(
                sx - sr * 0.4f, glassBaseY + glassRy * 0.8f,
                sx + sr * 0.4f, glassBaseY + glassRy * 0.8f,
                sx + sr * 0.82f, glassBaseY
            )
            lineTo(sx + sr * 0.85f, bottomBaseY)
            cubicTo(
                sx + sr * 0.4f, bottomBaseY + glassRy * 0.7f,
                sx - sr * 0.4f, bottomBaseY + glassRy * 0.7f,
                sx - sr * 0.85f, bottomBaseY
            )
            close()
        }
        drawScope.drawPath(
            path = bottomPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x33B2EBF2), Color(0x77E0F7FA), Color(0x99FFFFFF)),
                startY = bottomBaseY,
                endY = glassBaseY
            )
        )

        // Liquid Level (if any)
        if (glass.currentFill > 0f) {
            val fillRatio = glass.currentFill.coerceIn(0f, 1.25f)
            val fillHeight = sh * fillRatio
            val fluidTopY = glassBaseY - fillHeight
            val fluidRadius = (sr * 0.82f) + (sr * 0.18f) * fillRatio
            val fluidRy = glassRy * (0.82f + 0.18f * fillRatio)

            val fluidPath = Path().apply {
                moveTo(sx - fluidRadius, fluidTopY)
                lineTo(sx - sr * 0.82f, glassBaseY)
                cubicTo(
                    sx - sr * 0.4f, glassBaseY + glassRy * 0.8f,
                    sx + sr * 0.4f, glassBaseY + glassRy * 0.8f,
                    sx + sr * 0.82f, glassBaseY
                )
                lineTo(sx + fluidRadius, fluidTopY)
                cubicTo(
                    sx + fluidRadius * 0.5f, fluidTopY + fluidRy,
                    sx - fluidRadius * 0.5f, fluidTopY + fluidRy,
                    sx - fluidRadius, fluidTopY
                )
                close()
            }

            // Golden Schnapps / Tequila Liquid Gradient
            val isOverfilled = glass.currentFill > 1.0f
            val fluidBrush = Brush.verticalGradient(
                colors = if (isOverfilled) {
                    listOf(Color(0xFFFF1744), Color(0xFFD50000), Color(0xFFB71C1C))
                } else {
                    listOf(
                        Color(0xFFFFD54F),
                        Color(0xFFFFB300),
                        Color(0xFFFFA000),
                        Color(0xFFFF8F00)
                    )
                },
                startY = fluidTopY,
                endY = glassBaseY
            )
            drawScope.drawPath(path = fluidPath, brush = fluidBrush)

            // Liquid Meniscus (Top surface ellipse)
            drawScope.drawOval(
                brush = Brush.radialGradient(
                    colors = if (isOverfilled) {
                        listOf(Color(0xFFFF8A80), Color(0xFFFF1744))
                    } else {
                        listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00))
                    },
                    center = Offset(sx, fluidTopY),
                    radius = fluidRadius
                ),
                topLeft = Offset(sx - fluidRadius, fluidTopY - fluidRy),
                size = Size(fluidRadius * 2f, fluidRy * 2f)
            )

            // Liquid highlight reflection line
            drawScope.drawOval(
                color = Color.White.copy(alpha = 0.65f),
                topLeft = Offset(sx - fluidRadius * 0.7f, fluidTopY - fluidRy * 0.6f),
                size = Size(fluidRadius * 1.4f, fluidRy * 1.2f),
                style = Stroke(width = (2f * (scale / 2f)).coerceIn(1.5f, 4f))
            )
        }

        // Dashed Target Fill Line (Füllstrich) - High-visibility neon line
        val targetY = glassBaseY - (sh * glass.targetFill)
        val targetWidth = (sr * 0.85f) + (sr * 0.15f) * glass.targetFill
        val targetRy = glassRy * (0.85f + 0.15f * glass.targetFill)

        val strokeW = (3.5f * (scale / 2f)).coerceIn(2.8f, 8f)
        val dashOn = (8f * (scale / 2f)).coerceIn(7f, 20f)
        val dashOff = (5f * (scale / 2f)).coerceIn(4f, 13f)

        // Draw bright glow for the target line
        drawScope.drawOval(
            color = Color(0x77FFFF00),
            topLeft = Offset(sx - targetWidth - 2.5f, targetY - targetRy - 2.5f),
            size = Size((targetWidth + 2.5f) * 2f, (targetRy + 2.5f) * 2f),
            style = Stroke(width = strokeW + 2.5f)
        )

        drawScope.drawOval(
            color = Color(0xFFFFEB3B),
            topLeft = Offset(sx - targetWidth, targetY - targetRy),
            size = Size(targetWidth * 2f, targetRy * 2f),
            style = Stroke(
                width = strokeW,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
            )
        )

        // Highlighting for the active aligned glass in front
        if (isFront && glass.isAligned) {
            drawScope.drawOval(
                color = Color(0x9900E5FF),
                topLeft = Offset(sx - sr * 1.25f, glassTopY - glassRy * 1.25f),
                size = Size(sr * 2.5f, glassRy * 2.5f),
                style = Stroke(width = (4.0f * (scale / 2f)).coerceIn(3.5f, 9f))
            )
        }

        // Glass Rim Edge Outline & Reflection
        drawScope.drawPath(
            path = glassPath,
            color = Color(0xCCB2EBF2),
            style = Stroke(width = (2.2f * (scale / 2f)).coerceIn(2.0f, 6f))
        )

        // Top Opening Rim
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.90f), Color(0x88B2EBF2), Color.White.copy(alpha = 0.55f)),
                start = Offset(sx - sr, glassTopY),
                end = Offset(sx + sr, glassTopY)
            ),
            topLeft = Offset(sx - sr, glassTopY - glassRy),
            size = Size(sr * 2f, glassRy * 2f),
            style = Stroke(width = (3.0f * (scale / 2f)).coerceIn(2.5f, 7f))
        )

        // Fill Status Badge / Indicator when glass is finished
        if (glass.isFinished) {
            val badgeColor = when (glass.status) {
                GlassStatus.PERFECT -> Color(0xFFFFD700)
                GlassStatus.GOOD -> Color(0xFF00E676)
                GlassStatus.UNDERFILLED -> Color(0xFFFF9100)
                GlassStatus.OVERFLOW -> Color(0xFFFF1744)
                else -> Color.Transparent
            }
            val badgeRadius = (10f * (scale / 2f)).coerceIn(9f, 24f)
            drawScope.drawCircle(
                color = badgeColor,
                radius = badgeRadius,
                center = Offset(sx, glassTopY - badgeRadius * 1.8f)
            )
            drawScope.drawCircle(
                color = Color.White,
                radius = badgeRadius - 1.5f,
                center = Offset(sx, glassTopY - badgeRadius * 1.8f),
                style = Stroke(width = (2.5f * (scale / 2f)).coerceIn(2.2f, 6f))
            )
        }
    }

    private fun drawCollisionFeedback(
        drawScope: DrawScope,
        physics: HatPhysicsEngine,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        val (spotX, spotY, _) = project3D(
            HatPhysicsEngine.NOZZLE_X,
            HatPhysicsEngine.RIM_PLANE_Y,
            HatPhysicsEngine.NOZZLE_Z,
            centerX,
            centerY,
            scale
        )
        val collision = physics.lastCollisionResult
        val reticleRadius = 32f * scale
        val reticleRy = reticleRadius * sinPitch

        val reticleColor = when {
            collision.isDirectHit -> Color(0xFF00E676) // Bright green hit!
            collision.isRimHit -> Color(0xFFFF9100)    // Orange rim graze
            collision.isSpill && collision.isPouring -> Color(0xFFFF1744) // Red spill
            else -> Color(0x88FFFFFF) // Alignment target
        }

        // Pulsing Reticle Ring
        val ringWidth = if (collision.isDirectHit)
            (6f * (scale / 2f)).coerceIn(5f, 14f)
        else
            (3.5f * (scale / 2f)).coerceIn(3.0f, 9f)

        val dash1 = (9f * (scale / 2f)).coerceIn(8f, 22f)
        val dash2 = (6f * (scale / 2f)).coerceIn(5f, 15f)

        drawScope.drawOval(
            color = reticleColor,
            topLeft = Offset(spotX - reticleRadius, spotY - reticleRy),
            size = Size(reticleRadius * 2f, reticleRy * 2f),
            style = Stroke(
                width = ringWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash1, dash2), 0f)
            )
        )

        // Center crosshair / alignment dot
        drawScope.drawCircle(
            color = reticleColor,
            radius = (5.0f * (scale / 2f)).coerceIn(4.5f, 12f),
            center = Offset(spotX, spotY)
        )
    }

    private fun drawLiquidStream(
        drawScope: DrawScope,
        physics: HatPhysicsEngine,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        val collision = physics.lastCollisionResult
        if (!collision.isPouring && physics.bottleTiltAngle < 15f) return

        val (nozzleX, nozzleY, _) = project3D(
            HatPhysicsEngine.NOZZLE_X,
            HatPhysicsEngine.NOZZLE_Y,
            HatPhysicsEngine.NOZZLE_Z,
            centerX,
            centerY,
            scale
        )
        val (targetX, targetY, _) = project3D(
            collision.streamImpactX,
            collision.streamImpactY,
            collision.streamImpactZ,
            centerX,
            centerY,
            scale
        )

        // Draw Fluid Curve Stream
        val streamPath = Path().apply {
            moveTo(nozzleX, nozzleY)
            // Quadratic/cubic arc simulating fluid gravity path
            cubicTo(
                nozzleX, nozzleY + (targetY - nozzleY) * 0.4f,
                targetX + (nozzleX - targetX) * 0.2f, nozzleY + (targetY - nozzleY) * 0.7f,
                targetX, targetY
            )
        }

        val outerStreamWidth = (8.5f * (scale / 2f)).coerceIn(6f, 24f)
        val innerStreamWidth = (3.5f * (scale / 2f)).coerceIn(3.0f, 12f)

        // Stream Outer Glow
        drawScope.drawPath(
            path = streamPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFD54F), Color(0xFFFFB300)),
                startY = nozzleY,
                endY = targetY
            ),
            style = Stroke(width = outerStreamWidth, cap = StrokeCap.Round)
        )

        // Stream Core Highlight
        drawScope.drawPath(
            path = streamPath,
            color = Color(0xFFFFF9C4),
            style = Stroke(width = innerStreamWidth, cap = StrokeCap.Round)
        )

        // 1. Bottom Splash Contact Ripples on the glass bottom / liquid surface
        for (ripple in physics.bottomSplashRipples) {
            val (rx, ry, _) = project3D(ripple.x, ripple.y, ripple.z, centerX, centerY, scale)
            val rWidth = ripple.radius * scale
            val rHeight = rWidth * sinPitch
            if (rWidth > 1f && ripple.alpha > 0.02f) {
                // Expanding outer ripple ring
                drawScope.drawOval(
                    color = Color(ripple.color).copy(alpha = (ripple.alpha * 0.70f).coerceIn(0f, 1f)),
                    topLeft = Offset(rx - rWidth, ry - rHeight),
                    size = Size(rWidth * 2f, rHeight * 2f),
                    style = Stroke(width = (2.2f * (scale / 2f)).coerceIn(1.8f, 5.5f))
                )
                // Inner concentric wave
                val innerW = rWidth * 0.55f
                val innerH = rHeight * 0.55f
                drawScope.drawOval(
                    color = Color.White.copy(alpha = (ripple.alpha * 0.40f).coerceIn(0f, 1f)),
                    topLeft = Offset(rx - innerW, ry - innerH),
                    size = Size(innerW * 2f, innerH * 2f),
                    style = Stroke(width = (1.5f * (scale / 2f)).coerceIn(1.2f, 3.5f))
                )
            }
        }

        // 2. Stream Particles & Droplets
        for (d in physics.streamDroplets) {
            val (dx, dy, _) = project3D(d.x, d.y, d.z, centerX, centerY, scale)
            val dropRadius = (d.radius * (scale / 2f)).coerceIn(3.0f, 14f)
            drawScope.drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = d.alpha),
                radius = dropRadius,
                center = Offset(dx, dy)
            )
        }

        // 3. Splash Particles (Glass bottom contact bursts, rim splashes, spill droplets)
        for (p in physics.splashParticles) {
            val (px, py, _) = project3D(p.x, p.y, p.z, centerX, centerY, scale)
            val splashRadius = (p.size * (scale / 2f)).coerceIn(2.5f, 16f)

            if (p.isGlassBottom) {
                // Soft droplet glow / ambient mist
                drawScope.drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = p.alpha * 0.40f),
                    radius = splashRadius * 1.55f,
                    center = Offset(px, py)
                )

                // Liquid droplet body (tinted with schnapps color)
                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = p.alpha * 0.95f),
                            Color(p.color).copy(alpha = p.alpha)
                        ),
                        center = Offset(px - splashRadius * 0.25f, py - splashRadius * 0.25f),
                        radius = splashRadius
                    ),
                    radius = splashRadius,
                    center = Offset(px, py)
                )

                // Crisp specular glint
                drawScope.drawCircle(
                    color = Color.White.copy(alpha = p.alpha * 0.9f),
                    radius = (splashRadius * 0.35f).coerceAtLeast(1.2f),
                    center = Offset(px - splashRadius * 0.28f, py - splashRadius * 0.28f)
                )

                // Sparkling star glint on energetic droplets
                if (p.sparkle && p.alpha > 0.35f) {
                    val glintLen = splashRadius * 1.4f
                    val glintStroke = (1.5f * (scale / 2f)).coerceIn(1.2f, 3f)
                    val glintColor = Color.White.copy(alpha = p.alpha * 0.8f)
                    // Horizontal ray
                    drawScope.drawLine(
                        color = glintColor,
                        start = Offset(px - glintLen, py),
                        end = Offset(px + glintLen, py),
                        strokeWidth = glintStroke,
                        cap = StrokeCap.Round
                    )
                    // Vertical ray
                    drawScope.drawLine(
                        color = glintColor,
                        start = Offset(px, py - glintLen),
                        end = Offset(px, py + glintLen),
                        strokeWidth = glintStroke,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                // Standard rim or spill particle
                drawScope.drawCircle(
                    color = Color(p.color).copy(alpha = p.alpha),
                    radius = splashRadius,
                    center = Offset(px, py)
                )
            }
        }
    }

    private fun drawSuspendedBottle(
        drawScope: DrawScope,
        physics: HatPhysicsEngine,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        val (nozzleX, nozzleY, _) = project3D(
            HatPhysicsEngine.NOZZLE_X,
            HatPhysicsEngine.NOZZLE_Y,
            HatPhysicsEngine.NOZZLE_Z,
            centerX,
            centerY,
            scale
        )

        val tilt = physics.bottleTiltAngle
        // Draw schnapps bottle pointing downwards at the nozzle, body extending upwards
        drawScope.rotate(degrees = -tilt, pivot = Offset(nozzleX, nozzleY)) {
            val neckWidth = 12f * scale
            val neckHeight = 28f * scale
            val bWidth = 32f * scale
            val bHeight = 84f * scale

            // 1. Spout / Pouring lip at the nozzle
            drawScope.drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFFFA000)),
                    start = Offset(nozzleX - neckWidth / 2f, nozzleY - 4f * scale),
                    end = Offset(nozzleX + neckWidth / 2f, nozzleY)
                ),
                topLeft = Offset(nozzleX - neckWidth / 2f, nozzleY - 4f * scale),
                size = Size(neckWidth, 5f * scale)
            )

            // 2. Bottle Neck extending upwards from nozzle
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF43A047)),
                    start = Offset(nozzleX - neckWidth / 2f, nozzleY - neckHeight),
                    end = Offset(nozzleX + neckWidth / 2f, nozzleY)
                ),
                topLeft = Offset(nozzleX - neckWidth / 2f, nozzleY - neckHeight),
                size = Size(neckWidth, neckHeight)
            )

            // 3. Bottle Shoulder taper
            val shoulderTopY = nozzleY - neckHeight - 16f * scale
            val shoulderPath = Path().apply {
                moveTo(nozzleX - neckWidth / 2f, nozzleY - neckHeight)
                lineTo(nozzleX - bWidth / 2f, shoulderTopY)
                lineTo(nozzleX + bWidth / 2f, shoulderTopY)
                lineTo(nozzleX + neckWidth / 2f, nozzleY - neckHeight)
                close()
            }
            drawScope.drawPath(
                path = shoulderPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF43A047)),
                    start = Offset(nozzleX - bWidth / 2f, shoulderTopY),
                    end = Offset(nozzleX + bWidth / 2f, nozzleY - neckHeight)
                )
            )

            // 4. Bottle Main Body extending further up
            val bodyTopY = shoulderTopY - bHeight
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047), Color(0xFF1B5E20)),
                    start = Offset(nozzleX - bWidth / 2f, bodyTopY),
                    end = Offset(nozzleX + bWidth / 2f, shoulderTopY)
                ),
                topLeft = Offset(nozzleX - bWidth / 2f, bodyTopY),
                size = Size(bWidth, bHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale, 6f * scale)
            )

            // 5. Bottle Festive Label ("PARTY SCHNAPS")
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3), Color(0xFFFFE082))
                ),
                topLeft = Offset(nozzleX - bWidth * 0.44f, bodyTopY + bHeight * 0.22f),
                size = Size(bWidth * 0.88f, bHeight * 0.50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale)
            )
            // Label Border
            drawRoundRect(
                color = Color(0xFFFFB300),
                topLeft = Offset(nozzleX - bWidth * 0.44f, bodyTopY + bHeight * 0.22f),
                size = Size(bWidth * 0.88f, bHeight * 0.50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale),
                style = Stroke(width = (1.5f * scale).coerceAtLeast(1.5f))
            )

            // 6. Bottle Highlight Sheen (Glossy reflection stripe)
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(nozzleX - bWidth * 0.35f, bodyTopY + 4f * scale),
                size = Size(4f * scale, bHeight + 14f * scale)
            )
        }
    }
}
