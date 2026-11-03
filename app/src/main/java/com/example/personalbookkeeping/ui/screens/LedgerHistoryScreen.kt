package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.LedgerUiState
import com.example.personalbookkeeping.ui.components.LedgerEntryRow
import com.example.personalbookkeeping.ui.components.MonthNavigation
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun LedgerHistoryScreen(
    state: LedgerUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember(state.month) { mutableStateOf<LedgerType?>(null) }
    var selectedCategory by remember(state.month) { mutableStateOf<String?>(null) }
    val visibleEntries = state.entries.filter {
        (selectedType == null || it.type == selectedType) &&
            (selectedCategory == null || it.category == selectedCategory)
    }
    val categories = state.entries
        .asSequence()
        .filter { selectedType == null || it.type == selectedType }
        .map(LedgerEntry::category)
        .distinct()
        .sorted()
        .toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "账目流水",
            color = LedgerColors.Ink,
            style = MaterialTheme.typography.headlineSmall
        )
        MonthNavigation(
            month = state.month,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedType == null,
                onClick = { selectedType = null },
                label = { Text("全部") }
            )
            FilterChip(
                selected = selectedType == LedgerType.EXPENSE,
                onClick = {
                    selectedType = LedgerType.EXPENSE
                    selectedCategory = null
                },
                label = { Text("支出") }
            )
            FilterChip(
                selected = selectedType == LedgerType.INCOME,
                onClick = {
                    selectedType = LedgerType.INCOME
                    selectedCategory = null
                },
                label = { Text("收入") }
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("全部分类") }
                )
            }
            items(categories, key = { it }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) }
                )
            }
        }

        if (state.isLoading) {
            Text(
                text = "正在读取本地账目…",
                modifier = Modifier.padding(top = 20.dp),
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (visibleEntries.isEmpty()) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(
                    text = "这个月还没有流水",
                    color = LedgerColors.Ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "记下第一笔后，这里会按日期排列。",
                    modifier = Modifier.padding(top = 6.dp),
                    color = LedgerColors.MutedInk,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(visibleEntries, key = LedgerEntry::id) { entry ->
                    LedgerEntryRow(entry = entry, onClick = { onEdit(entry) })
                    HorizontalDivider(color = LedgerColors.Divider)
                }
            }
        }
    }
}
