package com.example.personalbookkeeping.domain.model

import java.time.Instant

/** Local AI provenance; entries always reflect the current active ledger rows. */
data class AiBatch(
    val id: String,
    val savedAt: Instant,
    val originalCount: Int,
    val entries: List<LedgerEntry>
)
