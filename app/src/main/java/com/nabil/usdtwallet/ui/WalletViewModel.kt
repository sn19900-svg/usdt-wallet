package com.nabil.usdtwallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabil.usdtwallet.BuildConfig
import com.nabil.usdtwallet.data.repository.*
import com.nabil.usdtwallet.domain.usecase.TronTransactionSigner
import com.nabil.usdtwallet.domain.usecase.BscTransactionSigner
import com.nabil.usdtwallet.domain.wallet.WalletManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── UI States ─────────────────────────────────────────────

sealed class Screen {
    object Splash : Screen()
    object Lock : Screen()
    object CreateWallet : Screen()
    object ImportWallet : Screen()
    object BackupPhrase : Screen()
    object Home : Screen()
    object Send : Screen()
    object Receive : Screen()
    object History : Screen()
}

enum class ActiveChain { TRON, BSC }

data class WalletUiState(
    // ─── Tron ───────────────────────────────────────────────
    val address: String = "",
    val usdtBalance: Double = 0.0,
    val trxBalance: Double = 0.0,
    // ─── BSC ────────────────────────────────────────────────
    val bscAddress: String = "",
    val bscUsdtBalance: Double = 0.0,
    val bnbBalance: Double = 0.0,
    // ─── الشبكة النشطة ──────────────────────────────────────
    val activeChain: ActiveChain = ActiveChain.TRON,
    // ─── مشتركة ─────────────────────────────────────────────
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentScreen: Screen = Screen.Splash,
    val mnemonic: List<String> = emptyList(),
    val sendSuccess: Boolean = false,
    val sendTxId: String = "",
    val updateAvailable: Boolean = false,
    val updateVersion: String = "",
    val updateDownloadUrl: String = "",
    val isUnlocked: Boolean = false,
    val isTestnet: Boolean = true
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureStorage(application)
    private val repository = WalletRepository()

    private val _uiState = MutableStateFlow(WalletUiState(isTestnet = NetworkConfig.isTestnet))
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    // ─── تبديل الشبكة (testnet/mainnet) ───────────────────

    fun toggleNetwork() {
        val newValue = !NetworkConfig.isTestnet
        NetworkConfig.setTestnet(newValue)
        _uiState.update { it.copy(isTestnet = newValue) }
        refreshBalance()
    }

    // ─── تبديل السلسلة النشطة (Tron/BSC) ─────────────────

    fun switchChain(chain: ActiveChain) {
        _uiState.update { it.copy(activeChain = chain, errorMessage = null) }
        refreshBalance()
    }

    init {
        checkWalletExists()
        checkForUpdate()
    }

    // ─── التحقق من وجود تحديث ─────────────────────────────

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateChecker.checkForUpdate(BuildConfig.BUILD_NUMBER)
            if (info.isUpdateAvailable) {
                _uiState.update {
                    it.copy(
                        updateAvailable = true,
                        updateVersion = info.latestVersion,
                        updateDownloadUrl = info.downloadUrl
                    )
                }
            }
        }
    }

    private fun checkWalletExists() {
        if (secureStorage.isWalletCreated()) {
            val address = secureStorage.getAddress() ?: ""
            val privateKey = secureStorage.getPrivateKey() ?: ""
            val bscAddress = if (privateKey.isNotEmpty())
                BscTransactionSigner.getAddressFromPrivateKey(privateKey) else ""
            _uiState.update {
                it.copy(
                    address = address,
                    bscAddress = bscAddress,
                    currentScreen = Screen.Lock
                )
            }
        } else {
            _uiState.update { it.copy(currentScreen = Screen.CreateWallet) }
        }
    }

    fun onUnlocked() {
        _uiState.update { it.copy(isUnlocked = true, currentScreen = Screen.Home) }
        refreshBalance()
    }

    // ─── إنشاء محفظة جديدة ────────────────────────────────

    fun createNewWallet() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val keys = WalletManager.generateNewWallet()
                val bscAddress = BscTransactionSigner.getAddressFromPrivateKey(keys.privateKeyHex)
                _uiState.update {
                    it.copy(
                        mnemonic = keys.mnemonic,
                        address = keys.address,
                        bscAddress = bscAddress,
                        isLoading = false,
                        currentScreen = Screen.BackupPhrase
                    )
                }
                secureStorage.saveWallet(keys.mnemonic, keys.privateKeyHex, keys.address)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "خطأ في إنشاء المحفظة")
                }
            }
        }
    }

    // ─── استيراد محفظة ────────────────────────────────────

    fun importWallet(words: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val keys = WalletManager.importFromMnemonic(words)
            if (keys != null) {
                val bscAddress = BscTransactionSigner.getAddressFromPrivateKey(keys.privateKeyHex)
                secureStorage.saveWallet(keys.mnemonic, keys.privateKeyHex, keys.address)
                _uiState.update {
                    it.copy(
                        address = keys.address,
                        bscAddress = bscAddress,
                        isLoading = false,
                        currentScreen = Screen.Home
                    )
                }
                refreshBalance()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "الكلمات غير صحيحة، تحقق منها وأعد المحاولة"
                    )
                }
            }
        }
    }

    fun confirmBackupDone() {
        _uiState.update { it.copy(currentScreen = Screen.Home) }
        refreshBalance()
    }

    // ─── تحديث الرصيد (حسب السلسلة النشطة) ───────────────

    fun refreshBalance() {
        when (_uiState.value.activeChain) {
            ActiveChain.TRON -> refreshTronBalance()
            ActiveChain.BSC  -> refreshBscBalance()
        }
    }

    private fun refreshTronBalance() {
        val address = _uiState.value.address
        if (address.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getBalance(address)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        usdtBalance = result.data.usdt,
                        trxBalance = result.data.trx,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun refreshBscBalance() {
        val bscAddress = _uiState.value.bscAddress
        if (bscAddress.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val usdtResult = BscTransactionSigner.getUsdtBalance(bscAddress)
            val bnbResult  = BscTransactionSigner.getBnbBalance(bscAddress)
            _uiState.update {
                it.copy(
                    bscUsdtBalance = (usdtResult as? Result.Success)?.data ?: it.bscUsdtBalance,
                    bnbBalance      = (bnbResult  as? Result.Success)?.data ?: it.bnbBalance,
                    isLoading = false,
                    errorMessage = (usdtResult as? Result.Error)?.message
                )
            }
        }
    }

    // ─── جلب المعاملات ────────────────────────────────────

    fun loadTransactions() {
        val address = _uiState.value.address
        if (address.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getTransactions(address)) {
                is Result.Success -> _uiState.update {
                    it.copy(transactions = result.data, isLoading = false)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    // ─── التنقل ───────────────────────────────────────────

    fun navigate(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
        if (screen == Screen.History) loadTransactions()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSendSuccess() {
        _uiState.update { it.copy(sendSuccess = false, sendTxId = "") }
    }

    // ─── إرسال USDT (حسب السلسلة النشطة) ─────────────────

    fun sendUsdt(toAddress: String, amount: Double) {
        val privateKey = secureStorage.getPrivateKey() ?: run {
            _uiState.update { it.copy(errorMessage = "لم يتم العثور على المفتاح الخاص") }
            return
        }
        when (_uiState.value.activeChain) {
            ActiveChain.TRON -> sendTronUsdt(toAddress, amount, privateKey)
            ActiveChain.BSC  -> sendBscUsdt(toAddress, amount, privateKey)
        }
    }

    private fun sendTronUsdt(toAddress: String, amount: Double, privateKey: String) {
        val fromAddress = _uiState.value.address
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = TronTransactionSigner.sendUsdt(fromAddress, toAddress, amount, privateKey)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = result.data) }
                    refreshBalance()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun sendBscUsdt(toAddress: String, amount: Double, privateKey: String) {
        val fromAddress = _uiState.value.bscAddress
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = BscTransactionSigner.sendUsdt(fromAddress, toAddress, amount, privateKey)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = result.data) }
                    refreshBalance()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun sendTrx(toAddress: String, amount: Double) {
        val privateKey = secureStorage.getPrivateKey() ?: run {
            _uiState.update { it.copy(errorMessage = "لم يتم العثور على المفتاح الخاص") }
            return
        }
        val fromAddress = _uiState.value.address
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = TronTransactionSigner.sendTrx(fromAddress, toAddress, amount, privateKey)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = result.data) }
                    refreshBalance()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun sendBnb(toAddress: String, amount: Double) {
        val privateKey = secureStorage.getPrivateKey() ?: run {
            _uiState.update { it.copy(errorMessage = "لم يتم العثور على المفتاح الخاص") }
            return
        }
        val fromAddress = _uiState.value.bscAddress
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = BscTransactionSigner.sendBnb(fromAddress, toAddress, amount, privateKey)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = result.data) }
                    refreshBalance()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    // ─── حذف المحفظة ──────────────────────────────────────

    fun deleteWallet() {
        secureStorage.deleteWallet()
        _uiState.update { WalletUiState(currentScreen = Screen.CreateWallet) }
    }

    fun getMnemonic(): List<String> = secureStorage.getMnemonic() ?: emptyList()
}
