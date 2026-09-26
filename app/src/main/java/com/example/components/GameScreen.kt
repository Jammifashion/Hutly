package com.example.components

import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.GameViewModel
import com.example.game.ScreenState
import com.example.game.audio.SoundSynthesizer
import com.example.game.graphics.CameraPreset
import com.example.game.graphics.Hat3DRenderer
import com.example.state.HatTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    activeTier: HatTier,
    onBackToMenu: () -> Unit
) {
    BackHandler {
        onBackToMenu()
    }

    val uiState by viewModel.uiState.collectAsState()
    val physics = viewModel.physics
    val renderer = remember { Hat3DRenderer() }

    var isMuted by remember { mutableStateOf(SoundSynthesizer.isMuted()) }
    var isHoldingPour by remember { mutableStateOf(false) }
    var cameraPreset by remember { mutableStateOf(CameraPreset.CLOSE_UP) }

    // Dynamic frame trigger for smooth 60fps canvas animation
    var frameTicks by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTicks = it }
        }
    }

    // Dynamic camera zoom when pouring (brings the active glass right into view!)
    val dynamicPourZoom by animateFloatAsState(
        targetValue = if (isHoldingPour) 1.10f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dynamic_pour_zoom"
    )

    // Pulse animation for alignment and combo
    val infiniteTransition = rememberInfiniteTransition(label = "game_fx")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Combo >= 3 Mega Glow & Scale Pulse
    val megaComboPulse by infiniteTransition.animateFloat(
        initialValue = 1.10f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "combo_fire_pulse"
    )

    // Screen Shake & Red Flash on Spill / Overflow (up to 6dp decay)
    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }
    val redVignetteAlpha = remember { Animatable(0f) }
    var prevLives by remember { mutableIntStateOf(uiState.lives) }

    LaunchedEffect(uiState.lives) {
        if (uiState.lives < prevLives) {
            launch {
                val steps = 7
                for (s in steps downTo 1) {
                    val amp = (s.toFloat() / steps) * 6f // max 6dp
                    val dx = if (s % 2 == 0) amp else -amp
                    val dy = if (s % 3 == 0) amp * 0.7f else -amp * 0.7f
                    shakeX.snapTo(dx)
                    shakeY.snapTo(dy)
                    delay(35)
                }
                shakeX.animateTo(0f, tween(40))
                shakeY.animateTo(0f, tween(40))
            }
            launch {
                redVignetteAlpha.snapTo(0.70f)
                redVignetteAlpha.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
            }
        }
        prevLives = uiState.lives
    }

    // Feedback Text Bounce & Overshoot Spring Animation
    val feedbackScale = remember { Animatable(1.0f) }
    LaunchedEffect(uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            feedbackScale.snapTo(0.72f)
            feedbackScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = 0.46f, // Snappy overshoot
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Victory celebration (Hut geschafft): Confetti & Hat Bounce
    val isVictory = uiState.screenState == ScreenState.ROUND_SUCCESS
    val victoryHop = remember { Animatable(0f) }
    LaunchedEffect(isVictory) {
        if (isVictory) {
            launch {
                victoryHop.animateTo(
                    targetValue = -30f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
                victoryHop.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(x = shakeX.value.dp, y = (shakeY.value + victoryHop.value).dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF140B06), // Warm cantina sunset amber
                        Color(0xFF261209),
                        Color(0xFF3A1A0C),
                        Color(0xFF1C0D06)
                    )
                )
            )
    ) {
        // ATMOSPHERIC BACKGROUND SCENE: 3-Plane Parallax Bokeh & Festive Lichterkette Garland
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = frameTicks / 1_000_000_000f

            // Layer 1 (Far Depth): Large soft warm Bokeh orbs drifting slowly
            val bokehColors = listOf(
                Color(0x33FFB300), // Amber
                Color(0x28FF4081), // Fiesta pink
                Color(0x2800E5FF), // Curacao cyan
                Color(0x30FFA000)  // Golden lantern
            )
            for (i in 0..7) {
                val seed = i * 47.3f
                val bx = ((w * 0.12f + i * (w * 0.12f) + sin(t * 0.3f + seed) * 35f) % w)
                val by = (h * 0.08f + (i % 3) * (h * 0.14f) + cos(t * 0.25f + seed) * 20f)
                val radius = 32f + (i % 4) * 14f
                drawCircle(
                    color = bokehColors[i % bokehColors.size],
                    radius = radius,
                    center = Offset(bx, by)
                )
            }

            // Layer 2 (Mid Depth): Draped Festoon Garland (Lichterkette) across top third
            val garlandY = h * 0.16f
            val garlandSag = 38f
            val garlandPath = Path().apply {
                moveTo(0f, garlandY)
                cubicTo(
                    w * 0.25f, garlandY + garlandSag,
                    w * 0.75f, garlandY + garlandSag,
                    w, garlandY
                )
            }
            // Draw garland cable
            drawPath(
                path = garlandPath,
                color = Color(0x554E342E),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )

            // Garland Edison light bulbs / glowing party lanterns
            val numBulbs = 10
            for (i in 1 until numBulbs) {
                val progress = i.toFloat() / numBulbs
                val lx = progress * w
                val ly = garlandY + sin(progress * Math.PI.toFloat()) * garlandSag
                val bulbColor = when (i % 4) {
                    0 -> Color(0xFFFFD54F)
                    1 -> Color(0xFFFF8A80)
                    2 -> Color(0xFF80D8FF)
                    else -> Color(0xFFCCFF90)
                }
                val glowAlpha = 0.55f + 0.35f * sin(t * 2.8f + i * 1.3f)

                // Bulb outer halo glow
                drawCircle(
                    color = bulbColor.copy(alpha = glowAlpha * 0.45f),
                    radius = 16f,
                    center = Offset(lx, ly + 4f)
                )
                // Bulb core
                drawCircle(
                    color = Color.White.copy(alpha = glowAlpha),
                    radius = 4.5f,
                    center = Offset(lx, ly + 4f)
                )
            }

            // Layer 3 (Near Ambient): Sparkling fiesta micro-particles floating upward
            for (i in 0..14) {
                val px = ((i * 127 + 25) % w.toInt()).toFloat()
                val py = ((h * 0.65f - ((t * 40f + i * 50f) % (h * 0.55f))))
                val alpha = (0.20f + 0.18f * sin(t * 3.5f + i)).coerceIn(0f, 1f)
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFFFFD54F) else Color(0xFFFF4081),
                    radius = 2.5f + (i % 3) * 1.2f,
                    center = Offset(px, py),
                    alpha = alpha
                )
            }

            // Victory Confetti Rain (when round is won!)
            if (isVictory) {
                val confettiCount = 35
                for (i in 0 until confettiCount) {
                    val cx = ((i * 89 + 15) % w.toInt()).toFloat() + sin(t * 3f + i) * 25f
                    val cy = ((t * 220f + i * 35f) % (h * 1.1f)) - 20f
                    val cColor = when (i % 5) {
                        0 -> Color(0xFFFFD54F)
                        1 -> Color(0xFFFF1744)
                        2 -> Color(0xFF00E5FF)
                        3 -> Color(0xFF76FF03)
                        else -> Color(0xFFFF4081)
                    }
                    drawRect(
                        color = cColor,
                        topLeft = Offset(cx, cy),
                        size = Size(8f, 14f)
                    )
                }
            }

            // Edge Vignette: Smooth radial gradient darkening corners for cinematic depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color(0x33000000),
                        Color(0x88000000)
                    ),
                    center = Offset(w / 2f, h * 0.52f),
                    radius = maxOf(w, h) * 0.72f
                )
            )

            // Red Spill Vignette Flash
            if (redVignetteAlpha.value > 0.01f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33FF1744).copy(alpha = redVignetteAlpha.value * 0.4f),
                            Color(0xAAFF1744).copy(alpha = redVignetteAlpha.value)
                        ),
                        center = Offset(w / 2f, h * 0.52f),
                        radius = maxOf(w, h) * 0.75f
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Back, 3 Lives, Camera Preset, Haptics & Sound
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = {
                        SoundSynthesizer.playClick()
                        onBackToMenu()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }

                // Lives Indicator (3 Party Sombreros)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..3) {
                        val isAlive = i <= uiState.lives
                        Surface(
                            shape = CircleShape,
                            color = if (isAlive) Color(0x33FFB300) else Color(0x22FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isAlive) Color(0xFFFFD700) else Color(0x44FFFFFF)
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isAlive) "🤠" else "💀",
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // Quick Camera Preset Selector
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x44000000))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        CameraPreset.CLOSE_UP to "Nah",
                        CameraPreset.OVERVIEW to "Voll",
                        CameraPreset.ACTION_CAM to "Action"
                    ).forEach { (preset, label) ->
                        val selected = cameraPreset == preset
                        Surface(
                            onClick = {
                                cameraPreset = preset
                                SoundSynthesizer.playClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Color(0xFFFFB300) else Color.Transparent,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color(0xFF1B0F05) else Color(0xDDFFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Haptic Toggle
                    IconButton(
                        onClick = {
                            viewModel.toggleHaptics()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isHapticsEnabled) Color(0x33FFB300) else Color(0x33FFFFFF))
                            .border(
                                1.dp,
                                if (uiState.isHapticsEnabled) Color(0xFFFFD700) else Color.Transparent,
                                CircleShape
                            )
                            .testTag("haptic_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Haptisches Feedback",
                            tint = if (uiState.isHapticsEnabled) Color(0xFFFFD700) else Color(0x77FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Sound Toggle
                    IconButton(
                        onClick = {
                            isMuted = SoundSynthesizer.toggleMute()
                            SoundSynthesizer.playClick()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .testTag("sound_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Sound",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // SCORE, PROGRESS & COMBO HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x44FFA000),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.score}",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        if (uiState.currentMultiplier > 1.0f) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${uiState.currentMultiplier}x)",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Combo Badge with Fire/Gold Glowing Pulse for combo >= 3
                if (uiState.combo > 1) {
                    val isMegaCombo = uiState.combo >= 3
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMegaCombo) Color(0xFFFF3D00) else Color(0xFFFF5722),
                        border = if (isMegaCombo) {
                            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD700))
                        } else null,
                        modifier = Modifier
                            .scale(if (isMegaCombo) megaComboPulse else pulseScale)
                            .shadow(if (isMegaCombo) 12.dp else 4.dp, RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = if (isMegaCombo) "🔥 MEGA x${uiState.combo}!" else "🔥 x${uiState.combo}",
                            color = if (isMegaCombo) Color(0xFFFFF9C4) else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isMegaCombo) 13.sp else 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Glass Progress Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x3300E5FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Text(
                        text = "Glas ${uiState.finishedGlassesCount}/${uiState.totalGlasses}",
                        color = Color(0xFFE0F7FA),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // COLLISION STATUS & FEEDBACK PILL (Spring Overshoot Animation)
            val collision = physics.lastCollisionResult
            val bannerColor = when {
                collision.isDirectHit -> Color(0xFF00E676)
                collision.isRimHit -> Color(0xFFFF9100)
                collision.isSpill && collision.isPouring -> Color(0xFFFF1744)
                collision.hitGlass?.isAligned == true -> Color(0xFFFFD700)
                else -> Color(0x99FFFFFF)
            }
            val bannerText = when {
                uiState.feedbackMessage != null -> uiState.feedbackMessage!!
                collision.isDirectHit -> "🟢 PERFEKT IM GLAS! STRICH BEACHTEN!"
                collision.isRimHit -> "🟠 AM RAND! ETWAS SCHRÄG!"
                collision.isSpill && collision.isPouring -> "🔴 DANEBEN! VERSCHÜTTET!"
                collision.hitGlass?.isAligned == true -> "🎯 GLAS BEREIT! DRÜCKEN ZUM EINSCHENKEN"
                else -> "🔄 Drehteller rotiert ... Glas anvisieren"
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bannerColor.copy(alpha = 0.22f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, bannerColor),
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .scale(feedbackScale.value)
                    .padding(vertical = 1.dp)
            ) {
                Text(
                    text = bannerText,
                    color = if (uiState.feedbackMessage != null) Color(uiState.feedbackColor) else bannerColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            // MAIN 3D INTERACTIVE PLAYFIELD CANVAS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isHoldingPour = true
                                viewModel.startPouring()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (isHoldingPour) {
                                    isHoldingPour = false
                                    viewModel.stopPouring()
                                }
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (frameTicks > 0) {
                        renderer.drawScene(
                            drawScope = this,
                            physics = physics,
                            hatTier = activeTier,
                            zoom = dynamicPourZoom,
                            cameraPreset = cameraPreset
                        )
                    }
                }
            }

            // BOTTOM CONTROL SECTION: Live Target Gauge & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Glass Fill & Target Gauge
                val currentTarget = collision.hitGlass?.targetFill ?: 0.70f
                val currentFill = collision.hitGlass?.currentFill ?: 0.0f
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33000000),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.fillMaxWidth(0.94f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFEB3B),
                                modifier = Modifier.size(width = 14.dp, height = 4.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Füllstrich: ${(currentTarget * 100).toInt()}%",
                                color = Color(0xFFFFD54F),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Füllstand: ${(currentFill * 100).toInt()}%",
                            color = when {
                                currentFill > 1.0f -> Color(0xFFFF1744)
                                currentFill >= currentTarget * 0.95f && currentFill <= currentTarget * 1.05f -> Color(0xFF00E676)
                                else -> Color(0xFFE0F7FA)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Turntable Jog Controls & Pouring Button
                Row(
                    modifier = Modifier.fillMaxWidth(0.96f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Jog Left Button
                    IconButton(
                        onClick = {
                            physics.jogTurntable(-0.25f)
                            SoundSynthesizer.playClick()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFA000))
                            .border(1.5.dp, Color(0xFFFFB300), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Links drehen",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // HUGE POURING PEDAL BUTTON
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = if (isHoldingPour) Color(0xFFFF8F00) else Color(0xFFFFB300),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .padding(horizontal = 10.dp)
                            .scale(if (isHoldingPour) 0.96f else 1.0f)
                            .shadow(
                                elevation = if (isHoldingPour) 6.dp else 14.dp,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = Color(0xFFFFB300),
                                spotColor = Color(0xFFFF6D00)
                            )
                            .testTag("pour_button")
                            .pointerInteropFilter { motionEvent ->
                                when (motionEvent.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        isHoldingPour = true
                                        viewModel.startPouring()
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        if (isHoldingPour) {
                                            isHoldingPour = false
                                            viewModel.stopPouring()
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = if (isHoldingPour) "🌊 SCHENKT EIN..." else "🫗 DRÜCKEN ZUM EINSCHENKEN",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFF2C150A)
                            )
                        }
                    }

                    // Jog Right Button
                    IconButton(
                        onClick = {
                            physics.jogTurntable(0.25f)
                            SoundSynthesizer.playClick()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFA000))
                            .border(1.5.dp, Color(0xFFFFB300), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rechts drehen",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
