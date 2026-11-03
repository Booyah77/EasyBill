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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.LedgerUiState
import com.example.personalbookkeeping.ui.components.LedgerEntryRow
import com.example.personalbookkeeping.ui.components.MonthNavigation
import com.example.personalbookkeeping.ui.components.MonthlySummary
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun HomeScreen(
    state: LedgerUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAdd: (LedgerType) -> Unit,
    onSeeLedger: () -> Unit,
    onAiBookkeeping: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "账本总览",
                    color = LedgerColors.Ink,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        MonthNavigation(
            month = state.month,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )

        MonthlySummary(totals = state.totals)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAction(
                title = "记支出",
                detail = "今天花了多少",
                icon = Icons.Filled.KeyboardArrowDown,
                color = LedgerColors.Expense,
                background = LedgerColors.SoftTerracotta,
                onClick = { onAdd(LedgerType.EXPENSE) },
                modifier = Modifier.weight(1f)
            )
            QuickAction(
                title = "记收入",
                detail = "补上一笔收入",
                icon = Icons.Filled.Add,
                color = LedgerColors.Income,
                background = LedgerColors.SoftGreen,
                onClick = { onAdd(LedgerType.INCOME) },
                modifier = Modifier.weight(1f)
            )
        }

        QuickAction(
            title = "AI 记账",
            detail = "写一段账单，自动分笔记入",
            icon = Icons.Filled.ArrowForward,
            color = LedgerColors.Income,
            background = LedgerColors.SoftGreen,
            onClick = onAiBookkeeping,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最近记录",
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onSeeLedger) {
                Text("全部流水")
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (state.isLoading) {
            Text(
                text = "正在读取本地账目…",
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (state.entries.isEmpty()) {
            EmptyLedgerState(onAddExpense = { onAdd(LedgerType.EXPENSE) })
        } else {
            Column {
                state.entries.take(6).forEachIndexed { index, entry ->
                    LedgerEntryRow(entry = entry, onClick = { onEdit(entry) })
                    if (index < minOf(state.entries.size, 6) - 1) {
                        HorizontalDivider(color = LedgerColors.Divider)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickAction(
    title: String,
    detail: String,
    icon: ImageVector,
    color: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(color = Color.White.copy(alpha = 0.76f), shape = RoundedCornerShape(12.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = color
                )
            }
            Column {
                Text(title, color = color, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = detail,
                    modifier = Modifier.padding(top = 2.dp),
                    color = LedgerColors.MutedInk,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyLedgerState(onAddExpense: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LedgerColors.Surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("还没有记录", color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
            Text(
                "从今天的第一笔开始，慢慢看清收支。",
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onAddExpense) { Text("记一笔支出") }
        }
    }
}
