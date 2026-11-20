package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// --- Base Neutral Palette (Strictly neutral — NO decorative brand colors) ---
val PaperCanvas = Color(0xFFFAF9F6)
val CardSurface = Color(0xFFFFFFFF)
val StoneSurface = Color(0xFFF1EFEA)
val StoneVariant = Color(0xFFE7E4DC)
val HairlineBorder = Color(0xFFDCD8CE)

val InkPrimary = Color(0xFF1C1B1F)
val InkSecondary = Color(0xFF56545C)
val InkMuted = Color(0xFF7A7782)
val OnDarkInk = Color(0xFFFFFFFF)

// --- Evidence Label Semantic Colors (THE ONLY colors used in the app) ---
// 1. Self-declared: Grey
val EvidenceSelfDeclaredBg = Color(0xFFF0F2F4)
val EvidenceSelfDeclaredBorder = Color(0xFFCBD0D6)
val EvidenceSelfDeclaredInk = Color(0xFF474E57)

// 2. Document-extracted: Yellow
val EvidenceDocExtractedBg = Color(0xFFFEF4D1)
val EvidenceDocExtractedBorder = Color(0xFFE5C454)
val EvidenceDocExtractedInk = Color(0xFF7A5400)

// 3. Source-matched: Blue
val EvidenceSourceMatchedBg = Color(0xFFE5F1FC)
val EvidenceSourceMatchedBorder = Color(0xFF8BBCEB)
val EvidenceSourceMatchedInk = Color(0xFF0D4F8B)

// 4. Source-verified (Simulated): Green
val EvidenceSourceVerifiedBg = Color(0xFFE2F5EA)
val EvidenceSourceVerifiedBorder = Color(0xFF82CCA0)
val EvidenceSourceVerifiedInk = Color(0xFF14633A)

// 5. Disputed: Red
val EvidenceDisputedBg = Color(0xFFFDECEC)
val EvidenceDisputedBorder = Color(0xFFE69A9D)
val EvidenceDisputedInk = Color(0xFF9E1C20)
