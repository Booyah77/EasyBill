package com.example.personalbookkeeping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.domain.model.MonthlyTotals
import com.example.personalbookkeeping.ui.theme.LedgerColors
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun MonthNavigation(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)),
            style = MaterialTheme.typography.titleMedium,
            color = LedgerColors.Ink
        )
        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

@Composable
fun MonthlySummary(
    totals: MonthlyTotals,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFEAEFE8),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "本月结余",
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = (if (totals.balanceFen < 0) "−" else "") + "¥${formatFen(abs(totals.balanceFen))}",
                modifier = Modifier.padding(top = 2.dp),
                color = LedgerColors.Ink,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 34.sp,
                lineHeight = 40.sp
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = LedgerColors.Divider
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SummaryAmount("收入", totals.incomeFen, LedgerColors.Income, Modifier.weight(1f))
                SummaryAmount("支出", totals.expenseFen, LedgerColors.Expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryAmount(
    label: String,
    amountFen: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "¥${formatFen(amountFen)}",
            modifier = Modifier.padding(top = 4.dp),
            color = color,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun LedgerEntryRow(
    entry: LedgerEntry,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val semanticColor = if (entry.type == LedgerType.INCOME) LedgerColors.Income else LedgerColors.Expense
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (entry.type == LedgerType.INCOME) LedgerColors.SoftGreen else LedgerColors.SoftTerracotta,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.category.take(1),
                color = semanticColor,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.category,
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = listOfNotNull(
                entry.note.takeIf(String::isNotBlank),
                entry.date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
            ).joinToString(" · ")
            Text(
                text = detail,
                modifier = Modifier.padding(top = 3.dp),
                color = LedgerColors.MutedInk,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = (if (entry.type == LedgerType.INCOME) "+" else "−") + "¥${formatFen(entry.amountFen)}",
            color = semanticColor,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

fun formatFen(amountFen: Long): String {
    val amount = BigDecimal.valueOf(amountFen, 2)
    return NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}
