package app.ledgerpop.screens.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsAuditEntity
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.local.SpamKeywordEntity
import app.ledgerpop.data.parser.SmsParser
import app.ledgerpop.ui.state.AuditFilter
import app.ledgerpop.ui.viewmodel.SmsAuditViewModel
import app.ledgerpop.utils.AmountUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmsAuditScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: SmsAuditViewModel = viewModel(
        factory = SmsAuditViewModel.factory(db)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Bulk report dialog
    if (uiState.showBulkReportDialog) {
        BulkReportDialog(
            selectedCount = uiState.selectedAuditIds.size,
            note = uiState.reportNote,
            onNoteChange = { newNote -> viewModel.onReportNoteChange(newNote) },
            onSubmitFalsePositive = { viewModel.applyBulkReport("FALSE_POSITIVE") },
            onSubmitFalseNegative = { viewModel.applyBulkReport("FALSE_NEGATIVE") },
            onDismiss = { viewModel.hideBulkReportDialog() }
        )
    }

    // Report dialog
    if (uiState.showReportDialog && uiState.reportingEntry != null) {
        ReportDialog(
            entry = uiState.reportingEntry!!,
            note = uiState.reportNote,
            onNoteChange = { newNote -> viewModel.onReportNoteChange(newNote) },
            onSubmitFalsePositive = { viewModel.submitReport("FALSE_POSITIVE") },
            onSubmitFalseNegative = { viewModel.submitReport("FALSE_NEGATIVE") },
            onDismiss = { viewModel.hideReportDialog() }
        )
    }

    // Similar entries dialog (Retroactive correction)
    if (uiState.showSimilarEntriesDialog) {
        SimilarEntriesDialog(
            entries = uiState.similarEntries,
            selectedIds = uiState.selectedSimilarIds,
            reportType = uiState.retroactiveReportType,
            onToggleSelection = { viewModel.toggleSimilarSelection(it) },
            onConfirm = { viewModel.applyRetroactiveCorrections() },
            onDismiss = { viewModel.hideSimilarEntriesDialog() }
        )
    }

    // Clear reports dialog (Batch clear)
    if (uiState.showClearSimilarDialog) {
        ClearSimilarDialog(
            entries = uiState.similarEntries,
            selectedIds = uiState.selectedSimilarIds,
            onToggleSelection = { viewModel.toggleSimilarSelection(it) },
            onConfirm = { viewModel.applyBatchClear() },
            onDismiss = { viewModel.hideClearSimilarDialog() }
        )
    }

    // Add Spam Keyword Dialog
    if (uiState.showAddSpamKeywordDialog) {
        AddSpamKeywordDialog(
            onConfirm = { keyword -> viewModel.addSpamKeyword(keyword) },
            onDismiss = { viewModel.hideAddSpamKeywordDialog() }
        )
    }

    // Edit Spam Keyword Dialog
    if (uiState.showEditSpamKeywordDialog && uiState.editingSpamKeyword != null) {
        val entity = uiState.editingSpamKeyword!!
        EditSpamKeywordDialog(
            initialKeyword = entity.keyword,
            onConfirm = { newKeyword -> viewModel.editSpamKeyword(entity, newKeyword) },
            onDelete = {
                viewModel.deleteSpamKeyword(entity)
                viewModel.hideEditSpamKeywordDialog()
            },
            onDismiss = { viewModel.hideEditSpamKeywordDialog() }
        )
    }

    // Retroactive Spam Bulk Update Dialog
    if (uiState.showRetroactiveSpamDialog) {
        RetroactiveSpamBulkDialog(
            keyword = uiState.retroactiveSpamKeyword,
            matchedTransactions = uiState.matchedSpamTransactions,
            selectedIds = uiState.selectedSpamTxnIds,
            onToggleSelection = { viewModel.toggleRetroactiveSpamSelection(it) },
            onSelectAll = { viewModel.selectAllRetroactiveSpam() },
            onConfirmMarkWronglyImported = { viewModel.applyRetroactiveSpamAction(true) },
            onDismiss = { viewModel.applyRetroactiveSpamAction(false) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Top Progress Bar ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.loadingProgress != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))) {
                LinearProgressIndicator(
                    progress = { uiState.loadingProgress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    trackColor = Color.Transparent,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Processing ${uiState.processingCurrent} of ${uiState.processingCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${((uiState.loadingProgress ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.isSelectionMode) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                }
                Text(
                    "${uiState.selectedAuditIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.selectAll() }) {
                    Icon(
                        Icons.Rounded.SelectAll,
                        contentDescription = "Select all",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.reparseSelected() }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Reload selected",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.showBulkReportDialog() }) {
                    Icon(
                        Icons.Rounded.Flag,
                        contentDescription = "Report selected",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "SMS Audit Log",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleSpamKeywordsSection() }) {
                    Icon(
                        Icons.Rounded.Block,
                        contentDescription = "Spam Keywords",
                        modifier = Modifier.size(22.dp),
                        tint = if (uiState.showSpamKeywordsSection || uiState.spamKeywords.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.reparseAll() }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh and re-parse all",
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { 
                    // Toogle selection mode manually if needed, or just let checkboxes work
                    // But we'll use this as an entry point
                    if (uiState.filteredEntries.isNotEmpty()) {
                        viewModel.toggleSelection(uiState.filteredEntries.first().id)
                    }
                }) {
                    Icon(
                        Icons.Rounded.LibraryAddCheck,
                        contentDescription = "Select messages",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── Spam Keywords Management Section ──────────────────────────────────
        AnimatedVisibility(
            visible = uiState.showSpamKeywordsSection,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SpamKeywordsCard(
                keywords = uiState.spamKeywords,
                onAddClick = { viewModel.showAddSpamKeywordDialog() },
                onEditClick = { viewModel.showEditSpamKeywordDialog(it) },
                onDeleteClick = { viewModel.deleteSpamKeyword(it) },
                onRestoreDefaultsClick = { viewModel.restoreDefaultSpamKeywords() }
            )
        }

        if (uiState.isLoading && uiState.loadingProgress == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // ── Stats row ─────────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                StatChip(
                    label = "Total",
                    count = uiState.totalSeen,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                StatChip(
                    label = "Imported",
                    count = uiState.totalImported,
                    color = Color(0xFF00B894)
                )
            }
            item {
                StatChip(
                    label = "Skipped",
                    count = uiState.totalSkipped,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                StatChip(
                    label = "Failed",
                    count = uiState.totalParseFailed,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (uiState.totalReported > 0) {
                item {
                    StatChip(
                        label = "Reported",
                        count = uiState.totalReported,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // ── Search ────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search sender, body…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Filter chips ──────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AuditFilter.entries) { filter ->
                FilterChip(
                    selected = uiState.selectedFilter == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    label = {
                        Text(
                            filter.name
                                .replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                        )
                    },
                    leadingIcon = if (uiState.selectedFilter == filter) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Results count ─────────────────────────────────────────────────────
        Text(
            text = "${uiState.filteredEntries.size} message${if (uiState.filteredEntries.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        // ── List ──────────────────────────────────────────────────────────────
        if (uiState.filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No messages found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Import SMS first via Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.filteredEntries,
                    key = { it.id }
                ) { entry ->
                    AuditEntryCard(
                        entry = entry,
                        isExpanded = uiState.expandedAuditIds.contains(entry.id),
                        isSelected = uiState.selectedAuditIds.contains(entry.id),
                        isSelectionMode = uiState.isSelectionMode,
                        onToggleExpand = { viewModel.toggleExpand(entry.id) },
                        onToggleSelect = { viewModel.toggleSelection(entry.id) },
                        onReport = { viewModel.showReportDialog(entry) },
                        onClearReport = { viewModel.clearReport(entry.id) }
                    )
                }
            }
        }
    }
}

// ── Audit Entry Card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuditEntryCard(
    entry: SmsAuditEntity,
    isExpanded: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onReport: () -> Unit,
    onClearReport: () -> Unit
) {
    val statusColor = when (entry.status) {
        "IMPORTED"     -> Color(0xFF00B894)
        "SKIPPED"      -> MaterialTheme.colorScheme.onSurfaceVariant
        "PARSE_FAILED" -> MaterialTheme.colorScheme.error
        else           -> MaterialTheme.colorScheme.outline
    }

    val statusIcon = when (entry.status) {
        "IMPORTED"     -> Icons.Rounded.CheckCircle
        "SKIPPED"      -> Icons.Rounded.RemoveCircleOutline
        "PARSE_FAILED" -> Icons.Rounded.ErrorOutline
        else           -> Icons.AutoMirrored.Rounded.Help
    }

    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { SimpleDateFormat("d MMM yy, h:mm a", locale) }
    val isReported = entry.reportType.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onToggleExpand()
                },
                onLongClick = {
                    if (!isSelectionMode) onToggleSelect()
                }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isReported -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Checkbox for selection ────────────────────────────────────────
            if (isSelectionMode || isSelected) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {

                // ── Header row ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status icon
                    Icon(
                        statusIcon,
                        contentDescription = entry.status,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))

                    // Sender
                    Text(
                        text = entry.sender,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Reported badge
                    if (isReported) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = if (entry.reportType == "FALSE_POSITIVE")
                                    "⚠ wrongly imported"
                                else
                                    "⚠ wrongly skipped",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Date + status label ───────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sdf.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.status.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                    // Show parsed amount if imported
                    if (entry.status == "IMPORTED" && entry.parsedAmount > 0) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${entry.parsedType} ₹${AmountUtils.formatAmount(entry.parsedAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.parsedType == "CREDIT")
                                Color(0xFF00B894)
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ── Skip reason ───────────────────────────────────────────────────
                if (entry.skipReason.isNotBlank() && entry.status != "IMPORTED") {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = entry.skipReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── SMS body (expandable) ─────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide message" else "Show message",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Rounded.ExpandLess
                        else
                            Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = entry.body,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .padding(10.dp)
                        )

                        // Report / clear report buttons
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isReported) {
                                // Show report note if any
                                if (entry.reportNote.isNotBlank()) {
                                    Text(
                                        text = "Note: ${entry.reportNote}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                TextButton(
                                    onClick = onClearReport,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Clear report",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            } else {
                                TextButton(onClick = onReport) {
                                    Icon(
                                        Icons.Rounded.Flag,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Report",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Report Dialog ─────────────────────────────────────────────────────────────

@Composable
private fun ReportDialog(
    entry: SmsAuditEntity,
    note: String,
    onNoteChange: (String) -> Unit,
    onSubmitFalsePositive: () -> Unit,
    onSubmitFalseNegative: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Report SMS",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Sender + snippet
                Text(
                    text = entry.sender,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entry.body.take(120) + if (entry.body.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .padding(10.dp)
                )

                HorizontalDivider()

                Text(
                    "What's wrong with this?",
                    style = MaterialTheme.typography.bodyMedium
                )

                // False positive button
                OutlinedButton(
                    onClick = onSubmitFalsePositive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Wrongly imported",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "This is not a real transaction",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // False negative button
                OutlinedButton(
                    onClick = onSubmitFalseNegative,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00B894)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF00B894).copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Wrongly skipped",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "This IS a real transaction",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Optional note
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Swiggy order payment") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Clear Similar Dialog ────────────────────────────────────────────────────

@Composable
private fun ClearSimilarDialog(
    entries: List<SmsAuditEntity>,
    selectedIds: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { SimpleDateFormat("d MMM, h:mm a", locale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Clear similar reports?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "We found ${entries.size} other messages with the same structure that were also reported. Clear reports for them too?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelection(entry.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(entry.id),
                                onCheckedChange = { onToggleSelection(entry.id) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = sdf.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = entry.body.take(80) + if (entry.body.length > 80) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Clear ${selectedIds.size + 1} reports")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Just this one")
            }
        }
    )
}

// ── Bulk Report Dialog ────────────────────────────────────────────────────────

@Composable
private fun BulkReportDialog(
    selectedCount: Int,
    note: String,
    onNoteChange: (String) -> Unit,
    onSubmitFalsePositive: () -> Unit,
    onSubmitFalseNegative: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Report $selectedCount messages",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Apply this correction to all $selectedCount selected messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text(
                    "What's wrong with these?",
                    style = MaterialTheme.typography.bodyMedium
                )

                // False positive button
                OutlinedButton(
                    onClick = onSubmitFalsePositive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Wrongly imported",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "These are not real transactions",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // False negative button
                OutlinedButton(
                    onClick = onSubmitFalseNegative,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00B894)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF00B894).copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Wrongly skipped",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "These ARE real transactions",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Optional note
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. System failed to recognize structure") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Stat Chip ─────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Similar Entries Dialog ──────────────────────────────────────────────────

@Composable
private fun SimilarEntriesDialog(
    entries: List<SmsAuditEntity>,
    selectedIds: Set<Int>,
    reportType: String,
    onToggleSelection: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { SimpleDateFormat("d MMM, h:mm a", locale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Apply to historical messages?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "We found ${entries.size} other messages with the same structure from ${entries.firstOrNull()?.sender}. Apply the same correction to them?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelection(entry.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(entry.id),
                                onCheckedChange = { onToggleSelection(entry.id) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = sdf.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = entry.body.take(80) + if (entry.body.length > 80) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply to ${selectedIds.size} messages")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}

// ── Add Spam Keyword Dialog ───────────────────────────────────────────────────

@Composable
private fun AddSpamKeywordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keywordText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Add Spam Keyword",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Messages containing this keyword will be excluded from automatic transaction import.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spam keyword or phrase") },
                    placeholder = { Text("e.g. reward points, loan offer") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keywordText) },
                enabled = keywordText.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add & Scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Retroactive Spam Bulk Update Dialog ────────────────────────────────────────

@Composable
private fun RetroactiveSpamBulkDialog(
    keyword: String,
    matchedTransactions: List<SmsTransactionEntity>,
    selectedIds: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onConfirmMarkWronglyImported: () -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { SimpleDateFormat("d MMM yyyy, h:mm a", locale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    "Spam Keyword Matched",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Found ${matchedTransactions.size} imported transaction(s) matching '$keyword'",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Would you like to mark these existing transactions as wrongly imported and remove them from your transactions?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} of ${matchedTransactions.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onSelectAll) {
                        Text(if (selectedIds.size == matchedTransactions.size) "Deselect all" else "Select all")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(matchedTransactions) { txn ->
                        val isSelected = selectedIds.contains(txn.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelection(txn.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection(txn.id) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.merchant.ifBlank { txn.sender },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "₹${AmountUtils.formatAmount(txn.amount)} · ${sdf.format(Date(txn.transactionTime))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = txn.body.take(80) + if (txn.body.length > 80) "…" else "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmMarkWronglyImported,
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Mark as Wrongly Imported (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss / Keep")
            }
        }
    )
}

// ── Edit Spam Keyword Dialog ──────────────────────────────────────────────────

@Composable
private fun EditSpamKeywordDialog(
    initialKeyword: String,
    onConfirm: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var keywordText by remember { mutableStateOf(initialKeyword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Edit Spam Keyword",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Modify this keyword filter. Messages matching this keyword will be excluded from automatic transaction import.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spam keyword or phrase") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
                Button(
                    onClick = { onConfirm(keywordText) },
                    enabled = keywordText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Scan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Spam Keywords Card ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpamKeywordsCard(
    keywords: List<SpamKeywordEntity>,
    onAddClick: () -> Unit,
    onEditClick: (SpamKeywordEntity) -> Unit,
    onDeleteClick: (SpamKeywordEntity) -> Unit,
    onRestoreDefaultsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Spam Keywords (${keywords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onRestoreDefaultsClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Restore Default Keywords",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    FilledTonalButton(
                        onClick = onAddClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Text(
                "Tap any keyword chip to edit or delete it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (keywords.isEmpty()) {
                Text(
                    "No spam keywords configured. Tap 'Add' to add custom filters or the restore icon to reload system defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keywords.forEach { item ->
                        InputChip(
                            selected = false,
                            onClick = { onEditClick(item) },
                            label = {
                                Text(
                                    item.keyword,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Delete keyword",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteClick(item) }
                                )
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }
    }
}