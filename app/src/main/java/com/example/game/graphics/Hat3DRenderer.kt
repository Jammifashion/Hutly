package com.example.game.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.game.physics.Glass3D
import com.example.game.physics.GlassStatus
import com.example.game.physics.HatPhysicsEngine
import com.example.state.HatTier
import kotlin.math.*

enum class CameraPreset(
    val pitchDeg: Float,
    val cameraDistance: Float,
    val focalLength: Float,
    val zoomMultiplier: Float,
    val verticalCenterOffset: Float
) {
    CLOSE_UP(
        pitchDeg = 48f,
        cameraDistance = 380f,
        focalLength = 540f,
        zoomMultiplier = 1.14f,
        verticalCenterOffset = 0.54f
    ),
    OVERVIEW(
        pitchDeg = 54f,
        cameraDistance = 450f,
        focalLength = 500f,
        zoomMultiplier = 0.94f,
        verticalCenterOffset = 0.56f
    ),
    ACTION_CAM(
        pitchDeg = 38f,
        cameraDistance = 340f,
        focalLength = 560f,
        zoomMultiplier = 1.25f,
        verticalCenterOffset = 0.52f
    )
}

/**
 * High-performance, high-fidelity 3D Renderer for "Hut ist gut".
 * Features:
 * - Pre-allocated, reusable Paths and cached rendering structures for zero per-frame GC churn.
 * - Back-to-front depth sorting (Z-ordering) for crown and all 11 glasses with atmospheric depth fog.
 * - Unified top-left directional lighting with consistent specular highlights on crown, brim, glasses, bottle.
 * - Soft sombrero drop shadow on table and contact shadows under glasses on the brim dish.
 * - Injection-molded party plastic appearance with bold top glossy streak and embossed relief zigzag bands.
 * - Dynamic liquid surface sloshing responsive to turntable rotation.
 * - Luminous pulsating target fill line as liquid approaches perfection.
 * - Specular edge highlights and golden flash effects for perfect pours.
 */
class Hat3DRenderer {

    // Current camera parameters
    private var pitchRad = 44.0f * (PI.toFloat() / 180f)
    private var cosPitch = cos(pitchRad)
    private var sinPitch = sin(pitchRad)
    private var cameraDistance = 400f
    private var cameraFocalLength = 520f

    // Reusable Path objects to prevent per-frame heap allocations
    private val brimZigPath = Path()
    private val brimShadowPath = Path()
    private val crownPath = Path()
    private val crownSheenPath = Path()
    private val crownTopGlintPath = Path()
    private val crownZigPath = Path()
    private val crownZigDarkPath = Path()
    private val glassPath = Path()
    private val glassBottomPath = Path()
    private val fluidPath = Path()
    private val streamPath = Path()
    private val bottleShoulderPath = Path()

    // Precomputed reusable DashPathEffects
    private var cachedDashEffectTrack: PathEffect? = null
    private var cachedDashTrackScale = 0f

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

        // Calculate dynamic adaptive scene scale so the party hat fills ~94% of available width
        val brimWorldDiameter = HatPhysicsEngine.BRIM_RADIUS * 2f // 290 units
        val scaleByWidth = (width * 0.94f) / brimWorldDiameter
        val scaleByHeight = (height * 0.86f) / 250f
        val baseUnitScale = min(scaleByWidth, scaleByHeight).coerceAtLeast(0.45f) * cameraPreset.zoomMultiplier
        val effectiveScale = baseUnitScale * zoom

        val centerX = width / 2f
        val centerY = height * cameraPreset.verticalCenterOffset

        // 1. Draw Turntable Base & Soft Hat Drop Shadow
        drawTurntableAndHatShadow(drawScope, centerX, centerY, effectiveScale)

        // 2. Project all 11 glasses and crown to determine precise Z-depth sorting
        val (crownBaseX, crownBaseY, crownDepthZ) = project3D(0f, 8f, 0f, centerX, centerY, effectiveScale)

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

        // 3. Draw Hat Brim Dish & Coaster Track
        drawHatBrim(drawScope, centerX, centerY, physics.currentRotationAngle, hatTier, effectiveScale)

        // 4. Sort all glasses strictly by depth Z (back to front)
        val sortedGlasses = physics.glasses.sortedBy { it.depthZ }
        val backGlasses = sortedGlasses.filter { it.depthZ < crownDepthZ }
        val frontGlasses = sortedGlasses.filter { it.depthZ >= crownDepthZ }

        // Draw small contact shadows under back glasses onto the brim
        for (glass in backGlasses) {
            drawGlassContactShadow(drawScope, glass, effectiveScale)
        }

        // 5. Draw Background Glasses (Depth Fog: slightly darker & subtly receded)
        for (glass in backGlasses) {
            drawShotGlass(drawScope, glass, physics, effectiveScale, isFront = false)
        }

        // 6. Draw Central Sombrero Crown (Molded Plastic with top glossy streak & relief zigzags)
        drawHatCrown(drawScope, crownBaseX, crownBaseY, physics.currentRotationAngle, hatTier, effectiveScale)

        // Draw contact shadows under front glasses onto the brim
        for (glass in frontGlasses) {
            drawGlassContactShadow(drawScope, glass, effectiveScale)
        }

        // 7. Draw Foreground Glasses (Crisp, full light, specular edge rim, sloshing meniscus)
        for (glass in frontGlasses) {
            drawShotGlass(drawScope, glass, physics, effectiveScale, isFront = true)
        }

        // 8. Draw Active Collision Reticle & Alignment Feedback on the active front spot
        drawCollisionFeedback(drawScope, physics, centerX, centerY, effectiveScale)

        // 9. Draw Liquid Stream and Splash Droplets/Ripples
        drawLiquidStream(drawScope, physics, centerX, centerY, effectiveScale)

        // 10. Draw Suspended Bottle at the top (with top-left highlight)
        drawSuspendedBottle(drawScope, physics, centerX, centerY, effectiveScale)
    }

    private fun drawTurntableAndHatShadow(
        drawScope: DrawScope,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        val (bx, by, _) = project3D(0f, -22f, 0f, centerX, centerY, scale)
        val rx = 182f * scale
        val ry = rx * sinPitch * 1.05f

        // Soft Hat Drop Shadow cast onto the turntable/table (offset slightly down-right, away from top-left light)
        val shadowOffsetX = 12f * scale
        val shadowOffsetY = 20f * scale
        val brimShadowRadius = HatPhysicsEngine.BRIM_RADIUS * scale * 1.05f
        val brimShadowRy = brimShadowRadius * sinPitch * 1.05f

        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x99000000),
                    Color(0x55000000),
                    Color(0x00000000)
                ),
                center = Offset(bx + shadowOffsetX, by + shadowOffsetY),
                radius = brimShadowRadius
            ),
            topLeft = Offset(bx + shadowOffsetX - brimShadowRadius, by + shadowOffsetY - brimShadowRy),
            size = Size(brimShadowRadius * 2f, brimShadowRy * 2f)
        )

        // Turntable base shadow
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x77000000), Color(0x00000000)),
                center = Offset(bx, by + 12f * scale),
                radius = rx * 1.25f
            ),
            topLeft = Offset(bx - rx * 1.25f, by + 12f * scale - ry * 1.25f),
            size = Size(rx * 2.5f, ry * 2.5f)
        )

        // Turntable metallic rim with top-left directional lighting
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF546E7A), // Highlight top-left
                    Color(0xFF37474F),
                    Color(0xFF212121),
                    Color(0xFF263238)
                ),
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

        // Outer Sombrero Brim Dish (Deep rich gradient with top-left key lighting)
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0.98f),
                    shadeColor.copy(alpha = 0.98f),
                    if (hatTier.isMetallic) Color(0xFFFFE082) else accentColor.copy(alpha = 0.92f)
                ),
                // Light source top-left: center of radial light shifted top-left
                center = Offset(bx - 35f * scale, by - 28f * scale),
                radius = outerR * 1.15f
            ),
            topLeft = Offset(bx - outerR, by - outerRy),
            size = Size(outerR * 2f, outerRy * 2f)
        )

        // Brim Outer Lip Highlight (Molded shiny plastic rim with strong top-left gleam)
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.88f), // Strong top-left highlight
                    Color.White.copy(alpha = 0.55f),
                    accentColor,
                    shadeColor.copy(alpha = 0.95f),
                    Color.Black.copy(alpha = 0.35f)
                ),
                start = Offset(bx - outerR, by - outerRy),
                end = Offset(bx + outerR, by + outerRy)
            ),
            topLeft = Offset(bx - outerR, by - outerRy),
            size = Size(outerR * 2f, outerRy * 2f),
            style = Stroke(width = (6.0f * (scale / 2f)).coerceIn(3.5f, 14f))
        )

        // Embossed Festive Zig-Zag pattern on brim (Tactile plastic relief: dark shadow line + light highlight line)
        brimZigPath.reset()
        val numZigs = 22
        val zigRadius = outerR * 0.82f
        for (i in 0..numZigs) {
            val a = rotationAngle + (i.toFloat() / numZigs) * 2f * PI.toFloat()
            val rOffset = if (i % 2 == 0) 9.5f * scale else -9.5f * scale
            val r = zigRadius + rOffset
            val zx = bx + r * cos(a)
            val zy = by + (r * sinPitch) * sin(a)
            if (i == 0) brimZigPath.moveTo(zx, zy) else brimZigPath.lineTo(zx, zy)
        }
        brimZigPath.close()

        val strokeW = (3.2f * (scale / 2f)).coerceIn(2.2f, 7.5f)

        // 1. Embossed dark relief line (offset +1.5px down-right)
        drawScope.drawPath(
            path = brimZigPath,
            color = Color.Black.copy(alpha = 0.35f),
            style = Stroke(width = strokeW)
        )

        // 2. Embossed bright highlight line (crisp shine)
        drawScope.drawPath(
            path = brimZigPath,
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.75f), accentColor.copy(alpha = 0.85f)),
                start = Offset(bx - outerR, by - outerRy),
                end = Offset(bx + outerR, by + outerRy)
            ),
            style = Stroke(width = strokeW * 0.85f)
        )

        // Glass coaster ring track inside the brim dish
        val trackR = HatPhysicsEngine.GLASS_TRACK_RADIUS * scale
        val trackRy = trackR * sinPitch

        if (cachedDashEffectTrack == null || cachedDashTrackScale != scale) {
            cachedDashTrackScale = scale
            cachedDashEffectTrack = PathEffect.dashPathEffect(floatArrayOf(12f * scale, 8f * scale), 0f)
        }

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
                pathEffect = cachedDashEffectTrack
            )
        )
    }

    private fun drawGlassContactShadow(drawScope: DrawScope, glass: Glass3D, scale: Float) {
        val sx = glass.screenX
        val sy = glass.screenY
        val sr = glass.screenRadius
        val sh = glass.screenHeight
        val glassBaseY = sy + sh / 2f
        val glassRy = sr * sinPitch

        // Small soft contact shadow under glass, offset slightly down-right (+2.5f, +3.5f) away from top-left light
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x77000000), Color(0x33000000), Color(0x00000000)),
                center = Offset(sx + 2.5f * scale, glassBaseY + 3.5f * scale),
                radius = sr * 1.3f
            ),
            topLeft = Offset(sx + 2.5f * scale - sr * 1.3f, glassBaseY + 3.5f * scale - glassRy * 1.3f),
            size = Size(sr * 2.6f, glassRy * 2.6f)
        )
    }

    private fun drawHatCrown(
        drawScope: DrawScope,
        crownBaseX: Float,
        crownBaseY: Float,
        rotationAngle: Float,
        hatTier: HatTier,
        scale: Float
    ) {
        val crownRadius = 55f * scale
        val crownHeight = 98f * scale
        val crownRy = crownRadius * sinPitch

        val baseColor = Color(hatTier.primaryColor)
        val shadeColor = Color(hatTier.secondaryColor)
        val accentColor = Color(hatTier.accentColor)

        // Crown shadow
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x88000000), Color(0x00000000)),
                center = Offset(crownBaseX + 4f * scale, crownBaseY + 4f * scale),
                radius = crownRadius * 1.15f
            ),
            topLeft = Offset(crownBaseX - crownRadius * 1.15f, crownBaseY - crownRy * 1.15f),
            size = Size(crownRadius * 2.3f, crownRy * 2.3f)
        )

        // Egg-shaped tall Sombrero Crown
        crownPath.reset()
        crownPath.moveTo(crownBaseX - crownRadius, crownBaseY)
        crownPath.cubicTo(
            crownBaseX - crownRadius * 0.95f, crownBaseY - crownHeight * 0.45f,
            crownBaseX - crownRadius * 0.70f, crownBaseY - crownHeight * 0.90f,
            crownBaseX, crownBaseY - crownHeight
        )
        crownPath.cubicTo(
            crownBaseX + crownRadius * 0.70f, crownBaseY - crownHeight * 0.90f,
            crownBaseX + crownRadius * 0.95f, crownBaseY - crownHeight * 0.45f,
            crownBaseX + crownRadius, crownBaseY
        )
        crownPath.cubicTo(
            crownBaseX + crownRadius * 0.5f, crownBaseY + crownRy * 0.8f,
            crownBaseX - crownRadius * 0.5f, crownBaseY + crownRy * 0.8f,
            crownBaseX - crownRadius, crownBaseY
        )
        crownPath.close()

        // Molded plastic gradient with top-left illumination
        val crownBrush = Brush.linearGradient(
            colors = if (hatTier.isMetallic) {
                listOf(
                    Color(0xFFFFF9C4), // Top-left gleaming highlight
                    baseColor,
                    Color(0xFFFFD54F),
                    shadeColor,
                    Color(0xFF4E342E)  // Shadow on bottom-right
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.85f), // Shiny plastic key light
                    baseColor,
                    accentColor,
                    shadeColor,
                    Color.Black.copy(alpha = 0.35f) // Shadow
                )
            },
            start = Offset(crownBaseX - crownRadius * 1.1f, crownBaseY - crownHeight * 1.1f),
            end = Offset(crownBaseX + crownRadius * 0.9f, crownBaseY + crownRy * 0.5f)
        )
        drawScope.drawPath(path = crownPath, brush = crownBrush)

        // Plastik-Look: Kräftiger Glanzstreifen oben auf der Krone (Bold top glossy streak)
        crownTopGlintPath.reset()
        crownTopGlintPath.moveTo(crownBaseX - crownRadius * 0.55f, crownBaseY - crownHeight * 0.92f)
        crownTopGlintPath.cubicTo(
            crownBaseX - crownRadius * 0.30f, crownBaseY - crownHeight * 0.99f,
            crownBaseX + crownRadius * 0.20f, crownBaseY - crownHeight * 0.97f,
            crownBaseX + crownRadius * 0.45f, crownBaseY - crownHeight * 0.90f
        )
        drawScope.drawPath(
            path = crownTopGlintPath,
            color = Color.White.copy(alpha = 0.85f),
            style = Stroke(width = (6.5f * (scale / 2f)).coerceIn(4f, 13f), cap = StrokeCap.Round)
        )

        // Top-left shoulder specular curve
        crownSheenPath.reset()
        crownSheenPath.moveTo(crownBaseX - crownRadius * 0.38f, crownBaseY - crownHeight * 0.82f)
        crownSheenPath.cubicTo(
            crownBaseX - crownRadius * 0.46f, crownBaseY - crownHeight * 0.52f,
            crownBaseX - crownRadius * 0.45f, crownBaseY - crownHeight * 0.28f,
            crownBaseX - crownRadius * 0.32f, crownBaseY - crownHeight * 0.06f
        )
        drawScope.drawPath(
            path = crownSheenPath,
            color = Color.White.copy(alpha = if (hatTier.isMetallic) 0.70f else 0.48f),
            style = Stroke(width = (7f * (scale / 2f)).coerceIn(3.5f, 14f), cap = StrokeCap.Round)
        )

        // Plastik-Look: Zickzack-Prägung als dezente Hell-Dunkel-Relieflinien
        val numCrownZigs = 14
        val bandY1 = crownBaseY - crownHeight * 0.32f
        val bandY2 = crownBaseY - crownHeight * 0.62f

        for (bandY in listOf(bandY1, bandY2)) {
            val bandR = crownRadius * (1f - (crownBaseY - bandY) / (crownHeight * 1.4f))
            val bandRy = bandR * sinPitch

            crownZigPath.reset()
            crownZigDarkPath.reset()

            for (i in 0..numCrownZigs) {
                val a = rotationAngle + (i.toFloat() / numCrownZigs) * 2f * PI.toFloat()
                val offset = if (i % 2 == 0) 4.5f * scale else -4.5f * scale
                val px = crownBaseX + bandR * cos(a)
                val py = bandY + bandRy * sin(a) + offset

                if (i == 0) {
                    crownZigPath.moveTo(px, py)
                    crownZigDarkPath.moveTo(px + 1.2f, py + 1.2f)
                } else {
                    crownZigPath.lineTo(px, py)
                    crownZigDarkPath.lineTo(px + 1.2f, py + 1.2f)
                }
            }

            val reliefW = (2.8f * (scale / 2f)).coerceIn(1.8f, 6f)

            // Dark embossed shadow line
            drawScope.drawPath(
                path = crownZigDarkPath,
                color = Color.Black.copy(alpha = 0.35f),
                style = Stroke(width = reliefW, cap = StrokeCap.Round)
            )

            // Bright embossed highlight line
            drawScope.drawPath(
                path = crownZigPath,
                color = Color.White.copy(alpha = 0.62f),
                style = Stroke(width = reliefW * 0.85f, cap = StrokeCap.Round)
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
        physics: HatPhysicsEngine,
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

        // Depth Fog calculation: glasses deeper in Z (behind) are slightly darker and minimally smaller
        val depthFactor = ((glass.depthZ + 120f) / 240f).coerceIn(0.58f, 1.0f)
        val fogAlpha = if (isFront) 1.0f else (0.80f + 0.20f * depthFactor)

        // Glass Body Silhouette Path (Tapered heavy-bottom tumbler)
        glassPath.reset()
        glassPath.moveTo(sx - sr, glassTopY)
        glassPath.lineTo(sx - sr * 0.82f, glassBaseY)
        glassPath.cubicTo(
            sx - sr * 0.4f, glassBaseY + glassRy * 0.8f,
            sx + sr * 0.4f, glassBaseY + glassRy * 0.8f,
            sx + sr * 0.82f, glassBaseY
        )
        glassPath.lineTo(sx + sr, glassTopY)
        glassPath.cubicTo(
            sx + sr * 0.5f, glassTopY - glassRy * 0.9f,
            sx - sr * 0.5f, glassTopY - glassRy * 0.9f,
            sx - sr, glassTopY
        )
        glassPath.close()

        // Transparent Glass Fill with Refraction Gradient & top-left light influence
        val glassBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f * fogAlpha), // Top-left specular entry
                Color(0x18B2EBF2).copy(alpha = 0.25f * fogAlpha),
                Color(0x30E0F7FA).copy(alpha = 0.35f * fogAlpha),
                Color(0x55B2EBF2).copy(alpha = 0.45f * fogAlpha)
            ),
            start = Offset(sx - sr, glassTopY),
            end = Offset(sx + sr, glassBaseY)
        )
        drawScope.drawPath(path = glassPath, brush = glassBrush)

        // Heavy Glass Bottom Block
        val baseThickness = sh * 0.22f
        val bottomBaseY = glassBaseY - baseThickness
        glassBottomPath.reset()
        glassBottomPath.moveTo(sx - sr * 0.85f, bottomBaseY)
        glassBottomPath.lineTo(sx - sr * 0.82f, glassBaseY)
        glassBottomPath.cubicTo(
            sx - sr * 0.4f, glassBaseY + glassRy * 0.8f,
            sx + sr * 0.4f, glassBaseY + glassRy * 0.8f,
            sx + sr * 0.82f, glassBaseY
        )
        glassBottomPath.lineTo(sx + sr * 0.85f, bottomBaseY)
        glassBottomPath.cubicTo(
            sx + sr * 0.4f, bottomBaseY + glassRy * 0.7f,
            sx - sr * 0.4f, bottomBaseY + glassRy * 0.7f,
            sx - sr * 0.85f, bottomBaseY
        )
        glassBottomPath.close()

        drawScope.drawPath(
            path = glassBottomPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x33B2EBF2).copy(alpha = 0.35f * fogAlpha),
                    Color(0x77E0F7FA).copy(alpha = 0.65f * fogAlpha),
                    Color.White.copy(alpha = 0.90f * fogAlpha)
                ),
                startY = bottomBaseY,
                endY = glassBaseY
            )
        )

        // Liquid Level (with responsive rotation sloshing!)
        if (glass.currentFill > 0f) {
            val fillRatio = glass.currentFill.coerceIn(0f, 1.25f)
            val fillHeight = sh * fillRatio

            // Dynamic Meniscus Sloshing when hat rotates:
            val sloshAngle = physics.currentRotationAngle * 2.8f + glass.index * 0.85f
            val sloshOffset = sin(sloshAngle) * (2.8f * (scale / 2f))

            val fluidTopY = (glassBaseY - fillHeight) + sloshOffset
            val fluidRadius = (sr * 0.82f) + (sr * 0.18f) * fillRatio
            val fluidRy = glassRy * (0.82f + 0.18f * fillRatio)

            fluidPath.reset()
            fluidPath.moveTo(sx - fluidRadius, fluidTopY)
            fluidPath.lineTo(sx - sr * 0.82f, glassBaseY)
            fluidPath.cubicTo(
                sx - sr * 0.4f, glassBaseY + glassRy * 0.8f,
                sx + sr * 0.4f, glassBaseY + glassRy * 0.8f,
                sx + sr * 0.82f, glassBaseY
            )
            fluidPath.lineTo(sx + fluidRadius, fluidTopY)
            fluidPath.cubicTo(
                sx + fluidRadius * 0.5f, fluidTopY + fluidRy,
                sx - fluidRadius * 0.5f, fluidTopY + fluidRy,
                sx - fluidRadius, fluidTopY
            )
            fluidPath.close()

            // Golden Schnapps / Tequila Liquid Gradient
            val isOverfilled = glass.currentFill > 1.0f
            val fluidBrush = Brush.verticalGradient(
                colors = if (isOverfilled) {
                    listOf(
                        Color(0xFFFF1744).copy(alpha = fogAlpha),
                        Color(0xFFD50000).copy(alpha = fogAlpha),
                        Color(0xFFB71C1C).copy(alpha = fogAlpha)
                    )
                } else {
                    listOf(
                        Color(0xFFFFF9C4).copy(alpha = fogAlpha),
                        Color(glass.liquidColor).copy(alpha = fogAlpha),
                        Color(0xFFFFA000).copy(alpha = fogAlpha),
                        Color(0xFFFF8F00).copy(alpha = fogAlpha)
                    )
                },
                startY = fluidTopY,
                endY = glassBaseY
            )
            drawScope.drawPath(path = fluidPath, brush = fluidBrush)

            // Liquid Meniscus (Top surface ellipse with slosh reflection)
            drawScope.drawOval(
                brush = Brush.radialGradient(
                    colors = if (isOverfilled) {
                        listOf(Color(0xFFFF8A80), Color(0xFFFF1744))
                    } else {
                        listOf(Color(0xFFFFFDE7), Color(glass.liquidColor), Color(0xFFFF8F00))
                    },
                    center = Offset(sx - fluidRadius * 0.2f, fluidTopY - fluidRy * 0.2f),
                    radius = fluidRadius
                ),
                topLeft = Offset(sx - fluidRadius, fluidTopY - fluidRy),
                size = Size(fluidRadius * 2f, fluidRy * 2f)
            )

            // Liquid specular sheen
            drawScope.drawOval(
                color = Color.White.copy(alpha = 0.65f * fogAlpha),
                topLeft = Offset(sx - fluidRadius * 0.7f, fluidTopY - fluidRy * 0.6f),
                size = Size(fluidRadius * 1.4f, fluidRy * 1.2f),
                style = Stroke(width = (2f * (scale / 2f)).coerceIn(1.5f, 4f))
            )

            // Juice Effect: Golden Flash Burst inside glass if PERFECT!
            if (glass.status == GlassStatus.PERFECT) {
                drawScope.drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xDDFFFFFF), Color(0xAAFFD700), Color(0x00FFD700)),
                        center = Offset(sx, fluidTopY),
                        radius = fluidRadius * 1.4f
                    ),
                    topLeft = Offset(sx - fluidRadius * 1.4f, fluidTopY - fluidRy * 1.4f),
                    size = Size(fluidRadius * 2.8f, fluidRy * 2.8f)
                )
            }
        }

        // Füllstrich (Target Fill Line) - Pulsating glow when liquid approaches!
        val targetY = glassBaseY - (sh * glass.targetFill)
        val targetWidth = (sr * 0.85f) + (sr * 0.15f) * glass.targetFill
        val targetRy = glassRy * (0.85f + 0.15f * glass.targetFill)

        val fillDiff = abs(glass.currentFill - glass.targetFill)
        val isApproaching = fillDiff < 0.16f && glass.currentFill > 0.10f
        val pulseIntensity = if (isApproaching) {
            0.5f + 0.5f * sin(System.currentTimeMillis() * 0.015f).toFloat()
        } else {
            0f
        }

        val strokeW = ((3.5f + pulseIntensity * 2f) * (scale / 2f)).coerceIn(2.8f, 10f)
        val dashOn = (8f * (scale / 2f)).coerceIn(7f, 20f)
        val dashOff = (5f * (scale / 2f)).coerceIn(4f, 13f)

        // Pulsating outer glow for target line
        val glowColor = if (isApproaching) {
            Color(0xFFFFEA00).copy(alpha = 0.60f + 0.40f * pulseIntensity)
        } else {
            Color(0x66FFFF00)
        }

        drawScope.drawOval(
            color = glowColor,
            topLeft = Offset(sx - targetWidth - 3f, targetY - targetRy - 3f),
            size = Size((targetWidth + 3f) * 2f, (targetRy + 3f) * 2f),
            style = Stroke(width = strokeW + 3f)
        )

        drawScope.drawOval(
            color = if (isApproaching) Color(0xFFFFFFFF) else Color(0xFFFFEB3B),
            topLeft = Offset(sx - targetWidth, targetY - targetRy),
            size = Size(targetWidth * 2f, targetRy * 2f),
            style = Stroke(
                width = strokeW,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
            )
        )

        // Glaskante seitlich & dünner heller Rand (Specular Edge Highlight on the left edge from top-left light)
        drawScope.drawLine(
            color = Color.White.copy(alpha = 0.72f * fogAlpha),
            start = Offset(sx - sr * 0.95f, glassTopY + 2f),
            end = Offset(sx - sr * 0.78f, glassBaseY - 2f),
            strokeWidth = (2.2f * (scale / 2f)).coerceIn(1.8f, 5f),
            cap = StrokeCap.Round
        )

        // Glass Rim Edge Outline
        drawScope.drawPath(
            path = glassPath,
            color = Color(0xCCB2EBF2).copy(alpha = 0.85f * fogAlpha),
            style = Stroke(width = (2.2f * (scale / 2f)).coerceIn(2.0f, 6f))
        )

        // Top Opening Thin Light Rim
        drawScope.drawOval(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f * fogAlpha), // Top-left glint
                    Color(0x88B2EBF2).copy(alpha = 0.70f * fogAlpha),
                    Color.White.copy(alpha = 0.50f * fogAlpha)
                ),
                start = Offset(sx - sr, glassTopY),
                end = Offset(sx + sr, glassTopY)
            ),
            topLeft = Offset(sx - sr, glassTopY - glassRy),
            size = Size(sr * 2f, glassRy * 2f),
            style = Stroke(width = (3.0f * (scale / 2f)).coerceIn(2.5f, 7f))
        )

        // Fill Status Badge when glass is finished
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
            collision.isDirectHit -> Color(0xFF00E676) // Bright green hit
            collision.isRimHit -> Color(0xFFFF9100)    // Orange rim graze
            collision.isSpill && collision.isPouring -> Color(0xFFFF1744) // Red spill
            else -> Color(0x88FFFFFF) // Alignment target
        }

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

        // Draw Fluid Curve Stream using reusable path
        streamPath.reset()
        streamPath.moveTo(nozzleX, nozzleY)
        streamPath.cubicTo(
            nozzleX, nozzleY + (targetY - nozzleY) * 0.4f,
            targetX + (nozzleX - targetX) * 0.2f, nozzleY + (targetY - nozzleY) * 0.7f,
            targetX, targetY
        )

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
                drawScope.drawOval(
                    color = Color(ripple.color).copy(alpha = (ripple.alpha * 0.70f).coerceIn(0f, 1f)),
                    topLeft = Offset(rx - rWidth, ry - rHeight),
                    size = Size(rWidth * 2f, rHeight * 2f),
                    style = Stroke(width = (2.2f * (scale / 2f)).coerceIn(1.8f, 5.5f))
                )
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

        // 2. Stream Droplets
        for (d in physics.streamDroplets) {
            val (dx, dy, _) = project3D(d.x, d.y, d.z, centerX, centerY, scale)
            val dropRadius = (d.radius * (scale / 2f)).coerceIn(3.0f, 14f)
            drawScope.drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = d.alpha),
                radius = dropRadius,
                center = Offset(dx, dy)
            )
        }

        // 3. Splash Particles
        for (p in physics.splashParticles) {
            val (px, py, _) = project3D(p.x, p.y, p.z, centerX, centerY, scale)
            val splashRadius = (p.size * (scale / 2f)).coerceIn(2.5f, 16f)

            if (p.isGlassBottom) {
                drawScope.drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = p.alpha * 0.40f),
                    radius = splashRadius * 1.55f,
                    center = Offset(px, py)
                )
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
                drawScope.drawCircle(
                    color = Color.White.copy(alpha = p.alpha * 0.9f),
                    radius = (splashRadius * 0.35f).coerceAtLeast(1.2f),
                    center = Offset(px - splashRadius * 0.28f, py - splashRadius * 0.28f)
                )

                if (p.sparkle && p.alpha > 0.35f) {
                    val glintLen = splashRadius * 1.4f
                    val glintStroke = (1.5f * (scale / 2f)).coerceIn(1.2f, 3f)
                    val glintColor = Color.White.copy(alpha = p.alpha * 0.8f)
                    drawScope.drawLine(
                        color = glintColor,
                        start = Offset(px - glintLen, py),
                        end = Offset(px + glintLen, py),
                        strokeWidth = glintStroke,
                        cap = StrokeCap.Round
                    )
                    drawScope.drawLine(
                        color = glintColor,
                        start = Offset(px, py - glintLen),
                        end = Offset(px, py + glintLen),
                        strokeWidth = glintStroke,
                        cap = StrokeCap.Round
                    )
                }
            } else {
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

            // 2. Bottle Neck extending upwards
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
            bottleShoulderPath.reset()
            bottleShoulderPath.moveTo(nozzleX - neckWidth / 2f, nozzleY - neckHeight)
            bottleShoulderPath.lineTo(nozzleX - bWidth / 2f, shoulderTopY)
            bottleShoulderPath.lineTo(nozzleX + bWidth / 2f, shoulderTopY)
            bottleShoulderPath.lineTo(nozzleX + neckWidth / 2f, nozzleY - neckHeight)
            bottleShoulderPath.close()

            drawScope.drawPath(
                path = bottleShoulderPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF43A047)),
                    start = Offset(nozzleX - bWidth / 2f, shoulderTopY),
                    end = Offset(nozzleX + bWidth / 2f, nozzleY - neckHeight)
                )
            )

            // 4. Bottle Main Body
            val bodyTopY = shoulderTopY - bHeight
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF43A047), // Top-left specular sheen
                        Color(0xFF2E7D32),
                        Color(0xFF1B5E20),
                        Color(0xFF0A2E0F)
                    ),
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
            drawRoundRect(
                color = Color(0xFFFFB300),
                topLeft = Offset(nozzleX - bWidth * 0.44f, bodyTopY + bHeight * 0.22f),
                size = Size(bWidth * 0.88f, bHeight * 0.50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale),
                style = Stroke(width = (1.5f * scale).coerceAtLeast(1.5f))
            )

            // 6. Bottle Highlight Sheen (Strong top-left glossy stripe)
            drawRect(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset(nozzleX - bWidth * 0.36f, bodyTopY + 4f * scale),
                size = Size(3.5f * scale, bHeight + 14f * scale)
            )
        }
    }
}
