package com.nabil.usdtwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nabil.usdtwallet.ui.*
import com.nabil.usdtwallet.ui.screens.*
import com.nabil.usdtwallet.ui.theme.USDTWalletTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            USDTWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WalletApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun WalletApp(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.currentScreen) {
        is Screen.Splash -> {
            // شاشة بسيطة أثناء التحقق
            CreateWalletScreen(viewModel)
        }
        is Screen.CreateWallet -> CreateWalletScreen(viewModel)
        is Screen.ImportWallet -> ImportWalletScreen(viewModel)
        is Screen.BackupPhrase -> BackupPhraseScreen(viewModel)
        is Screen.Home -> HomeScreen(viewModel)
        is Screen.Send -> SendScreen(viewModel)
        is Screen.Receive -> ReceiveScreen(viewModel)
        is Screen.History -> HistoryScreen(viewModel)
    }
}
