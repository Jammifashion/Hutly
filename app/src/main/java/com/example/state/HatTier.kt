package com.example.state

enum class HatTier(
    val level: Int,
    val displayName: String,
    val subtitle: String,
    val primaryColor: Long,
    val secondaryColor: Long,
    val accentColor: Long,
    val multiplier: Float,
    val isMetallic: Boolean = false,
    val isDiamond: Boolean = false
) {
    GELB(
        level = 0,
        displayName = "Gelber Sombrero",
        subtitle = "Der Party-Klassiker",
        primaryColor = 0xFFFFD600,
        secondaryColor = 0xFFFFAB00,
        accentColor = 0xFFFF6D00,
        multiplier = 1.0f
    ),
    GRUEN(
        level = 1,
        displayName = "Grüner Sombrero",
        subtitle = "Fiesta Verde",
        primaryColor = 0xFF00E676,
        secondaryColor = 0xFF00C853,
        accentColor = 0xFFB9F6CA,
        multiplier = 1.5f
    ),
    BLAU(
        level = 2,
        displayName = "Blauer Sombrero",
        subtitle = "Neon Fiesta",
        primaryColor = 0xFF2979FF,
        secondaryColor = 0xFF1565C0,
        accentColor = 0xFF82B1FF,
        multiplier = 2.0f
    ),
    ROT(
        level = 3,
        displayName = "Roter Sombrero",
        subtitle = "Salsa Fuego",
        primaryColor = 0xFFFF1744,
        secondaryColor = 0xFFD50000,
        accentColor = 0xFFFF8A80,
        multiplier = 3.0f
    ),
    SCHWARZ(
        level = 4,
        displayName = "Schwarzer Sombrero",
        subtitle = "Velvet Noir",
        primaryColor = 0xFF212121,
        secondaryColor = 0xFF424242,
        accentColor = 0xFFECEFF1,
        multiplier = 5.0f
    ),
    GOLD(
        level = 5,
        displayName = "Goldener Sombrero",
        subtitle = "El Dorado",
        primaryColor = 0xFFFFD700,
        secondaryColor = 0xFFFFAB00,
        accentColor = 0xFFFFF9C4,
        multiplier = 10.0f,
        isMetallic = true
    ),
    DIAMANT(
        level = 6,
        displayName = "Diamant Sombrero",
        subtitle = "König der Fiesta",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF7C4DFF,
        accentColor = 0xFFFFFFFF,
        multiplier = 25.0f,
        isDiamond = true
    );

    companion object {
        fun fromLevel(level: Int): HatTier {
            return entries.firstOrNull { it.level == level } ?: GELB
        }
    }
}
