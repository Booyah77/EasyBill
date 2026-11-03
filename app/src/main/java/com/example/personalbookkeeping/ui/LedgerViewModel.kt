package com.example.personalbookkeeping.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalbookkeeping.data.repository.LedgerFilter
import com.example.personalbookkeeping.data.repository.LedgerRepository
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.domain.model.MonthlyTotals
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class LedgerUiState(
    val month: YearMonth,
    val entries: List<LedgerEntry>,
    val totals: MonthlyTotals,
    val isLoading: Boolean
)

class LedgerViewModel(
    private val repository: LedgerRepository
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LedgerUiState> = selectedMonth
        .flatMapLatest { month ->
            combine(
                repository.observeEntries(LedgerFilter(month = month)),
                repository.observeMonthlyTotals(month)
            ) { entries, totals ->
                LedgerUiState(
                    month = month,
                    entries = entries,
                    totals = totals,
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyState(selectedMonth.value)
        )

    fun shiftMonth(amount: Long) {
        selectedMonth.update { it.plusMonths(amount) }
    }

    suspend fun saveEntry(
        type: LedgerType,
        amountFen: Long,
        date: LocalDate,
        category: String,
        note: String,
        existing: LedgerEntry? = null
    ) {
        val now = Instant.now()
        val entry = if (existing == null) {
            LedgerEntry(
                type = type,
                amountFen = amountFen,
                date = date,
                category = category,
                note = note
            )
        } else {
            existing.copy(
                type = type,
                amountFen = amountFen,
                date = date,
                category = category,
                note = note,
                updatedAt = if (now.isBefore(existing.updatedAt)) existing.updatedAt else now
            )
        }
        repository.save(entry)
    }

    suspend fun deleteEntry(id: String) {
        repository.delete(id)
    }

    private fun emptyState(month: YearMonth) = LedgerUiState(
        month = month,
        entries = emptyList(),
        totals = MonthlyTotals(month, incomeFen = 0L, expenseFen = 0L),
        isLoading = true
    )
}

class LedgerViewModelFactory(
    private val repository: LedgerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            return LedgerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    }
}
