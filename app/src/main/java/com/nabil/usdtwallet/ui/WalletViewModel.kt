package com.nabil.usdtwallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabil.usdtwallet.BuildConfig
import com.nabil.usdtwallet.data.repository.*
import com.nabil.usdtwallet.domain.usecase.BscTransactionSigner
import com.nabil.usdtwallet.domain.usecase.TronTransactionSigner
import com.nabil.usdtwallet.domain.usecase.WalletNotificationWorker
import com.nabil.usdtwallet.domain.wallet.WalletManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    object AddressBook : Screen()
    object Settings : Screen()
    object Market : Screen()
}

enum class ActiveChain { TRON, BSC }

data class WalletUiState(
    val address: String = "",
    val usdtBalance: Double = 0.0,
    val trxBalance: Double = 0.0,
    val bscAddress: String = "",
    val bscUsdtBalance: Double = 0.0,
    val bnbBalance: Double = 0.0,
    val activeChain: ActiveChain = ActiveChain.TRON,
    val bnbUsdPrice: Double = 0.0,
    val trxUsdPrice: Double = 0.0,
    val usdSypRate: Double = 13000.0,
    val transactions: List<Transaction> = emptyList(),
    val savedAddresses: List<SavedAddress> = emptyList(),
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
    val isTestnet: Boolean = true,
    val notificationsEnabled: Boolean = false
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureStorage(application)
    private val repository = WalletRepository()
    private val app = application

    private val _uiState = MutableStateFlow(WalletUiState(isTestnet = NetworkConfig.isTestnet))
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        checkWalletExists()
        checkForUpdate()
    }

    fun toggleNetwork() {
        NetworkConfig.setTestnet(!NetworkConfig.isTestnet)
        _uiState.update { it.copy(isTestnet = NetworkConfig.isTestnet) }
        refreshBalance()
    }

    fun switchChain(chain: ActiveChain) {
        _uiState.update { it.copy(activeChain = chain, errorMessage = null) }
        refreshBalance()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateChecker.checkForUpdate(BuildConfig.BUILD_NUMBER)
            if (info.isUpdateAvailable) {
                _uiState.update {
                    it.copy(updateAvailable = true, updateVersion = info.latestVersion, updateDownloadUrl = info.downloadUrl)
                }
            }
        }
    }

    private fun checkWalletExists() {
        if (secureStorage.isWalletCreated()) {
            val address = secureStorage.getAddress() ?: ""
            val privateKey = secureStorage.getPrivateKey() ?: ""
            val bscAddress = if (privateKey.isNotEmpty()) BscTransactionSigner.getAddressFromPrivateKey(privateKey) else ""
            _uiState.update { it.copy(address = address, bscAddress = bscAddress, currentScreen = Screen.Lock) }
        } else {
            _uiState.update { it.copy(currentScreen = Screen.CreateWallet) }
        }
    }

    fun onUnlocked() {
        _uiState.update { it.copy(isUnlocked = true, currentScreen = Screen.Home) }
        refreshBalance()
        fetchPrices()
        loadAddressBook()
    }

    fun createNewWallet() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val keys = WalletManager.generateNewWallet()
                val bscAddress = BscTransactionSigner.getAddressFromPrivateKey(keys.privateKeyHex)
                secureStorage.saveWallet(keys.mnemonic, keys.privateKeyHex, keys.address)
                _uiState.update {
                    it.copy(mnemonic = keys.mnemonic, address = keys.address, bscAddress = bscAddress, isLoading = false, currentScreen = Screen.BackupPhrase)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "خطأ في إنشاء المحفظة") }
            }
        }
    }

    fun importWallet(words: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val keys = WalletManager.importFromMnemonic(words)
            if (keys != null) {
                val bscAddress = BscTransactionSigner.getAddressFromPrivateKey(keys.privateKeyHex)
                secureStorage.saveWallet(keys.mnemonic, keys.privateKeyHex, keys.address)
                _uiState.update { it.copy(address = keys.address, bscAddress = bscAddress, isLoading = false, currentScreen = Screen.Home) }
                refreshBalance(); fetchPrices()
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "الكلمات غير صحيحة، تحقق منها وأعد المحاولة") }
            }
        }
    }

    fun confirmBackupDone() {
        _uiState.update { it.copy(currentScreen = Screen.Home) }
        refreshBalance(); fetchPrices()
    }

    fun refreshBalance() {
        when (_uiState.value.activeChain) {
            ActiveChain.TRON -> refreshTronBalance()
            ActiveChain.BSC  -> refreshBscBalance()
        }
    }

    private fun refreshTronBalance() {
        val address = _uiState.value.address; if (address.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val r = repository.getBalance(address)) {
                is Result.Success -> _uiState.update { it.copy(usdtBalance = r.data.usdt, trxBalance = r.data.trx, isLoading = false, errorMessage = null) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    private fun refreshBscBalance() {
        val bscAddress = _uiState.value.bscAddress; if (bscAddress.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val usdtResult = BscTransactionSigner.getUsdtBalance(bscAddress)
            val bnbResult  = BscTransactionSigner.getBnbBalance(bscAddress)
            _uiState.update {
                it.copy(
                    bscUsdtBalance = (usdtResult as? Result.Success)?.data ?: it.bscUsdtBalance,
                    bnbBalance     = (bnbResult  as? Result.Success)?.data ?: it.bnbBalance,
                    isLoading = false,
                    errorMessage = (usdtResult as? Result.Error)?.message
                )
            }
        }
    }

    fun fetchPrices() {
        viewModelScope.launch {
            try {
                val prices = PriceRepository.getPrices()
                _uiState.update { it.copy(bnbUsdPrice = prices.bnbUsd, trxUsdPrice = prices.trxUsd, usdSypRate = prices.usdSyp) }
            } catch (_: Exception) {}
        }
    }

    fun loadTransactions() {
        val address = _uiState.value.address; if (address.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val r = repository.getTransactions(address)) {
                is Result.Success -> _uiState.update { it.copy(transactions = r.data, isLoading = false) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun loadAddressBook() {
        _uiState.update { it.copy(savedAddresses = AddressBookManager.getAll(app)) }
    }

    fun saveAddress(name: String, address: String) {
        val chain = if (_uiState.value.activeChain == ActiveChain.BSC) "BSC" else "TRON"
        AddressBookManager.save(app, SavedAddress(name = name, address = address, chain = chain))
        loadAddressBook()
    }

    fun deleteAddress(id: String) {
        AddressBookManager.delete(app, id)
        loadAddressBook()
    }

    fun toggleNotifications(enabled: Boolean) {
        if (enabled) WalletNotificationWorker.schedule(app) else WalletNotificationWorker.cancel(app)
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun navigate(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
        when (screen) {
            is Screen.History     -> loadTransactions()
            is Screen.AddressBook -> loadAddressBook()
            is Screen.Home        -> fetchPrices()
            is Screen.Market      -> {}
            else -> {}
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearSendSuccess() { _uiState.update { it.copy(sendSuccess = false, sendTxId = "") } }

    fun sendUsdt(toAddress: String, amount: Double) {
        val pk = secureStorage.getPrivateKey() ?: run { _uiState.update { it.copy(errorMessage = "لم يتم العثور على المفتاح الخاص") }; return }
        when (_uiState.value.activeChain) {
            ActiveChain.TRON -> viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                when (val r = TronTransactionSigner.sendUsdt(_uiState.value.address, toAddress, amount, pk)) {
                    is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                    is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                }
            }
            ActiveChain.BSC -> viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                when (val r = BscTransactionSigner.sendUsdt(_uiState.value.bscAddress, toAddress, amount, pk)) {
                    is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                    is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                }
            }
        }
    }

    fun sendTrx(toAddress: String, amount: Double) {
        val pk = secureStorage.getPrivateKey() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = TronTransactionSigner.sendTrx(_uiState.value.address, toAddress, amount, pk)) {
                is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun sendBnb(toAddress: String, amount: Double) {
        val pk = secureStorage.getPrivateKey() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = BscTransactionSigner.sendBnb(_uiState.value.bscAddress, toAddress, amount, pk)) {
                is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun deleteWallet() {
        secureStorage.deleteWallet()
        WalletNotificationWorker.cancel(app)
        _uiState.update { WalletUiState(currentScreen = Screen.CreateWallet) }
    }

    fun getMnemonic(): List<String> = secureStorage.getMnemonic() ?: emptyList()
}
