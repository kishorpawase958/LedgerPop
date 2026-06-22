package app.ledgerpop.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    // Report dialog
    if (uiState.showReportDialog && uiState.reportingEntry != null) {
        ReportDialog(
            entry = uiState.reportingEntry!!,
            note = uiState.reportNote,
            onNoteChange = { viewModel.onReportNoteChange(it) },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (uiState.isLoading) {
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
                        isExpanded = uiState.expandedEntryId == entry.id,
                        onToggleExpand = { viewModel.toggleExpand(entry.id) },
                        onReport = { viewModel.showReportDialog(entry) },
                        onClearReport = { viewModel.clearReport(entry.id) }
                    )
                }
            }
        }
    }
}

// ── Audit Entry Card ──────────────────────────────────────────────────────────

@Composable
private fun AuditEntryCard(
    entry: SmsAuditEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReported)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

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
                Text("·",
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
                    Text("·",
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