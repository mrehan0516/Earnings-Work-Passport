package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Calm, strictly neutral color scheme so that color is reserved ONLY for Evidence Labels.
private val NeutralPassportColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = OnDarkInk,
    primaryContainer = StoneVariant,
    onPrimaryContainer = InkPrimary,
    secondary = InkSecondary,
    onSecondary = OnDarkInk,
    secondaryContainer = StoneSurface,
    onSecondaryContainer = InkPrimary,
    tertiary = InkSecondary,
    onTertiary = OnDarkInk,
    tertiaryContainer = StoneSurface,
    onTertiaryContainer = InkPrimary,
    background = PaperCanvas,
    onBackground = InkPrimary,
    surface = CardSurface,
    onSurface = InkPrimary,
    surfaceVariant = StoneSurface,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = CardSurface,
    surfaceContainerLow = PaperCanvas,
    surfaceContainer = StoneSurface,
    surfaceContainerHigh = StoneVariant,
    outline = HairlineBorder,
    outlineVariant = StoneVariant,
    error = EvidenceDisputedInk,
    onError = OnDarkInk,
    errorContainer = EvidenceDisputedBg,
    onErrorContainer = EvidenceDisputedInk
)

val PassportShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WorkPassportTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeutralPassportColorScheme,
        typography = AppTypography,
        shapes = PassportShapes,
        content = content
    )
}
