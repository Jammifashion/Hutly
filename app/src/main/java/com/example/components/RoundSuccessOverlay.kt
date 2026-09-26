package com.example.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun RoundSuccessOverlay(
    stars: Int,
    score: Int,
    earnedTier: HatTier,
    round: Int,
    onGoToShelf: () -> Unit,
    onNextRound: () -> Unit
) {
    val previewPhysics = remember {
        HatPhysicsEngine().apply {
            baseAngularVelocity = 0.7f
        }
    }
    val renderer = remember { Hat3DRenderer() }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val confettiAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_fall"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    LaunchedEffect(spinAngle) {
        previewPhysics.currentRotationAngle = spinAngle * (Math.PI.toFloat() / 180f)
        previewPhysics.update(0.016f) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE1A1009)),
        contentAlignment = Alignment.Center
    ) {
        // Confetti particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0..30) {
                val startX = ((i * 123) % w.toInt()).toFloat()
                val speed = 250f + (i % 6) * 60f
                val y = (confettiAnim * speed * 2f + i * 40f) % h
                val x = startX + sin((confettiAnim * 4f + i)) * 30f
                val color = when (i % 4) {
                    0 -> Color(0xFFFFD54F)
                    1 -> Color(0xFFFF4081)
                    2 -> Color(0xFF00E5FF)
                    else -> Color(0xFF76FF03)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(10f, 16f)
                )
            }
        }

        // Center Celebration Card
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF2C1910),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB300)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🎉 Hut geschafft!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center
                )

                // Stars display
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..3) {
                        val active = i <= stars
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (active) Color(0xFFFFD700) else Color(0x44FFFFFF),
                            modifier = Modifier
                                .size(38.dp)
                                .scale(if (active) 1.15f else 0.9f)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Punkte: $score",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFFFECB3)
                )

                // 3D Preview of Earned Hat
                Box(
                    modifier = Modifier
                        .height(140.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderer.drawScene(
                            drawScope = this,
                            physics = previewPhysics,
                            hatTier = earnedTier,
                            zoom = 0.95f
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(earnedTier.primaryColor).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(earnedTier.primaryColor))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Neuer Hut: ${earnedTier.displayName}",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(earnedTier.primaryColor),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${earnedTier.subtitle} • Multiplikator ${earnedTier.multiplier}x",
                            fontSize = 12.sp,
                            color = Color(0xFFFFE082)
                        )
                    }
                }

                // Action Buttons: Ab ins Regal & Nächste Runde
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            SoundSynthesizer.playClick()
                            onGoToShelf()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("go_to_shelf_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB300)
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = Color(0xFF3E1F12),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ab ins Regal!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF3E1F12)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            SoundSynthesizer.playClick()
                            onNextRound()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("next_round_button"),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFD54F)),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            text = "Nächste Runde (Runde ${round + 1})",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
