package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.transfer.ImportPreview
import com.example.personalbookkeeping.ui.components.LedgerEntryRow
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun ImportPreviewScreen(
    preview: ImportPreview,
    committing: Boolean,
    onBack: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "确认导入",
                modifier = Modifier.padding(start = 4.dp),
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Text(
            text = if (preview.issues.isEmpty()) {
                "找到 ${preview.entries.size} 笔记录${if (preview.repeatedRows > 0) "，文件内重复 ${preview.repeatedRows} 笔" else ""}"
            } else {
                "文件有 ${preview.issues.size} 处问题，未写入任何记录"
            },
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            color = if (preview.issues.isEmpty()) LedgerColors.MutedInk else LedgerColors.Expense,
            style = MaterialTheme.typography.bodyMedium
        )

        if (preview.issues.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(preview.issues.take(100)) { issue ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = LedgerColors.SoftTerracotta,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val location = buildList {
                                issue.row?.let { add("第 $it 行") }
                                issue.field?.let { add(it) }
                            }.joinToString(" · ")
                            if (location.isNotBlank()) {
                                Text(location, color = LedgerColors.Expense, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(issue.reason, color = LedgerColors.Ink, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(preview.entries.take(100), key = { it.id }) { entry ->
                    LedgerEntryRow(entry = entry, onClick = null)
                    HorizontalDivider(color = LedgerColors.Divider)
                }
                if (preview.entries.size > 100) {
                    item {
                        Text(
                            text = "另有 ${preview.entries.size - 100} 笔记录未展开显示",
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = LedgerColors.MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !committing) {
                Text("取消")
            }
            Button(
                onClick = onCommit,
                modifier = Modifier.weight(1f),
                enabled = preview.canImport && !committing && preview.entries.isNotEmpty()
            ) {
                if (committing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("正在导入")
                } else {
                    Text("导入 ${preview.entries.size} 笔")
                }
            }
        }
    }
}
