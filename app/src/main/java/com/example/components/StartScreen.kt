package com.example.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.audio.SoundSynthesizer
import com.example.game.graphics.Hat3DRenderer
import com.example.game.physics.HatPhysicsEngine
import com.example.state.HatTier
import kotlin.math.sin

@Composable
fun StartScreen(
    highestTier: HatTier,
    collectedHatsCount: Int,
    highScore: Int = 0,
    hasSavedGame: Boolean = false,
    savedRound: Int = 1,
    savedScore: Int = 0,
    unlockedTiersCount: Int = 1,
    onStartGame: () -> Unit,
    onResumeGame: (() -> Unit)? = null,
    onOpenShelf: () -> Unit
) {
    // Rotating preview hat
    val previewPhysics = remember {
        HatPhysicsEngine().apply {
            baseAngularVelocity = 0.5f
        }
    }
    val renderer = remember { Hat3DRenderer() }

    var isMuted by remember { mutableStateOf(SoundSynthesizer.isMuted()) }

    // Animation frame for rotating preview
    val infiniteTransition = rememberInfiniteTransition(label = "hat_spin")
    val angleAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    // Pulse animation for play button
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(angleAnim) {
        previewPhysics.currentRotationAngle = (angleAnim * (Math.PI.toFloat() / 180f))
        previewPhysics.update(0.016f) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A120B),
                        Color(0xFF2C1810),
                        Color(0xFF3E1F12),
                        Color(0xFF1F1008)
                    )
                )
            )
    ) {
        // Floating festive ambient particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0..18) {
                val px = ((i * 137) % w.toInt()).toFloat()
                val py = ((i * 229) % h.toInt()).toFloat()
                val r = (3f + (i % 5) * 2f)
                val alpha = 0.25f + 0.15f * sin((angleAnim + i * 20) * 0.05f)
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFFFFD54F) else Color(0xFFFF4081),
                    radius = r,
                    center = Offset(px, py),
                    alpha = alpha
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Multiplier badge, High Score & Sound toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Multiplier & Highscore Chips in a row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Multiplier Chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x33FFA000),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Multiplikator",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${highestTier.multiplier}x",
                                color = Color(0xFFFFE082),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Highscore Badge
                    if (highScore > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x33FFD54F),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Rekord",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$highScore Pkt",
                                    color = Color(0xFFFFE082),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Sound Toggle Button
                IconButton(
                    onClick = {
                        isMuted = SoundSynthesizer.toggleMute()
                        SoundSynthesizer.playClick()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Sound an/aus",
                        tint = Color.White
                    )
                }
            }

            // Title & Headline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Hut ist gut",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Das 3D-Einschenkspiel für treffsichere Partygäste",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFECB3),
                    textAlign = TextAlign.Center
                )

                // Saved Progress Subtitle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x22FFFFFF),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "🏆 $unlockedTiersCount/7 Stufen freigeschaltet • Höchster Hut: ${highestTier.displayName}",
                        fontSize = 11.sp,
                        color = Color(0xFFFFE082),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Central 3D Interactive Hat Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    renderer.drawScene(
                        drawScope = this,
                        physics = previewPhysics,
                        hatTier = highestTier,
                        zoom = 1.1f
                    )
                }
            }

            // Bottom Action Section: Resume / Play Buttons & Shelf Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // If there is an active saved game, offer to Resume!
                if (hasSavedGame && onResumeGame != null) {
                    Button(
                        onClick = {
                            SoundSynthesizer.playClick()
                            onResumeGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .height(58.dp)
                            .scale(pulseAnim)
                            .shadow(16.dp, RoundedCornerShape(29.dp), ambientColor = Color(0xFF00E676), spotColor = Color(0xFF00B0FF))
                            .testTag("resume_game_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676)
                        ),
                        shape = RoundedCornerShape(29.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF1B5E20),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fortsetzen (Runde $savedRound • $savedScore Pkt)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }

                // "Einschenken!" Primary CTA (or "Neues Spiel" if saved game exists)
                Button(
                    onClick = {
                        SoundSynthesizer.playClick()
                        onStartGame()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(if (hasSavedGame) 50.dp else 62.dp)
                        .then(if (!hasSavedGame) Modifier.scale(pulseAnim) else Modifier)
                        .shadow(14.dp, RoundedCornerShape(31.dp), ambientColor = Color(0xFFFFB300), spotColor = Color(0xFFFF6D00))
                        .testTag("start_game_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300)
                    ),
                    shape = RoundedCornerShape(31.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (hasSavedGame) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF3E1F12),
                            modifier = Modifier.size(if (hasSavedGame) 22.dp else 30.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasSavedGame) "Neues Spiel starten" else "Einschenken!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color(0xFF3E1F12)
                        )
                    }
                }

                // "Hutregal" Action
                OutlinedButton(
                    onClick = {
                        SoundSynthesizer.playClick()
                        onOpenShelf()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(48.dp)
                        .testTag("open_shelf_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x22FFFFFF),
                        contentColor = Color(0xFFFFECB3)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66FFD54F)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hutregal ($collectedHatsCount Hüte im Besitz)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFFFECB3)
                        )
                    }
                }
            }
        }
    }
}
