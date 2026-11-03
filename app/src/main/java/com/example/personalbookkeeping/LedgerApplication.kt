package com.example.personalbookkeeping

import android.app.Application
import com.example.personalbookkeeping.data.ai.AiSettingsStore
import com.example.personalbookkeeping.data.local.LedgerDatabase
import com.example.personalbookkeeping.data.repository.RoomLedgerRepository
import com.example.personalbookkeeping.data.repository.RoomObjectStorageSyncRepository
import com.example.personalbookkeeping.data.sync.GitSyncManager
import com.example.personalbookkeeping.data.sync.ObjectStorageSyncManager
import com.example.personalbookkeeping.data.transfer.LedgerFileTransfer

class LedgerApplication : Application() {
    val ledgerRepository: RoomLedgerRepository by lazy {
        val database = LedgerDatabase.getInstance(this)
        RoomLedgerRepository(database.ledgerEntryDao(), database)
    }

    val aiSettingsStore: AiSettingsStore by lazy {
        AiSettingsStore(this)
    }

    val ledgerFileTransfer: LedgerFileTransfer by lazy {
        LedgerFileTransfer(ledgerRepository)
    }

    val gitSyncManager: GitSyncManager by lazy {
        GitSyncManager(this, ledgerRepository)
    }

    val objectStorageSyncManager: ObjectStorageSyncManager by lazy {
        ObjectStorageSyncManager(this, RoomObjectStorageSyncRepository(LedgerDatabase.getInstance(this)))
    }
}
