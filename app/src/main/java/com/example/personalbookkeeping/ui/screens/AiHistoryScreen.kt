package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.personalbookkeeping.domain.model.AiBatch
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.components.formatFen
import com.example.personalbookkeeping.ui.theme.LedgerColors
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun aiBatchResultTitle(batch: AiBatch?): String = when {
    batch == null -> "正在读取本批记录…"
    batch.entries.isEmpty() -> "本批记录已全部删除"
    else -> "已记入账本 ${batch.originalCount} 笔"
}

/** Ledger rows fit Long individually; later edits can make the aggregate exceed it. */
internal fun aiBatchTotalText(entries: List<LedgerEntry>): String {
    fun total(type: LedgerType): String {
        val fen = entries.asSequence().filter { it.type == type }
            .fold(BigInteger.ZERO) { sum, entry -> sum.add(BigInteger.valueOf(entry.amountFen)) }
        return NumberFormat.getNumberInstance(Locale.CHINA).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(BigDecimal(fen, 2))
    }
    return "支出 ¥${total(LedgerType.EXPENSE)} · 收入 ¥${total(LedgerType.INCOME)}"
}

@Composable
fun AiHistoryScreen(
    history: List<AiBatch>,
    onBack: () -> Unit,
    onSeeLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedBatchId by rememberSaveable { mutableStateOf<String?>(null) }
    val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    Column(
        modifier.fillMaxSize().background(LedgerColors.Canvas).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("AI 历史记录", color = LedgerColors.Ink, style = MaterialTheme.typography.headlineSmall)
        }
        Text("按每次保存分组，仅保存在本机。明细随流水中的修改和删除更新。", color = LedgerColors.MutedInk,
            style = MaterialTheme.typography.bodyMedium)
        if (history.isEmpty()) {
            Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有 AI 记账记录", color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
                    Text("用 AI 成功记账后，每次保存的记录会出现在这里。", color = LedgerColors.MutedInk)
                }
            }
        }
        history.sortedByDescending { it.savedAt }.forEach { batch ->
            val expanded = expandedBatchId == batch.id
            Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(timeFormat.format(batch.savedAt), color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
                    Text("保存时 ${batch.originalCount} 笔 · 当前保留 ${batch.entries.size} 笔", color = LedgerColors.MutedInk)
                    if (batch.entries.isEmpty()) {
                        Text("本批记录已全部删除", color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("当前${aiBatchTotalText(batch.entries)}", color = LedgerColors.Ink)
                    }
                    TextButton(onClick = { expandedBatchId = if (expanded) null else batch.id }) {
                        Text(if (expanded) "收起明细" else "查看明细")
                    }
                    if (expanded) {
                        HorizontalDivider(color = LedgerColors.Divider)
                        if (batch.entries.isEmpty()) {
                            Text("没有仍在账本中的明细，此批次的保存时间和原始笔数仍会保留。", color = LedgerColors.MutedInk)
                        } else {
                            batch.entries.forEach { AiEntryDetail(it) }
                        }
                    }
                }
            }
        }
        Text("如需修改或删除记录，请到流水中操作。", color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onSeeLedger, modifier = Modifier.fillMaxWidth()) { Text("查看流水") }
    }
}

@Composable
internal fun AiEntryDetail(entry: LedgerEntry) {
    Surface(color = LedgerColors.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val isIncome = entry.type == LedgerType.INCOME
            Text("${if (isIncome) "收入" else "支出"} · ${entry.category} · ¥${formatFen(entry.amountFen)}",
                color = if (isIncome) LedgerColors.Income else LedgerColors.Expense,
                style = MaterialTheme.typography.titleMedium)
            if (entry.note.isNotEmpty()) Text(entry.note, color = LedgerColors.Ink, style = MaterialTheme.typography.bodyLarge)
            Text(entry.date.toString(), color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
