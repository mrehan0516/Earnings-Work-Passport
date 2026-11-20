package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.rules.RupeeFormatter
import com.example.model.EvidenceStatus
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDisputedBg
import com.example.ui.theme.EvidenceDisputedBorder
import com.example.ui.theme.EvidenceDisputedInk
import com.example.ui.theme.EvidenceDocExtractedBg
import com.example.ui.theme.EvidenceDocExtractedBorder
import com.example.ui.theme.EvidenceDocExtractedInk
import com.example.ui.theme.EvidenceSelfDeclaredBg
import com.example.ui.theme.EvidenceSelfDeclaredBorder
import com.example.ui.theme.EvidenceSelfDeclaredInk
import com.example.ui.theme.EvidenceSourceMatchedBg
import com.example.ui.theme.EvidenceSourceMatchedBorder
import com.example.ui.theme.EvidenceSourceMatchedInk
import com.example.ui.theme.EvidenceSourceVerifiedBg
import com.example.ui.theme.EvidenceSourceVerifiedBorder
import com.example.ui.theme.EvidenceSourceVerifiedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface

data class EvidenceVisualStyle(
    val bg: Color,
    val border: Color,
    val ink: Color,
    val icon: ImageVector
)

fun evidenceStyleFor(status: EvidenceStatus): EvidenceVisualStyle = when (status) {
    EvidenceStatus.SELF_DECLARED -> EvidenceVisualStyle(
        bg = EvidenceSelfDeclaredBg,
        border = EvidenceSelfDeclaredBorder,
        ink = EvidenceSelfDeclaredInk,
        icon = Icons.Outlined.EditNote
    )
    EvidenceStatus.DOCUMENT_EXTRACTED -> EvidenceVisualStyle(
        bg = EvidenceDocExtractedBg,
        border = EvidenceDocExtractedBorder,
        ink = EvidenceDocExtractedInk,
        icon = Icons.Outlined.Description
    )
    EvidenceStatus.SOURCE_MATCHED -> EvidenceVisualStyle(
        bg = EvidenceSourceMatchedBg,
        border = EvidenceSourceMatchedBorder,
        ink = EvidenceSourceMatchedInk,
        icon = Icons.Outlined.AccountBalance
    )
    EvidenceStatus.SOURCE_VERIFIED_SIMULATED -> EvidenceVisualStyle(
        bg = EvidenceSourceVerifiedBg,
        border = EvidenceSourceVerifiedBorder,
        ink = EvidenceSourceVerifiedInk,
        icon = Icons.Outlined.VerifiedUser
    )
    EvidenceStatus.DISPUTED -> EvidenceVisualStyle(
        bg = EvidenceDisputedBg,
        border = EvidenceDisputedBorder,
        ink = EvidenceDisputedInk,
        icon = Icons.Outlined.ErrorOutline
    )
}

/**
 * The ONLY colored semantic badge in the app. Always shows explicit label text + icon.
 */
@Composable
fun EvidenceBadge(
    status: EvidenceStatus,
    modifier: Modifier = Modifier
) {
    val style = evidenceStyleFor(status)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(style.bg)
            .border(1.dp, style.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Evidence label: ${status.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.ink,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = style.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrustFooterBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trust_footer_banner"),
        color = StoneSurface
    ) {
        Column {
            HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = InkSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "AI reads. Rules and you decide.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
            }
        }
    }
}

@Composable
fun NotALenderNotice(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StoneSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = InkSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Text(
                text = if (compact) {
                    "Not a lender · Not a credit score · AI never verifies income."
                } else {
                    "Earnings & Work Passport is NOT a lender and NOT a credit score. It never approves loans or claims AI-verified income—every figure shows how it is backed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }
    }
}

@Composable
fun PassportSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, HairlineBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun EvidenceTierSummaryRow(
    status: EvidenceStatus,
    totalRupees: Long,
    recordCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            EvidenceBadge(status = status)
            Text(
                text = "$recordCount ${if (recordCount == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = RupeeFormatter.format(totalRupees),
            style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.SemiBold),
            color = InkPrimary
        )
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    PassportSectionCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StoneSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = InkSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
            if (action != null) {
                Spacer(Modifier.height(14.dp))
                action()
            }
        }
    }
}
