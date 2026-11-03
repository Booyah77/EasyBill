package com.example.personalbookkeeping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personalbookkeeping.ui.LedgerViewModel
import com.example.personalbookkeeping.ui.LedgerViewModelFactory
import com.example.personalbookkeeping.ui.PersonalLedgerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ledgerApplication = application as LedgerApplication
        setContent {
            val viewModel: LedgerViewModel = viewModel(
                factory = LedgerViewModelFactory(ledgerApplication.ledgerRepository)
            )
            PersonalLedgerApp(
                viewModel = viewModel,
                fileTransfer = ledgerApplication.ledgerFileTransfer
            )
        }
    }
}
