package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.announcements.AnnouncementItem
import com.example.ui.theme.HairlineGrey
import com.example.ui.theme.OffWhite
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

private val URL_REGEX = Regex("""https?://[^\s<>()]+""")

internal fun buildLinkifiedAnnouncementBody(body: String): AnnotatedString {
    return buildAnnotatedString {
        append(body)
        URL_REGEX.findAll(body).forEach { match ->
            val rawUrl = match.value
            val trimmedUrl = rawUrl.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')
            if (trimmedUrl.isNotEmpty()) {
                val start = match.range.first
                val end = start + trimmedUrl.length
                addLink(
                    url = LinkAnnotation.Url(
                        url = trimmedUrl,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = PureBlack,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}

@Composable
fun AnnouncementDialog(
    announcement: AnnouncementItem,
    onDismiss: () -> Unit,
    onActionClick: (AnnouncementItem) -> Unit
) {
    val context = LocalContext.current
    val linkifiedBody = remember(announcement.body) {
        buildLinkifiedAnnouncementBody(announcement.body)
    }
    val bannerUrl = announcement.imageUrl?.takeIf { it.isNotBlank() }
    var imageLoadFailed by remember(bannerUrl) { mutableStateOf(false) }
    val showBanner = bannerUrl != null && !imageLoadFailed

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val cardInteraction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("announcement_dialog_scrim"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .heightIn(max = 600.dp)
                    .fillMaxWidth()
                    .border(1.dp, PureBlack, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("announcement_dialog_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showBanner) {
                        val imageRequest = remember(bannerUrl, context) {
                            ImageRequest.Builder(context)
                                .data(bannerUrl)
                                .crossfade(true)
                                .build()
                        }
                        val bannerPainter = rememberAsyncImagePainter(
                            model = imageRequest,
                            onError = { imageLoadFailed = true }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(OffWhite)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss
                                )
                                .testTag("announcement_dialog_image")
                        ) {
                            Image(
                                painter = bannerPainter,
                                contentDescription = announcement.title.ifBlank { "Announcement banner" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        HorizontalDivider(color = HairlineGrey, thickness = 1.dp)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 22.dp,
                                end = 14.dp,
                                top = if (showBanner) 12.dp else 16.dp,
                                bottom = 22.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 6.dp, end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (announcement.isUrgent) {
                                    Surface(
                                        color = PureBlack,
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier
                                            .semantics(mergeDescendants = true) {}
                                            .testTag("announcement_dialog_urgent_badge")
                                    ) {
                                        Text(
                                            text = "URGENT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PureWhite,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (announcement.title.isNotBlank()) {
                                    Text(
                                        text = announcement.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PureBlack,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = onDismiss
                                            )
                                            .testTag("announcement_dialog_title")
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("announcement_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close announcement",
                                    tint = PureBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (announcement.body.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = linkifiedBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureBlack,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 8.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onDismiss
                                        )
                                        .testTag("announcement_dialog_body")
                                )
                            }
                        }

                        if (announcement.hasActionButton && !announcement.buttonText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            BlackPillButton(
                                text = announcement.buttonText,
                                onClick = { onActionClick(announcement) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                                    .testTag("announcement_action_button")
                            )
                        }
                    }
                }
            }
        }
    }
}
