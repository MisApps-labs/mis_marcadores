// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// ==========================================
// Identidad de marca (familia "Mis Apps")
// ==========================================
// Como Mis Notas usa el verde de su logo, Mis Marcadores usa el verde agua del suyo (#61BBA0),
// ajustado para que el texto y los iconos tengan buen contraste en claro y en oscuro.
val LogoMint = Color(0xFF61BBA0)              // Verde agua exacto del logo
val BrandMint = Color(0xFF1F7A63)             // Primario en modo claro (contraste 5,2:1 sobre blanco)
val BrandMintLight = Color(0xFF7FD3B8)        // Primario en modo oscuro
val BrandMintContainer = Color(0xFFC9EFE1)
val BrandMintOnContainer = Color(0xFF002118)
val BrandMintDarkContainer = Color(0xFF1D5E4C)
val BrandCream = Color(0xFFFFFBF5)            // Fondo del logo

val MintSecondary = Color(0xFF4B635A)
val MintSecondaryDark = Color(0xFFB2CCC2)

// ==========================================
// Tema claro (fondos agrupados estilo iOS, compartidos con el resto de Mis Apps)
// ==========================================
val BackgroundLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE5E5EA)
val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF8E8E93)
val TextTertiaryLight = Color(0xFFC7C7CC)
val OutlineLight = Color(0xFFD1D1D6)

// ==========================================
// Tema oscuro (gris elegante) y AMOLED (negro puro)
// ==========================================
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val BackgroundAmoled = Color(0xFF000000)
val SurfaceAmoled = Color(0xFF000000)
val SurfaceVariantAmoled = Color(0xFF121214)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989D)
val TextTertiaryDark = Color(0xFF48484A)
val OutlineDark = Color(0xFF38383A)

// ==========================================
// Acentos semánticos
// ==========================================
val AccentGreen = Color(0xFF34C759)
val AccentOrange = Color(0xFFFF9500)
val AccentRed = Color(0xFFFF3B30)
val AccentYellow = Color(0xFFFFCC00)
val AccentYellowDark = Color(0xFFFFD60A)
val AccentBlue = Color(0xFF007AFF)
val AccentBlueDark = Color(0xFF0A84FF)
val AccentTeal = Color(0xFF30B0C7)
val AccentTealDark = Color(0xFF40C8E0)
val AccentPurple = Color(0xFFAF52DE)
val AccentPurpleDark = Color(0xFFBF5AF2)
val AccentIndigo = Color(0xFF5856D6)
val AccentIndigoDark = Color(0xFF5E5CE6)
val AccentPink = Color(0xFFFF2D55)
val AccentPinkDark = Color(0xFFFF375F)

/** Colores para carpetas y marcadores (el mismo juego de colores que Mis Notas, más el rosa). */
enum class LabelColor(val key: String) {
    DEFAULT("DEFAULT"),
    YELLOW("YELLOW"),
    BLUE("BLUE"),
    GREEN("GREEN"),
    ORANGE("ORANGE"),
    RED("RED"),
    PINK("PINK"),
    PURPLE("PURPLE"),
    TEAL("TEAL"),
    INDIGO("INDIGO");

    companion object {
        fun fromKey(key: String?): LabelColor {
            if (key == null) return DEFAULT
            return entries.firstOrNull {
                it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}

/** Color de acento según el tema activo. */
fun LabelColor.getAccentColor(isDark: Boolean): Color = when (this) {
    LabelColor.DEFAULT -> if (isDark) SurfaceDark else SurfaceLight
    LabelColor.YELLOW -> if (isDark) AccentYellowDark else AccentYellow
    LabelColor.BLUE -> if (isDark) AccentBlueDark else AccentBlue
    LabelColor.GREEN -> AccentGreen
    LabelColor.ORANGE -> AccentOrange
    LabelColor.RED -> AccentRed
    LabelColor.PINK -> if (isDark) AccentPinkDark else AccentPink
    LabelColor.PURPLE -> if (isDark) AccentPurpleDark else AccentPurple
    LabelColor.TEAL -> if (isDark) AccentTealDark else AccentTeal
    LabelColor.INDIGO -> if (isDark) AccentIndigoDark else AccentIndigo
}

/** Color para iconos (carpetas, marcas): el primario de la app si no hay color. */
@Composable
fun LabelColor.iconTint(isDark: Boolean = isAppInDarkTheme()): Color =
    if (this == LabelColor.DEFAULT) MaterialTheme.colorScheme.primary else getAccentColor(isDark)

/** Fondo de tarjeta: el acento al 12 % (claro) o 18 % (oscuro) sobre la superficie. */
@Composable
fun LabelColor.toCardBackground(isDark: Boolean = isAppInDarkTheme()): Color {
    val surfaceBase = MaterialTheme.colorScheme.surface
    if (this == LabelColor.DEFAULT) return surfaceBase
    return getAccentColor(isDark).copy(alpha = if (isDark) 0.18f else 0.12f).compositeOver(surfaceBase)
}

/** Borde de tarjeta: el contorno normal o el acento al 35 %. */
@Composable
fun LabelColor.toCardBorder(isDark: Boolean = isAppInDarkTheme()): Color {
    if (this == LabelColor.DEFAULT) return MaterialTheme.colorScheme.outline
    return getAccentColor(isDark).copy(alpha = 0.35f)
}
