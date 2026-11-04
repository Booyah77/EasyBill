package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.transfer.LedgerFileCodec
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.theme.LedgerColors
import java.time.Instant
import java.time.LocalDate

/** Synthetic, deterministic data encoded by the same v1 codec as real exports. */
internal object FileFormatHelp {
    val exportedAt: Instant = Instant.parse("2026-01-02T04:00:00Z")
    val entries: List<LedgerEntry> = listOf(
        LedgerEntry(
            id = "11111111-1111-4111-8111-111111111111",
            type = LedgerType.EXPENSE,
            amountFen = 2800L,
            date = LocalDate.of(2026, 1, 2),
            category = "餐饮",
            note = "午饭,含饮料",
            createdAt = Instant.parse("2026-01-02T03:00:00Z"),
            updatedAt = Instant.parse("2026-01-02T03:00:00Z")
        )
    )
    private val codec = LedgerFileCodec()
    // Hide only the invisible optional CSV BOM; copying the visible example remains importable.
    val csvExample: String = codec.encodeCsv(entries).removePrefix("\uFEFF")
    val jsonExample: String = codec.encodeJson(entries, exportedAt)
}

@Composable
fun FileFormatHelpDialog(onDismiss: () -> Unit) {
    var showJson by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据文件格式说明") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("仅支持本软件固定的 v1 格式。导出的 CSV / JSON 可以重新导入；微信、支付宝原始账单需先转换，不能直接导入。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showJson, onClick = { showJson = false }, label = { Text("CSV") })
                    FilterChip(selected = showJson, onClick = { showJson = true }, label = { Text("JSON") })
                }
                Text(if (showJson) "JSON 结构" else "CSV 表头", style = MaterialTheme.typography.titleSmall)
                if (showJson) {
                    Text("根对象恰好包含 schemaVersion、currency、exportedAt、records。schemaVersion 是数字 1；currency 是字符串 CNY；exportedAt 是导出时间字符串；records 是记录数组。")
                    Text("每笔记录恰好包含 id、type、amount、date、category、note、createdAt、updatedAt，值全部是字符串。记录内不另设 currency。不要增加或省略字段。")
                } else {
                    SelectionContainer {
                        Text(FileFormatHelp.csvExample.lineSequence().first(), fontFamily = FontFamily.Monospace)
                    }
                    Text("使用 UTF-8 编码，可带 BOM。第一行必须与上方表头完全一致，列名及顺序不能修改；每笔一行、共 9 列。含逗号、双引号或换行的字段用双引号包住，字段内双引号写成两个双引号。")
                }
                Text("字段含义与填写规则", style = MaterialTheme.typography.titleSmall)
                Text("id：小写标准 UUID，作为记录的唯一标识。\ntype：EXPENSE 表示支出，INCOME 表示收入，区分大小写。\namount：人民币元，必须大于 0 且恰好两位小数，例如 28.00、0.01；不用正负号、千位分隔、科学计数法或多余前导零。金额不能超出整数分的范围。\n${if (showJson) "currency（根对象）" else "currency"}：固定为 CNY。\ndate：实际收支日期，YYYY-MM-DD，例如 2026-01-02，必须是有效日期。\ncategory：分类名称，必填且不能全为空白；已有分类按原名保留。\nnote：备注，字段必填，内容可为空字符串。\ncreatedAt / updatedAt：创建 / 修改时间，使用含时区的 ISO-8601 时间，例如 2026-01-02T03:00:00Z；updatedAt 不得早于 createdAt。")
                if (showJson) Text("exportedAt：文件导出时间，同样使用 ISO-8601 时间。amount 即使是金额也要加双引号，例如 \"28.00\"。")
                Text("完整示例（可长按选择复制）", style = MaterialTheme.typography.titleSmall)
                Surface(color = LedgerColors.Canvas, shape = MaterialTheme.shapes.small) {
                    SelectionContainer {
                        Text(
                            text = if (showJson) FileFormatHelp.jsonExample else FileFormatHelp.csvExample,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text("示例为虚构数据。导入会先显示预览，存在无效记录时不会写入整份文件。", color = LedgerColors.MutedInk)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}
