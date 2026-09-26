package com.example.components

import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.GameViewModel
import com.example.game.audio.SoundSynthesizer
import com.example.game.graphics.CameraPreset
import com.example.game.graphics.Hat3DRenderer
import com.example.state.HatTier
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

    // Dynamic responsive camera zoom when pouring (brings the active glass right into the player's face!)
    val dynamicPourZoom by animateFloatAsState(
        targetValue = if (isHoldingPour) 1.10f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dynamic_pour_zoom"
    )

    // Gentle pulse animation for the active alignment feedback
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF140D07),
                        Color(0xFF26140B),
                        Color(0xFF331B0E),
                        Color(0xFF1A0E07)
                    )
                )
            )
    ) {
        // Decorative background fairy light particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = frameTicks / 1_000_000_000f
            for (i in 0..15) {
                val px = ((i * 120 + 30) % size.width.toInt()).toFloat()
                val py = ((i * 90 + 50) % (size.height * 0.45f).toInt()).toFloat()
                val pulse = 0.25f + 0.15f * sin(t * 2.5f + i)
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFFFFD54F) else Color(0xFFFF4081),
                    radius = 3.5f,
                    center = Offset(px, py),
                    alpha = pulse
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
            // TOP BAR: Back, 3 Lives, Sound, and Camera Switcher
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
                    CameraPreset.values().forEach { preset ->
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
                                    text = preset.shortName,
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

            // SCORE, PROGRESS & COMBO HEADER (Compact, high information density)
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

                // Combo Badge
                if (uiState.combo > 1) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFF5722),
                        modifier = Modifier.scale(pulseScale)
                    ) {
                        Text(
                            text = "🔥 x${uiState.combo}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Glass Progress Badge (e.g. Glas 4/11)
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

            // COLLISION STATUS & FEEDBACK PILL (Floating compact banner)
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

            // MAIN 3D INTERACTIVE PLAYFIELD CANVAS (Optimized Zoom & Mobile Perspective)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Touch-and-hold anywhere on the main canvas triggers pouring!
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
                // Live Glass Fill & Target Strich Indicator
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

                // Turntable Jog Controls & Dedicated Pouring Button
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

                    // HUGE POURING PEDAL BUTTON (Gedrückt halten zum Einschenken)
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
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🍾",
                                fontSize = 26.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHoldingPour) "SCHENKT EIN..." else "DRÜCKEN ZUM EINSCHENKEN",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFF3E1F12),
                                textAlign = TextAlign.Center
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
