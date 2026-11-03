package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.ai.AiPhase
import com.example.personalbookkeeping.data.ai.AiFailure
import com.example.personalbookkeeping.data.ai.MAX_AI_INPUT_CHARS
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.AiUiState
import com.example.personalbookkeeping.ui.components.formatFen
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun AiBookkeepingScreen(
    state: AiUiState,
    onDraftChange: (String) -> Unit,
    onRecognize: () -> Unit,
    onRetrySave: () -> Unit,
    onConfigure: () -> Unit,
    onCancel: () -> Unit,
    onNewDraft: () -> Unit,
    onSeeLedger: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(LedgerColors.Canvas).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = state.phase != AiPhase.SAVING) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("AI 记账", color = LedgerColors.Ink, style = MaterialTheme.typography.headlineSmall)
        }
        if (state.savedEntries.isNotEmpty()) {
            Surface(color = LedgerColors.SoftGreen, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("已记入账本 ${state.savedEntries.size} 笔", color = LedgerColors.Income, style = MaterialTheme.typography.titleLarge)
                    val income = state.savedEntries.filter { it.type == LedgerType.INCOME }.sumOf { it.amountFen }
                    val expense = state.savedEntries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amountFen }
                    Text("合计 ¥${formatFen(income + expense)} · 支出 ¥${formatFen(expense)} · 收入 ¥${formatFen(income)}",
                        color = LedgerColors.Ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.savedEntries.forEach { entry ->
                Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isIncome = entry.type == LedgerType.INCOME
                        Text("${if (isIncome) "收入" else "支出"} · ${entry.category} · ¥${formatFen(entry.amountFen)}",
                            color = if (isIncome) LedgerColors.Income else LedgerColors.Expense,
                            style = MaterialTheme.typography.titleMedium)
                        Text(entry.note, color = LedgerColors.Ink, style = MaterialTheme.typography.bodyLarge)
                        Text(entry.date.toString(), color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("如需更正，可到流水中编辑或删除。", color = LedgerColors.MutedInk)
            Button(onClick = onNewDraft, modifier = Modifier.fillMaxWidth()) { Text("再记一段") }
            OutlinedButton(onClick = onSeeLedger, modifier = Modifier.fillMaxWidth()) { Text("查看流水") }
        } else {
            Text("把几笔收支写在一起，一次识别并自动保存。", color = LedgerColors.Ink,
                style = MaterialTheme.typography.titleMedium)
            Text("例如：鸡腿10元晚上吃饭20手机壳10元", color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.draft,
                onValueChange = { if (it.length <= MAX_AI_INPUT_CHARS) onDraftChange(it) },
                enabled = !state.busy && !state.canRetrySave,
                label = { Text("账单文字") },
                placeholder = { Text("写下项目、金额，也可以注明收入和日期") },
                minLines = 6,
                supportingText = { Text("${state.draft.length}/$MAX_AI_INPUT_CHARS") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("点击后，本次文字、可用分类名称和当前日期会发送到你配置的大模型服务，可能消耗 API 额度。默认按今天的支出记账，只支持人民币。",
                color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodySmall)
            if (!state.loadingSettings && state.settings == null) {
                Text("尚未配置大模型。保存设置后会回到这段草稿。", color = LedgerColors.MutedInk)
            }
            state.error?.let { Text(it.message, color = LedgerColors.Expense, style = MaterialTheme.typography.bodyMedium) }
            if (state.error == null && state.settingsError != null) {
                Text(state.settingsError.message, color = LedgerColors.Expense, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(if (state.phase == AiPhase.SAVING) "正在整批保存，请稍候…" else "正在识别，校验通过后会自动记账…",
                    color = LedgerColors.MutedInk)
                if (state.phase == AiPhase.RECOGNIZING) {
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消识别") }
                }
            } else if (state.canRetrySave) {
                Button(onClick = onRetrySave, modifier = Modifier.fillMaxWidth()) { Text("重试保存本次结果") }
            } else {
                Button(onClick = onRecognize,
                    enabled = !state.loadingSettings && state.settings != null && state.draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) { Text("识别并记账") }
            }
            if (!state.busy && !state.canRetrySave) {
                OutlinedButton(onClick = onConfigure, enabled = !state.loadingSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.settings == null || state.error == AiFailure.NOT_CONFIGURED) "配置大模型" else "大模型设置")
                }
            }
        }
    }
}
