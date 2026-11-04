package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalbookkeeping.domain.model.LedgerCategories
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.theme.LedgerColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

private val EXPENSE_CATEGORIES = com.example.personalbookkeeping.domain.model.LedgerCategories.expense
private val INCOME_CATEGORIES = com.example.personalbookkeeping.domain.model.LedgerCategories.income

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun EntryScreen(
    initialType: LedgerType,
    existing: LedgerEntry?,
    onBack: () -> Unit,
    onSave: suspend (LedgerType, Long, LocalDate, String, String, LedgerEntry?) -> Unit,
    onDelete: suspend (LedgerEntry) -> Unit,
    categoryCatalog: Map<LedgerType, List<String>> = LedgerCategories.available(listOfNotNull(existing)),
    modifier: Modifier = Modifier
) {
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: initialType) }
    var amount by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { BigDecimal.valueOf(it.amountFen, 2).toPlainString() } ?: "")
    }
    var category by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.category ?: defaultCategory(type))
    }
    var date by rememberSaveable(existing?.id) { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val isExistingCategoryCustom = existing != null &&
        existing.category !in LedgerCategories.defaults(existing.type)
    var showCustomCategory by rememberSaveable(existing?.id) { mutableStateOf(isExistingCategoryCustom) }
    var customCategory by rememberSaveable(existing?.id) {
        mutableStateOf(if (isExistingCategoryCustom) existing!!.category else "")
    }
    var showDeleteConfirmation by rememberSaveable(existing?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val datePickerState = key(date) {
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
    }
    val categories = if (type == LedgerType.EXPENSE) EXPENSE_CATEGORIES else INCOME_CATEGORIES
    val parsedAmountFen = parseAmountFen(amount)

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = if (existing == null) "记一笔" else "编辑记录",
                modifier = Modifier.padding(start = 4.dp),
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = type == LedgerType.EXPENSE,
                onClick = {
                    if (type != LedgerType.EXPENSE) {
                        type = LedgerType.EXPENSE
                        category = defaultCategory(type)
                        showCustomCategory = false
                        customCategory = ""
                        saveError = null
                    }
                },
                label = { Text("支出") }
            )
            FilterChip(
                selected = type == LedgerType.INCOME,
                onClick = {
                    if (type != LedgerType.INCOME) {
                        type = LedgerType.INCOME
                        category = defaultCategory(type)
                        showCustomCategory = false
                        customCategory = ""
                        saveError = null
                    }
                },
                label = { Text("收入") }
            )
        }

        Column {
            Text("金额", color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filter { character -> character.isDigit() || character == '.' }
                    amountError = null
                    saveError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                prefix = { Text("¥", fontSize = 24.sp, color = LedgerColors.MutedInk) },
                textStyle = MaterialTheme.typography.displaySmall.copy(color = LedgerColors.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amountError != null,
                placeholder = { Text("0.00", color = LedgerColors.Divider) }
            )
            amountError?.let { error ->
                Text(error, modifier = Modifier.padding(top = 4.dp), color = LedgerColors.Expense, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Column {
            Text("分类", color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { option ->
                    FilterChip(
                        selected = category == option && !showCustomCategory,
                        onClick = {
                            category = option
                            showCustomCategory = false
                        },
                        label = { Text(option) }
                    )
                }
                FilterChip(
                    selected = showCustomCategory,
                    onClick = { showCustomCategory = true },
                    label = { Text("自定义") }
                )
            }
            if (showCustomCategory) {
                OutlinedTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it; saveError = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    label = { Text("分类名称") },
                    supportingText = { Text("新分类限 1～20 个字，已有分类可原样保留") },
                    singleLine = true
                )
            }
        }

        Column {
            Text("日期", color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(date.toString(), color = LedgerColors.Ink, style = MaterialTheme.typography.titleMedium)
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注（可选）") },
            singleLine = true
        )

        saveError?.let { error ->
            Text(error, color = LedgerColors.Expense, style = MaterialTheme.typography.bodyMedium)
        }

        if (existing != null) {
            TextButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("删除这笔记录", color = LedgerColors.Expense)
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                val rawCategory = if (showCustomCategory) customCategory else category
                val resolvedCategory = runCatching {
                    LedgerCategories.resolve(type, rawCategory, categoryCatalog)
                }.getOrNull()
                if (parsedAmountFen == null) {
                    amountError = "请输入大于 0 且最多两位小数的金额"
                } else if (resolvedCategory == null) {
                    saveError = LedgerCategories.NEW_NAME_HINT
                } else {
                    saving = true
                    keyboard?.hide()
                    scope.launch {
                        runCatching {
                            onSave(type, parsedAmountFen, date, resolvedCategory, note.trim(), existing)
                        }.onSuccess {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBack()
                        }.onFailure {
                            saveError = "保存失败，请重试"
                        }
                        saving = false
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(if (saving) "正在保存" else "保存记录")
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirmation && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后会从流水和月度统计中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { onDelete(existing) }
                            .onSuccess {
                                showDeleteConfirmation = false
                                onBack()
                            }
                            .onFailure { saveError = "删除失败，请重试" }
                    }
                }) { Text("删除", color = LedgerColors.Expense) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") }
            }
        )
    }
}

private fun defaultCategory(type: LedgerType): String =
    if (type == LedgerType.EXPENSE) EXPENSE_CATEGORIES.first() else INCOME_CATEGORIES.first()

private fun parseAmountFen(value: String): Long? {
    if (!value.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?"))) return null
    return runCatching {
        BigDecimal(value)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()?.takeIf { it > 0L }
}
