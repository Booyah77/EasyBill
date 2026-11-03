package com.example.personalbookkeeping.domain.model

object LedgerCategories {
    val expense = listOf("餐饮", "交通", "购物", "居家", "娱乐", "医疗", "其他")
    val income = listOf("工资", "奖金", "理财", "转账", "其他")

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
}
