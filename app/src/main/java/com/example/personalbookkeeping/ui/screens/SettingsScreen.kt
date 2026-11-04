package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun SettingsScreen(
    onImportCsv: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onConfigureAi: () -> Unit,
    aiConfigured: Boolean,
    onConfigureGit: () -> Unit,
    syncConfigured: Boolean,
    syncStatus: String?,
    onSyncNow: () -> Unit,
    objectConfigured: Boolean,
    objectStatus: String,
    onConfigureObject: () -> Unit,
    onTestObject: () -> Unit,
    onSyncObject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFormatHelp by rememberSaveable { mutableStateOf(false) }
    if (showFormatHelp) FileFormatHelpDialog(onDismiss = { showFormatHelp = false })
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text("设置", color = LedgerColors.Ink, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "管理账目文件和可选同步",
                modifier = Modifier.padding(top = 4.dp),
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 记账", color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
                Text(if (aiConfigured) "已配置大模型，可从总览输入文字账单。" else "配置自己的大模型服务，一段文字自动记入多笔收支。",
                    color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onConfigureAi, modifier = Modifier.fillMaxWidth()) {
                    Text(if (aiConfigured) "修改大模型设置" else "配置大模型")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("数据文件", modifier = Modifier.weight(1f), color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showFormatHelp = true }) { Text("格式说明") }
            }
            Text(
                text = "CSV 和 JSON 使用本软件固定格式，导出的文件可以重新导入。",
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImportCsv, modifier = Modifier.weight(1f)) {
                    Text("导入 CSV", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) {
                    Text("导入 JSON", modifier = Modifier.padding(start = 6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) {
                    Text("导出 CSV", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) {
                    Text("导出 JSON", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        HorizontalDivider(color = LedgerColors.Divider)

        Surface(
            color = LedgerColors.Surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Git 同步", color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (syncConfigured) {
                        syncStatus ?: "已配置远端，点击后手动同步"
                    } else {
                        "未配置远端。当前只保存在本机，也可以继续记账。"
                    },
                    color = LedgerColors.MutedInk,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                if (syncConfigured) {
                    Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
                        Text("立即同步")
                    }
                    OutlinedButton(onClick = onConfigureGit, modifier = Modifier.fillMaxWidth()) {
                        Text("修改 Git 设置")
                    }
                } else {
                    OutlinedButton(onClick = onConfigureGit, modifier = Modifier.fillMaxWidth()) {
                        Text("配置 Git 远端")
                    }
                }
                Text(
                    text = "建议使用私有仓库保存账目。",
                    color = LedgerColors.MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("对象存储同步", color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
                Text(objectStatus, color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
                if (objectConfigured) {
                    Button(onClick = onSyncObject, modifier = Modifier.fillMaxWidth()) { Text("立即同步到对象存储") }
                    OutlinedButton(onClick = onTestObject, modifier = Modifier.fillMaxWidth()) { Text("测试连接（只读取列表）") }
                }
                OutlinedButton(onClick = onConfigureObject, modifier = Modifier.fillMaxWidth()) {
                    Text(if (objectConfigured) "修改对象存储设置" else "配置对象存储")
                }
                Text("请使用私有存储桶和限定前缀的访问凭证。同步由你手动触发。",
                    color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
