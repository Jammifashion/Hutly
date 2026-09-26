package com.example.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.example.state.HatEntity
import com.example.state.HatTier
import com.example.state.HatViewModel

@Composable
fun ShelfScreen(
    hatViewModel: HatViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val hats by hatViewModel.allHats.collectAsState()
    val highestTier by hatViewModel.highestTier.collectAsState()
    val equippedTier by hatViewModel.equippedTier.collectAsState()
    val unlockedTiers by hatViewModel.unlockedTiers.collectAsState()
    val playerProgress by hatViewModel.playerProgress.collectAsState()

    var newlyMergedTier by remember { mutableStateOf<HatTier?>(null) }

    LaunchedEffect(Unit) {
        hatViewModel.mergeEvents.collect { tier ->
            newlyMergedTier = tier
        }
    }

    // Group hats by level
    val hatsByLevel = remember(hats) {
        hats.groupBy { it.level }
    }

    // Shelf background gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E120B),
                        Color(0xFF2C1910),
                        Color(0xFF382014),
                        Color(0xFF1B0E07)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        SoundSynthesizer.playClick()
                        onBack()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Das Hutregal",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = "3 gleiche Hüte mergen zu 1 höheren Stufe!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFECB3)
                    )
                }
            }

            // Player Progress & Stats Banner (localStorage / Room)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x33000000),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFD54F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${hats.size}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            text = "Hüte im Regal",
                            fontSize = 10.sp,
                            color = Color(0xFFFFECB3)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(Color(0x33FFFFFF))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${unlockedTiers.size}/7",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = "Freigeschaltet",
                            fontSize = 10.sp,
                            color = Color(0xFFFFECB3)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(Color(0x33FFFFFF))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${playerProgress.highScore}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFFFF9100)
                        )
                        Text(
                            text = "Highscore",
                            fontSize = 10.sp,
                            color = Color(0xFFFFECB3)
                        )
                    }
                }
            }

            // Highest Tier Multiplier Banner
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(highestTier.primaryColor).copy(alpha = 0.22f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(highestTier.primaryColor)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Höchster Hut: ${highestTier.displayName}",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(highestTier.primaryColor),
                            fontSize = 14.sp
                        )
                        Text(
                            text = highestTier.subtitle,
                            fontSize = 11.sp,
                            color = Color(0xFFFFE082)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFB300)
                    ) {
                        Text(
                            text = "${highestTier.multiplier}x Multiplikator",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Color(0xFF3E1F12),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Merge Alert Announcement if a merge occurred
            AnimatedVisibility(
                visible = newlyMergedTier != null,
                enter = fadeIn() + scaleIn()
            ) {
                newlyMergedTier?.let { tier ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF7C4DFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Glückwunsch! 3 Hüte verschmolzen zu: ${tier.displayName}!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Wooden Shelves for each Tier (0 to 6)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(HatTier.entries.toList()) { tier ->
                    val hatsInTier = hatsByLevel[tier.level] ?: emptyList()
                    val isUnlocked = unlockedTiers.contains(tier) || tier.level <= playerProgress.highestUnlockedLevel || hatsInTier.isNotEmpty()
                    val isEquipped = equippedTier == tier

                    ShelfTierRow(
                        tier = tier,
                        hats = hatsInTier,
                        isUnlocked = isUnlocked,
                        isEquipped = isEquipped,
                        canMerge = hatsInTier.size >= 3 && tier.level < HatTier.DIAMANT.level,
                        onMerge = {
                            hatViewModel.performAutoMerge { merged ->
                                newlyMergedTier = merged
                            }
                        },
                        onEquip = {
                            hatViewModel.equipTier(tier)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfTierRow(
    tier: HatTier,
    hats: List<HatEntity>,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    canMerge: Boolean,
    onMerge: () -> Unit,
    onEquip: () -> Unit
) {
    val shelfPhysics = remember {
        HatPhysicsEngine().apply {
            baseAngularVelocity = 0.5f
        }
    }
    val renderer = remember { Hat3DRenderer() }

    // Pulsing highlight for ready-to-merge shelves
    val pulseTransition = rememberInfiniteTransition(label = "shelf_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUnlocked) Color(0x33000000) else Color(0x1F000000))
            .border(
                1.dp,
                when {
                    canMerge -> Color(0xFFFFD54F)
                    isEquipped -> Color(tier.primaryColor)
                    isUnlocked -> Color(0x33FFFFFF)
                    else -> Color(0x15FFFFFF)
                },
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        // Tier Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isUnlocked) Color(tier.primaryColor) else Color(0x55888888),
                    modifier = Modifier.size(16.dp)
                ) {
                    if (!isUnlocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Gesperrt",
                            tint = Color.White,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tier.displayName,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) Color(tier.primaryColor) else Color(0x88FFFFFF),
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isUnlocked) "(${hats.size} im Besitz)" else "(Gesperrt)",
                    color = if (isUnlocked) Color(0xBBFFFFFF) else Color(0x55FFFFFF),
                    fontSize = 12.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Multiplier chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33FFA000)
                ) {
                    Text(
                        text = "${tier.multiplier}x",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Equip / Active Button
                if (isUnlocked) {
                    if (isEquipped) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Aktiv",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onEquip,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF))
                        ) {
                            Text(
                                text = "Ausrüsten",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Shelf Board with 3D Hats Representation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x11000000), Color(0x55000000))
                    ),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!isUnlocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0x55FFFFFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wird durch Verschmelzen oder Runden freigeschaltet",
                        color = Color(0x55FFFFFF),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else if (hats.isEmpty()) {
                Text(
                    text = "Freigeschaltet! (Kein Hut im Lager – merge 3x Stufe darunter)",
                    color = Color(0x88FFD54F),
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayCount = minOf(hats.size, 6)
                    for (i in 0 until displayCount) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                renderer.drawScene(
                                    drawScope = this,
                                    physics = shelfPhysics,
                                    hatTier = tier,
                                    zoom = 0.55f + (tier.level * 0.04f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Wooden shelf bar texture
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF5D4037), Color(0xFF8D6E63), Color(0xFF4E342E))
                    ),
                    RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                )
        )

        // Merge Action Button if 3 or more hats are ready
        if (canMerge) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    SoundSynthesizer.playClick()
                    onMerge()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .scale(pulseScale)
                    .shadow(8.dp, RoundedCornerShape(22.dp))
                    .testTag("merge_button_${tier.level}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300)
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF3E1F12),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "3x Verschmelzen → ${HatTier.fromLevel(tier.level + 1).displayName}!",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF3E1F12),
                    fontSize = 13.sp
                )
            }
        }
    }
}
