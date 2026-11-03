package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.sync.GitConflictChoice
import com.example.personalbookkeeping.data.sync.GitSyncConflict
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.components.formatFen
import com.example.personalbookkeeping.ui.theme.LedgerColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GitConflictScreen(
    conflicts: List<GitSyncConflict>,
    processingRecordId: String?,
    resolutionReady: Boolean,
    onChoose: (String, GitConflictChoice) -> Unit,
    onContinueSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "同步冲突",
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Text(
            text = "同一笔记录在两台设备上都发生了修改。两份内容都已保留，请选择要继续使用的版本。",
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            color = LedgerColors.MutedInk,
            style = MaterialTheme.typography.bodyMedium
        )

        if (conflicts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(conflicts, key = GitSyncConflict::recordId) { conflict ->
                    ConflictCard(
                        conflict = conflict,
                        busy = processingRecordId == conflict.recordId,
                        enabled = processingRecordId == null,
                        onChoose = { choice -> onChoose(conflict.recordId, choice) }
                    )
                }
            }
        } else if (resolutionReady) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "冲突已处理",
                    color = LedgerColors.Ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "继续完成合并并推送到远端。",
                    modifier = Modifier.padding(top = 6.dp),
                    color = LedgerColors.MutedInk,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (resolutionReady && conflicts.isEmpty()) {
            Button(
                onClick = onContinueSync,
                enabled = processingRecordId == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (processingRecordId != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                }
                Text("继续同步")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConflictCard(
    conflict: GitSyncConflict,
    busy: Boolean,
    enabled: Boolean,
    onChoose: (GitConflictChoice) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LedgerColors.Surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = conflict.localVersion?.category ?: conflict.remoteVersion?.category ?: "已删除记录",
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "记录编号 ${conflict.recordId.take(8)}",
                modifier = Modifier.padding(top = 3.dp),
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LedgerColors.Divider)
            VersionSummary("本机版本", conflict.localVersion, LedgerColors.Green)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LedgerColors.Divider)
            VersionSummary("远端版本", conflict.remoteVersion, LedgerColors.Expense)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onChoose(GitConflictChoice.LOCAL) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (conflict.localVersion == null) "保留本机删除" else "保留本机版本")
                }
                Button(
                    onClick = { onChoose(GitConflictChoice.REMOTE) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(if (conflict.remoteVersion == null) "保留远端删除" else "保留远端版本")
                }
            }
        }
    }
}

@Composable
private fun VersionSummary(label: String, entry: LedgerEntry?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        if (entry == null || entry.deletedAt != null) {
            Text("此版本已删除这笔记录", color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
        } else {
            val sign = if (entry.type == LedgerType.INCOME) "+" else "−"
            Text(
                text = "$sign¥${formatFen(entry.amountFen)} · ${entry.category}",
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = buildString {
                    append(entry.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)))
                    if (entry.note.isNotBlank()) append(" · ${entry.note}")
                },
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
