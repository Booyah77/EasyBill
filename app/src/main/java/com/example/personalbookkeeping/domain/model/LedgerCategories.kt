package com.example.personalbookkeeping.domain.model

import java.text.Normalizer
import java.util.Locale

object LedgerCategories {
    val expense = listOf("餐饮", "交通", "购物", "居家", "通讯", "娱乐", "医疗", "教育", "人情", "其他")
    val income = listOf("工资", "奖金", "兼职", "理财", "礼金", "其他")
    const val MAX_NEW_NAME_LENGTH = 20
    const val NEW_NAME_HINT = "分类需为 1～20 个字，不能含换行、不可见字符或占位名称"

    private val placeholders = setOf(
        "分类", "分类名称", "自定义", "未分类", "未知", "待分类", "待定", "不详", "无",
        "null", "undefined", "unknown", "n/a", "na", "none"
    )

    fun defaults(type: LedgerType): List<String> = when (type) {
        LedgerType.EXPENSE -> expense
        LedgerType.INCOME -> income
    }

    fun available(entries: List<LedgerEntry>): Map<LedgerType, List<String>> =
        LedgerType.entries.associateWith { type ->
            (defaults(type) + entries.asSequence()
                .filter { it.type == type && it.deletedAt == null }
                .map { it.category }
                .filter(String::isNotBlank)
                .sorted()
                .toList()).distinct()
        }

    /** Historical names are data, not new input: preserve their exact spelling before validation. */
    fun resolve(
        type: LedgerType,
        rawName: String,
        categories: Map<LedgerType, List<String>>
    ): String {
        val sameType = (defaults(type) + categories[type].orEmpty()).distinct()
        sameType.firstOrNull { it == rawName }?.let { return it }
        val key = normalizedName(rawName)
        sameType.firstOrNull { normalizedName(it) == key }?.let { return it }
        // Resolve known names before applying rules that only govern genuinely new names.
        // A known income-only name must not silently become a new expense category, or vice versa.
        val belongsToOtherType = LedgerType.entries.filter { it != type }.any { otherType ->
            (defaults(otherType) + categories[otherType].orEmpty()).any { normalizedName(it) == key }
        }
        return if (belongsToOtherType) "其他" else validateNewName(rawName)
    }

    fun validateNewName(rawName: String): String {
        val name = rawName.trim()
        val codePoints = rawName.codePoints().toArray()
        require(codePoints.none { point ->
            Character.getType(point) in setOf(
                Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(),
                Character.PARAGRAPH_SEPARATOR.toInt(), Character.SURROGATE.toInt()
            )
        }) { NEW_NAME_HINT }
        require(name.codePointCount(0, name.length) in 1..MAX_NEW_NAME_LENGTH) { NEW_NAME_HINT }
        require(name.codePoints().anyMatch { Character.isLetter(it) }) { NEW_NAME_HINT }
        require(normalizedName(name) !in placeholders) { NEW_NAME_HINT }
        return name
    }

    private fun normalizedName(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
