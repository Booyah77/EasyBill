package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.sync.ObjectStorageConflict

@Composable
fun ObjectStorageConflictScreen(
    conflicts: List<ObjectStorageConflict>,
    working: Boolean,
    onChoose: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("选择账目版本", style = MaterialTheme.typography.headlineSmall)
        Text("每条冲突必须明确选择一个版本。所有冲突处理完成后，账目才会一起更新。")
        OutlinedButton(onClick = onRefresh, enabled = !working, modifier = Modifier.fillMaxWidth()) {
            Text("刷新远端冲突")
        }
        conflicts.forEach { conflict ->
            Text("记录 ${conflict.recordId}", style = MaterialTheme.typography.titleMedium)
            conflict.revisions.forEach { revision ->
                val entry = revision.entry
                Button(onClick = { onChoose(conflict.recordId, revision.revisionId) }, enabled = !working,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("${if (entry.deletedAt == null) entry.type.name else "已删除"} · ${entry.amountFen / 100.0} · ${entry.category} · ${entry.date}\n${entry.note}\n版本 ${revision.revisionId.take(12)}")
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回设置") }
    }
}
