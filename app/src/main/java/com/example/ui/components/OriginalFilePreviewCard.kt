package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ExtractedDocumentDraft
import com.example.domain.rules.UploadFileKind
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.StoneSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class RenderedPdfPage(
    val bitmap: Bitmap?,
    val pageCount: Int,
    val errorMessage: String? = null
)

/**
 * Displays the original uploaded image, rendered PDF page (with multi-page controls),
 * or synthetic sample printed document beside or above the Review & Confirm form.
 */
@Composable
fun OriginalFilePreviewCard(
    draft: ExtractedDocumentDraft,
    isUnsavedInMemory: Boolean,
    isWidePane: Boolean,
    modifier: Modifier = Modifier
) {
    var isCollapsed by remember(draft.fileHash, draft.title) { mutableStateOf(false) }
    var isExpandedHeight by remember(draft.fileHash, draft.title) { mutableStateOf(isWidePane) }

    val kindLabel = when {
        draft.fileKind == UploadFileKind.PDF || draft.pdfBytes != null -> "PDF"
        draft.fileKind == UploadFileKind.IMAGE || draft.previewUri != null -> "IMAGE"
        !draft.rawSampleText.isNullOrBlank() -> "SAMPLE DOC"
        else -> draft.documentType.label.uppercase()
    }
    val kindIcon = when {
        draft.fileKind == UploadFileKind.PDF || draft.pdfBytes != null -> Icons.Outlined.PictureAsPdf
        draft.fileKind == UploadFileKind.IMAGE || draft.previewUri != null -> Icons.Outlined.Image
        else -> Icons.Outlined.Description
    }

    PassportSectionCard(
        modifier = modifier.testTag("original_file_preview_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = kindIcon,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Original File Preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = draft.fileName.ifBlank { draft.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = StoneSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (!isWidePane) {
                    IconButton(
                        onClick = { isCollapsed = !isCollapsed },
                        modifier = Modifier.testTag("preview_collapse_toggle")
                    ) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            contentDescription = if (isCollapsed) "Show original file preview" else "Hide original file preview",
                            tint = InkPrimary
                        )
                    }
                }
            }
        }

        if (isUnsavedInMemory) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Draft held in memory only — nothing is written to Firestore until you tap Confirm.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }

        if (!isCollapsed || isWidePane) {
            Spacer(Modifier.height(10.dp))
            when {
                draft.previewUri != null -> {
                    ImageFilePreviewBody(
                        model = draft.previewUri,
                        expanded = isExpandedHeight,
                        onToggleExpand = { isExpandedHeight = !isExpandedHeight }
                    )
                }

                draft.pdfBytes != null && draft.pdfBytes.isNotEmpty() -> {
                    PdfFilePreviewBody(
                        pdfBytes = draft.pdfBytes,
                        cacheKey = draft.fileHash.ifBlank { draft.title },
                        expanded = isExpandedHeight,
                        onToggleExpand = { isExpandedHeight = !isExpandedHeight }
                    )
                }

                !draft.rawSampleText.isNullOrBlank() -> {
                    SampleDocumentPaperPreview(
                        rawText = draft.rawSampleText,
                        expanded = isExpandedHeight,
                        onToggleExpand = { isExpandedHeight = !isExpandedHeight }
                    )
                }

                else -> {
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Saved Pending Draft Summary",
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary
                            )
                            Text(
                                text = "This pending document was loaded from your saved queue. Compare the extracted fields below against your original receipt before confirming.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                            if (draft.fileHash.isNotBlank()) {
                                Text(
                                    text = "File SHA-256: ${draft.fileHash.take(16)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageFilePreviewBody(
    model: Any,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = StoneSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = model,
                contentDescription = "Original uploaded income document image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (expanded) 400.dp else 210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PaperCanvas)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onToggleExpand,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "Compact view" else "Expand image",
                    color = InkPrimary
                )
            }
        }
    }
}

@Composable
private fun PdfFilePreviewBody(
    pdfBytes: ByteArray,
    cacheKey: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember(cacheKey) { mutableIntStateOf(0) }
    var isLoading by remember(cacheKey, currentPage) { mutableStateOf(true) }
    var rendered by remember(cacheKey, currentPage) { mutableStateOf<RenderedPdfPage?>(null) }

    LaunchedEffect(cacheKey, currentPage) {
        isLoading = true
        rendered = withContext(Dispatchers.IO) {
            renderPdfPageSafely(context, pdfBytes, currentPage)
        }
        isLoading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = StoneSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (expanded) 420.dp else 240.dp)
                    .background(PaperCanvas),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            color = InkPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    rendered?.bitmap != null -> {
                        Image(
                            bitmap = rendered!!.bitmap!!.asImageBitmap(),
                            contentDescription = "Uploaded PDF page ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (expanded) 420.dp else 240.dp)
                                .padding(6.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = rendered?.errorMessage ?: "Could not render PDF preview on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        val pageCount = rendered?.pageCount ?: 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (currentPage > 0) currentPage -= 1 },
                    enabled = currentPage > 0,
                    modifier = Modifier.testTag("pdf_prev_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.NavigateBefore,
                        contentDescription = "Previous PDF page",
                        tint = if (currentPage > 0) InkPrimary else InkSecondary.copy(alpha = 0.4f)
                    )
                }
                Text(
                    text = "Page ${currentPage + 1} of $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                IconButton(
                    onClick = { if (currentPage + 1 < pageCount) currentPage += 1 },
                    enabled = currentPage + 1 < pageCount,
                    modifier = Modifier.testTag("pdf_next_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                        contentDescription = "Next PDF page",
                        tint = if (currentPage + 1 < pageCount) InkPrimary else InkSecondary.copy(alpha = 0.4f)
                    )
                }
            }

            TextButton(
                onClick = onToggleExpand,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (expanded) "Compact" else "Expand",
                    color = InkPrimary
                )
            }
        }
    }
}

@Composable
private fun SampleDocumentPaperPreview(
    rawText: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = if (expanded) 380.dp else 185.dp)
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = rawText.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = InkPrimary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onToggleExpand,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "Compact text" else "Expand document",
                    color = InkPrimary
                )
            }
        }
    }
}

private fun renderPdfPageSafely(
    context: Context,
    pdfBytes: ByteArray,
    pageIndex: Int
): RenderedPdfPage {
    val tempFile = File.createTempFile("review_preview_", ".pdf", context.cacheDir)
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null
    return try {
        tempFile.writeBytes(pdfBytes)
        pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        if (count <= 0) {
            return RenderedPdfPage(bitmap = null, pageCount = 0, errorMessage = "This PDF has no readable pages.")
        }
        val safeIndex = pageIndex.coerceIn(0, count - 1)
        page = renderer.openPage(safeIndex)
        val targetWidth = 1080
        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
        val targetHeight = (page.height * scale).toInt().coerceIn(200, 2200)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        RenderedPdfPage(bitmap = bitmap, pageCount = count)
    } catch (e: Exception) {
        RenderedPdfPage(
            bitmap = null,
            pageCount = 1,
            errorMessage = "Preview could not render this PDF (${e.localizedMessage ?: "unsupported format"})."
        )
    } finally {
        try { page?.close() } catch (_: Exception) {}
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        try { tempFile.delete() } catch (_: Exception) {}
    }
}
