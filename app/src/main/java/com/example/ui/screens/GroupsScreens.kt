package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.floating.FloatingCounterManager
import com.example.floating.SmallOneCroreLogoGlyph
import com.example.groups.GroupCelebrationNotice
import com.example.groups.GroupCelebrationType
import com.example.groups.GroupItem
import com.example.groups.GroupLogic
import com.example.groups.GroupMemberProgress
import com.example.groups.GroupType
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.WhiteBorderPillButton
import com.example.ui.components.formatNumber
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.appOutlinedTextFieldColors
import java.time.LocalDate

@Composable
fun HomeMyGroupsCard(
    groups: List<GroupItem>,
    onOpenGroups: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    val leadGroup = groups.firstOrNull()
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpenGroups)
            .testTag("home_my_groups_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(if (groups.isNotEmpty()) palette.accent else palette.subtleSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (groups.isNotEmpty()) palette.accent else palette.hairline, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = "My Groups",
                    tint = if (groups.isNotEmpty()) palette.onAccent else palette.onCard,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "My Groups",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.onCard,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = palette.subtleSurface,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .border(1.dp, palette.hairline, RoundedCornerShape(999.dp))
                    ) {
                        Text(
                            text = "${groups.size} / ${GroupLogic.MAX_GROUPS_PER_USER}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onCard,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("home_my_groups_count")
                        )
                    }
                }
                Text(
                    text = if (leadGroup != null) {
                        val rest = if (groups.size > 1) " (+${groups.size - 1} more)" else ""
                        "${leadGroup.name}$rest · ${formatNumber(leadGroup.effectiveGroupTotal)} total"
                    } else {
                        "Create a group or join with an invite code"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onCardSubtext,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open My Groups",
                tint = palette.onCard,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun GroupsHubScreen(
    groups: List<GroupItem>,
    statusMessage: String?,
    isJoiningGroup: Boolean = false,
    onClearStatusMessage: () -> Unit,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: (name: String, type: GroupType, zikrName: String?, targetCount: Long) -> Unit,
    onJoinByCode: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = AppThemeColors.palette

    var joinCodeInput by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    val atGroupLimit = groups.size >= GroupLogic.MAX_GROUPS_PER_USER
    val groupIdsSnapshot = remember(groups) { groups.map { it.id }.toSet() }
    var previousGroupIds by remember { mutableStateOf(groupIdsSnapshot) }
    androidx.compose.runtime.LaunchedEffect(groupIdsSnapshot) {
        if (groupIdsSnapshot.any { it !in previousGroupIds }) {
            joinCodeInput = ""
        }
        previousGroupIds = groupIdsSnapshot
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("groups_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.onCanvas
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Groups",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.onCanvas
                    )
                    Text(
                        text = "${groups.size} of ${GroupLogic.MAX_GROUPS_PER_USER} group slots used",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onCanvasSubtext
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    color = palette.subtleSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.strongBorder, RoundedCornerShape(12.dp))
                        .clickable { onClearStatusMessage() }
                        .testTag("groups_status_banner")
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onCard,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Join with invite code card
            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "JOIN WITH CODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCardSubtext
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { joinCodeInput = it.uppercase().take(48) },
                            placeholder = { Text("Enter invite code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            colors = appOutlinedTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("groups_join_code_input")
                        )
                        BlackPillButton(
                            text = if (isJoiningGroup) "Joining..." else "Join",
                            enabled = GroupLogic.extractInviteCodeInput(joinCodeInput).length >= 4 &&
                                !atGroupLimit &&
                                !isJoiningGroup,
                            onClick = {
                                onJoinByCode(joinCodeInput)
                            },
                            modifier = Modifier.testTag("groups_join_button")
                        )
                    }
                    if (atGroupLimit) {
                        Text(
                            text = "You are in ${GroupLogic.MAX_GROUPS_PER_USER} groups (max). Open a group and tap Leave Group to free a slot.",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onCardSubtext,
                            modifier = Modifier.testTag("groups_limit_notice")
                        )
                    }
                }
            }

            BlackPillButton(
                text = "Create Group",
                enabled = !atGroupLimit,
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("groups_create_button")
            )

            if (groups.isEmpty()) {
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                        .testTag("groups_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = null,
                            tint = palette.onCard,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No groups joined yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onCard,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Create a group for Durood or any Zikr with a shared target, or enter a friend's invite code above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onCardSubtext,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("groups_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "YOUR GROUPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCanvasSubtext
                    )
                    groups.forEach { group ->
                        GroupSummaryCard(
                            group = group,
                            onClick = { onOpenGroup(group.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, zikrName, target ->
                showCreateDialog = false
                onCreateGroup(name, type, zikrName, target)
            }
        )
    }
}

@Composable
private fun GroupSummaryCard(
    group: GroupItem,
    onClick: () -> Unit
) {
    val palette = AppThemeColors.palette
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("group_card_${group.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = if (group.type == GroupType.MAIN_DUROOD) palette.accent else palette.subtleSurface,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.border(
                            1.dp,
                            if (group.type == GroupType.MAIN_DUROOD) palette.accent else palette.hairline,
                            RoundedCornerShape(999.dp)
                        )
                    ) {
                        Text(
                            text = group.effectiveZikrName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (group.type == GroupType.MAIN_DUROOD) palette.onAccent else palette.onCard,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("group_card_zikr_pill_${group.id}")
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open ${group.name}",
                    tint = palette.onCard
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(palette.track, RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            group.progressFraction.coerceAtLeast(
                                if (group.effectiveGroupTotal > 0L) 0.02f else 0f
                            )
                        )
                        .fillMaxHeight()
                        .background(palette.accent, RoundedCornerShape(999.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatNumber(group.effectiveGroupTotal)} / ${formatNumber(group.targetCount)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatGroupMemberCount(group.memberCount)} · Code ${group.inviteCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onCardSubtext
                )
            }
        }
    }
}

internal fun formatGroupMemberCount(count: Int): String {
    return "$count ${if (count == 1) "member" else "members"}"
}

@Composable
fun GroupDetailScreen(
    group: GroupItem?,
    currentUserId: String,
    currentDate: LocalDate,
    isRefreshing: Boolean = false,
    isFloatingButtonEnabled: Boolean? = null,
    onToggleFloatingButton: ((GroupItem) -> Unit)? = null,
    onScreenOpen: () -> Unit = {},
    onScreenClose: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onInviteWhatsApp: (GroupItem) -> Unit,
    onRenameGroup: (groupId: String, newName: String) -> Unit = { _, _ -> },
    onLeaveGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit = {},
    onAdjustMainGroupCount: (groupId: String, delta: Long) -> Unit = { _, _ -> },
    onIncrementCustomZikr: (groupId: String, delta: Long) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val palette = AppThemeColors.palette
    val floatingState by FloatingCounterManager.state.collectAsStateWithLifecycle()

    val activeGroupId = group?.id
    androidx.compose.runtime.DisposableEffect(activeGroupId) {
        if (!activeGroupId.isNullOrBlank()) {
            onScreenOpen()
        }
        onDispose {
            if (!activeGroupId.isNullOrBlank()) {
                onScreenClose()
            }
        }
    }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showMainCustomDialog by remember { mutableStateOf(false) }
    var mainCustomAmountText by remember { mutableStateOf("") }
    var mainCustomIsReduce by remember { mutableStateOf(false) }
    var copiedFeedbackTick by remember { mutableIntStateOf(0) }
    var showCopiedBanner by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedbackTick) {
        if (copiedFeedbackTick > 0) {
            showCopiedBanner = true
            delay(2000L)
            showCopiedBanner = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        if (group == null) {
            var didAutoReturn by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (!didAutoReturn) {
                    didAutoReturn = true
                    onBack()
                }
            }
            return@Box
        }

        val todayKey = currentDate.toString()
        val isCreator = currentUserId.isNotBlank() && group.creatorUserId == currentUserId
        val rankedMembers = remember(group, todayKey) { group.rankedMembers(todayKey) }
        val celebrations = remember(group, currentUserId, currentDate) {
            GroupLogic.computeCelebrations(group, currentUserId, currentDate)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("group_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.onCanvas
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.onCanvas,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = group.effectiveZikrName,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onCanvasSubtext,
                        modifier = Modifier.testTag("group_detail_zikr_label")
                    )
                }
                if (isCreator) {
                    IconButton(
                        onClick = {
                            renameInput = group.name
                            showRenameDialog = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("group_edit_name_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Rename group",
                            tint = palette.onCanvas,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("group_detail_refresh_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = palette.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh group",
                            tint = palette.onCanvas,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Group Total + Member Count Card
            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GROUP TOTAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onCardSubtext
                        )
                        Surface(
                            color = palette.accent,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = formatGroupMemberCount(group.memberCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("group_member_count")
                            )
                        }
                    }

                    Text(
                        text = formatNumber(group.effectiveGroupTotal),
                        style = MaterialTheme.typography.displayMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("group_total_value")
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(palette.track, RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    group.progressFraction.coerceAtLeast(
                                        if (group.effectiveGroupTotal > 0L) 0.02f else 0f
                                    )
                                )
                                .fillMaxHeight()
                                .background(palette.accent, RoundedCornerShape(999.dp))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target: ${formatNumber(group.targetCount)} (${group.progressPercentFormatted})",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onCardSubtext,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .testTag("group_target_text")
                        )
                        Surface(
                            color = palette.subtleSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
                                .minimumInteractiveComponentSize()
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Copy invite code"
                                ) {
                                    if (copyInviteCodeToClipboard(context, group.inviteCode)) {
                                        copiedFeedbackTick++
                                    }
                                }
                                .semantics {
                                    contentDescription = "Invite code ${group.inviteCode}, tap to copy"
                                }
                                .testTag("group_invite_code_label")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Code: ${group.inviteCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onCard,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = palette.onCard,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Counts added or reduced here update only this group and your contribution inside it — separate from your personal Durood, global totals, and badges.",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onCardSubtext
                    )
                }
            }

            BlackPillButton(
                text = "+ Custom",
                onClick = {
                    mainCustomIsReduce = false
                    mainCustomAmountText = ""
                    showMainCustomDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_main_custom_button")
            )

            WhiteBorderPillButton(
                text = "Invite on WhatsApp",
                onClick = { onInviteWhatsApp(group) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_invite_whatsapp_button")
            )

            val groupFloatingEnabled = isFloatingButtonEnabled ?: floatingState.isGroupRunning(group.id)
            GroupFloatingButtonToggleRow(
                groupName = group.name,
                isEnabled = groupFloatingEnabled,
                onToggle = {
                    if (onToggleFloatingButton != null) {
                        onToggleFloatingButton(group)
                    } else {
                        FloatingCounterManager.toggleGroupFloatingButton(context, group)
                    }
                }
            )

            if (celebrations.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_celebrations_section"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CELEBRATIONS & REPORTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCanvasSubtext
                    )
                    celebrations.forEach { notice ->
                        GroupCelebrationCard(notice = notice)
                    }
                }
            }

            // Ranked Member Growth List (Name + Today + Lifetime Total, most-read first)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_leaderboard_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MEMBER RANKING (MOST READ FIRST)",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onCanvasSubtext
                )
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        rankedMembers.forEachIndexed { index, member ->
                            GroupMemberLeaderboardRow(
                                rank = index + 1,
                                member = member,
                                todayKey = todayKey,
                                isSelf = member.userId == currentUserId
                            )
                            if (index < rankedMembers.lastIndex) {
                                HorizontalDivider(color = palette.hairline, thickness = 1.dp)
                            }
                        }
                    }
                }
            }

            WhiteBorderPillButton(
                text = "Leave Group",
                onClick = { showLeaveConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_leave_button")
            )

            if (isCreator) {
                BlackPillButton(
                    text = "Delete Group",
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_delete_button")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        AnimatedVisibility(
            visible = showCopiedBanner,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(999.dp))
                    .background(palette.accent, RoundedCornerShape(999.dp))
                    .testTag("group_invite_copied_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Copied",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.onAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showLeaveConfirm && group != null) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                .testTag("group_leave_confirm_dialog"),
            title = {
                Text(
                    text = "Leave ${group.name}?",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("group_leave_dialog_title")
                )
            },
            text = {
                Text(
                    "Leaving frees a group slot (up to ${GroupLogic.MAX_GROUPS_PER_USER} groups). You can rejoin later with the invite code.",
                    color = palette.onCardSubtext
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        onLeaveGroup(group.id)
                    },
                    modifier = Modifier.testTag("group_leave_confirm_button")
                ) {
                    Text("Leave", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveConfirm = false },
                    modifier = Modifier.testTag("group_leave_cancel_button")
                ) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
            }
        )
    }

    if (showMainCustomDialog && group != null) {
        val parsedAmount = mainCustomAmountText.toLongOrNull() ?: 0L
        val canSubmitMainCustom = parsedAmount > 0L
        AlertDialog(
            onDismissRequest = { showMainCustomDialog = false },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                .testTag("group_main_custom_dialog"),
            title = {
                Text(
                    text = if (mainCustomIsReduce) "Reduce Group Count" else "Add to ${group.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("group_main_custom_dialog_title")
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !mainCustomIsReduce,
                            onClick = { mainCustomIsReduce = false },
                            label = { Text("Add") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.cardSurface,
                                labelColor = palette.onCard,
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = palette.onAccent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = !mainCustomIsReduce,
                                borderColor = palette.hairline,
                                selectedBorderColor = palette.accent
                            ),
                            modifier = Modifier.testTag("group_main_mode_add")
                        )
                        FilterChip(
                            selected = mainCustomIsReduce,
                            onClick = { mainCustomIsReduce = true },
                            label = { Text("Reduce") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.cardSurface,
                                labelColor = palette.onCard,
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = palette.onAccent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = mainCustomIsReduce,
                                borderColor = palette.hairline,
                                selectedBorderColor = palette.accent
                            ),
                            modifier = Modifier.testTag("group_main_mode_reduce")
                        )
                    }
                    OutlinedTextField(
                        value = mainCustomAmountText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 7) {
                                mainCustomAmountText = input
                            }
                        },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_main_custom_input")
                    )
                    Text(
                        text = if (mainCustomIsReduce) {
                            "Lowers only your count in this group (never below 0). Personal Durood is untouched."
                        } else {
                            "Adds straight to this group's total and your group contribution only."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onCardSubtext
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSubmitMainCustom,
                    onClick = {
                        if (parsedAmount > 0L) {
                            val signedDelta = if (mainCustomIsReduce) -parsedAmount else parsedAmount
                            if (group.type == GroupType.CUSTOM_ZIKR) {
                                onIncrementCustomZikr(group.id, signedDelta)
                            } else {
                                onAdjustMainGroupCount(group.id, signedDelta)
                            }
                        }
                        mainCustomAmountText = ""
                        showMainCustomDialog = false
                    },
                    modifier = Modifier.testTag("group_main_custom_confirm")
                ) {
                    Text(
                        text = if (mainCustomIsReduce) "Reduce" else "Add",
                        color = if (canSubmitMainCustom) palette.accent else palette.onCardSubtext,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mainCustomAmountText = ""
                        showMainCustomDialog = false
                    },
                    modifier = Modifier.testTag("group_main_custom_cancel")
                ) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
            }
        )
    }

    if (showRenameDialog && group != null) {
        val trimmedRename = renameInput.trim()
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                .testTag("group_rename_dialog"),
            title = {
                Text(
                    text = "Rename Group",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("group_rename_dialog_title")
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it.take(60) },
                    label = { Text("Group Name") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_rename_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        onRenameGroup(group.id, trimmedRename)
                    },
                    enabled = trimmedRename.isNotEmpty(),
                    modifier = Modifier.testTag("group_rename_confirm_button")
                ) {
                    Text(
                        "Save",
                        color = if (trimmedRename.isNotEmpty()) palette.accent else palette.onCardSubtext,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false },
                    modifier = Modifier.testTag("group_rename_cancel_button")
                ) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
            }
        )
    }

    if (showDeleteConfirm && group != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                .testTag("group_delete_confirm_dialog"),
            title = {
                Text(
                    text = "Delete ${group.name}?",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onCard,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("group_delete_dialog_title")
                )
            },
            text = {
                Text(
                    "Deleting removes this group permanently for all members. Members' personal Durood totals will never be touched.",
                    color = palette.onCardSubtext
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteGroup(group.id)
                    },
                    modifier = Modifier.testTag("group_delete_confirm_button")
                ) {
                    Text("Delete", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("group_delete_cancel_button")
                ) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
            }
        )
    }
}

@Composable
private fun GroupFloatingButtonToggleRow(
    groupName: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch, onClick = onToggle)
            .semantics {
                contentDescription = "Floating button for $groupName"
                stateDescription = if (isEnabled) "On" else "Off"
            }
            .testTag("group_floating_button_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                ) {
                    SmallOneCroreLogoGlyph(modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Floating button",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Count for this group over any app",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCardSubtext
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                color = if (isEnabled) palette.accent else palette.cardSurface,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (isEnabled) palette.accent else palette.hairline,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggle)
                    .testTag("group_floating_button_toggle")
            ) {
                Text(
                    text = if (isEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) palette.onAccent else palette.onCard,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupMemberLeaderboardRow(
    rank: Int,
    member: GroupMemberProgress,
    todayKey: String,
    isSelf: Boolean
) {
    val palette = AppThemeColors.palette
    val todayVal = member.effectiveTodayCount(todayKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("group_member_row_${member.userId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(if (rank <= 3) palette.accent else palette.subtleSurface, CircleShape)
                .border(1.dp, if (rank <= 3) palette.accent else palette.hairline, CircleShape)
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelSmall,
                color = if (rank <= 3) palette.onAccent else palette.onCard,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "${member.displayName} (You)" else member.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onCard,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Today: ${formatNumber(todayVal)}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onCardSubtext
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatNumber(member.lifetimeTotalCount),
                style = MaterialTheme.typography.titleSmall,
                color = palette.onCard,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "total",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onCardSubtext
            )
        }
    }
}

@Composable
private fun GroupCelebrationCard(notice: GroupCelebrationNotice) {
    val palette = AppThemeColors.palette
    val isPrivate = notice.type == GroupCelebrationType.WEEKLY_TOP_3_PRIVATE
    Surface(
        color = if (isPrivate) palette.accent else palette.subtleSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.strongBorder, RoundedCornerShape(14.dp))
            .testTag("group_celebration_card_${notice.type.wireValue}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = if (isPrivate) palette.onAccent else palette.onCard,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPrivate) palette.onAccent else palette.onCard,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPrivate) palette.onAccent.copy(alpha = 0.88f) else palette.onCard
                )
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: GroupType, zikrName: String?, targetCount: Long) -> Unit
) {
    val palette = AppThemeColors.palette
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(GroupType.MAIN_DUROOD) }
    var zikrName by remember { mutableStateOf("") }
    var targetInput by remember { mutableStateOf(GroupLogic.DEFAULT_UNIFIED_GROUP_TARGET.toString()) }

    val parsedTarget = targetInput.toLongOrNull() ?: 0L
    val canSubmit = name.trim().isNotEmpty() &&
        parsedTarget > 0L &&
        (selectedType == GroupType.MAIN_DUROOD || zikrName.trim().isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
            .testTag("create_group_dialog"),
        title = {
            Text(
                text = "Create Group",
                style = MaterialTheme.typography.titleMedium,
                color = palette.onCard,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("create_group_dialog_title")
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Group name") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == GroupType.MAIN_DUROOD,
                        onClick = { selectedType = GroupType.MAIN_DUROOD },
                        label = { Text("For Durood") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = palette.cardSurface,
                            labelColor = palette.onCard,
                            selectedContainerColor = palette.accent,
                            selectedLabelColor = palette.onAccent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == GroupType.MAIN_DUROOD,
                            borderColor = palette.hairline,
                            selectedBorderColor = palette.accent
                        ),
                        modifier = Modifier.testTag("create_group_type_main")
                    )
                    FilterChip(
                        selected = selectedType == GroupType.CUSTOM_ZIKR,
                        onClick = { selectedType = GroupType.CUSTOM_ZIKR },
                        label = { Text("Other Zikr") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = palette.cardSurface,
                            labelColor = palette.onCard,
                            selectedContainerColor = palette.accent,
                            selectedLabelColor = palette.onAccent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == GroupType.CUSTOM_ZIKR,
                            borderColor = palette.hairline,
                            selectedBorderColor = palette.accent
                        ),
                        modifier = Modifier.testTag("create_group_type_zikr")
                    )
                }

                Text(
                    text = "TARGET",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onCardSubtext
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("50K", "50000", "create_group_target_50k"),
                        Triple("1L", "100000", "create_group_target_1l"),
                        Triple("10L", "1000000", "create_group_target_10l")
                    ).forEach { (label, value, tag) ->
                        FilterChip(
                            selected = targetInput == value,
                            onClick = { targetInput = value },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.cardSurface,
                                labelColor = palette.onCard,
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = palette.onAccent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = targetInput == value,
                                borderColor = palette.hairline,
                                selectedBorderColor = palette.accent
                            ),
                            modifier = Modifier.testTag(tag)
                        )
                    }
                }
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 9) {
                            targetInput = input
                        }
                    },
                    label = { Text("Group target") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_target_input")
                )

                if (selectedType == GroupType.CUSTOM_ZIKR) {
                    OutlinedTextField(
                        value = zikrName,
                        onValueChange = { zikrName = it.take(60) },
                        label = { Text("Zikr name (e.g. سُبْحَانَ اللَّهِ)") },
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_group_zikr_name_input")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val target = (targetInput.toLongOrNull() ?: GroupLogic.DEFAULT_UNIFIED_GROUP_TARGET)
                        .coerceIn(1L, 100_000_000L)
                    onCreate(
                        name.trim(),
                        selectedType,
                        if (selectedType == GroupType.CUSTOM_ZIKR) zikrName.trim() else null,
                        target
                    )
                },
                modifier = Modifier.testTag("create_group_submit_button")
            ) {
                Text(
                    "Create",
                    color = if (canSubmit) palette.accent else palette.onCardSubtext,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("create_group_cancel_button")
            ) {
                Text("Cancel", color = palette.onCardSubtext)
            }
        }
    )
}

@Composable
fun GroupCelebrationPopupDialog(
    notice: GroupCelebrationNotice,
    onDismiss: () -> Unit
) {
    val palette = AppThemeColors.palette
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.scrim)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .testTag("group_celebration_popup_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = palette.accent,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = notice.groupName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("group_celebration_popup_close")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close group celebration",
                                tint = palette.onCard,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = notice.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onCard,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("group_celebration_popup_title")
                    )

                    Text(
                        text = notice.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCardSubtext
                    )

                    BlackPillButton(
                        text = "الْحَمْدُ لِلَّهِ",
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_celebration_popup_confirm")
                    )
                }
            }
        }
    }
}

internal fun extractCopyableInviteCode(inviteCode: String): String = inviteCode.trim()

internal fun copyInviteCodeToClipboard(context: Context, inviteCode: String): Boolean {
    val cleanCode = extractCopyableInviteCode(inviteCode)
    if (cleanCode.isEmpty()) return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Group invite code", cleanCode))
    return true
}
