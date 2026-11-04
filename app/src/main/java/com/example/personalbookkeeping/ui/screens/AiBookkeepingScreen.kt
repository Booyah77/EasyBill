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
import com.example.personalbookkeeping.ui.AiUiState
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
    onHistory: () -> Unit,
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
            Text("AI 记账", modifier = Modifier.weight(1f), color = LedgerColors.Ink, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onHistory, enabled = !state.busy) { Text("历史记录") }
        }
        if (state.currentBatchId != null) {
            val batch = state.history.firstOrNull { it.id == state.currentBatchId }
            Surface(color = LedgerColors.SoftGreen, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(aiBatchResultTitle(batch), color = LedgerColors.Income, style = MaterialTheme.typography.titleLarge)
                    when {
                        batch == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        batch.entries.isEmpty() -> Text("本次保存批次仍保留在历史记录中，可以继续记下一段。", color = LedgerColors.MutedInk)
                        else -> Text("当前保留 ${batch.entries.size} 笔 · ${aiBatchTotalText(batch.entries)}",
                            color = LedgerColors.Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            batch?.entries?.forEach { entry -> AiEntryDetail(entry) }
            Text("如需更正，可到流水中编辑或删除。", color = LedgerColors.MutedInk)
            Button(onClick = onNewDraft, modifier = Modifier.fillMaxWidth()) { Text("再记一段") }
            OutlinedButton(onClick = onSeeLedger, modifier = Modifier.fillMaxWidth()) { Text("查看流水") }
        } else {
            Text("像平时说话一样，写下做了什么、花了或收了多少钱。", color = LedgerColors.Ink,
                style = MaterialTheme.typography.titleMedium)
            Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("例如", color = LedgerColors.MutedInk, style = MaterialTheme.typography.labelLarge)
                    Text("今天午饭花了28元，地铁4元，买水果16.5元。", color = LedgerColors.Ink)
                    Text("昨天买书花了45元，今天收到工资8000元。", color = LedgerColors.Ink)
                }
            }
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
            Text("识别通过后会自动保存。本次文字、分类名称和当前日期会发送到你配置的大模型服务，可能消耗 API 额度。仅支持人民币。",
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
