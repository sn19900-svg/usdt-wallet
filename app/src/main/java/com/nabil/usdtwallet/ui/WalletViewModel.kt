package com.nabil.usdtwallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabil.usdtwallet.BuildConfig
import com.nabil.usdtwallet.data.repository.*
import com.nabil.usdtwallet.data.repository.EthApiService
import com.nabil.usdtwallet.domain.usecase.BscTransactionSigner
import com.nabil.usdtwallet.domain.usecase.EthTransactionSigner
import com.nabil.usdtwallet.domain.usecase.SolanaTransactionSigner
import com.nabil.usdtwallet.domain.usecase.TronTransactionSigner
import com.nabil.usdtwallet.domain.usecase.WalletNotificationWorker
import com.nabil.usdtwallet.domain.wallet.WalletManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── Screens ──────────────────────────────────────────────
sealed class Screen {
    object Splash      : Screen()
    object Lock        : Screen()
    object CreateWallet: Screen()
    object ImportWallet: Screen()
    object BackupPhrase: Screen()
    object Home        : Screen()
    object Send        : Screen()
    object Receive     : Screen()
    object History     : Screen()
    object AddressBook : Screen()
    object Settings    : Screen()
    object Market      : Screen()
    object Wallets     : Screen()   // إدارة المحافظ
}

enum class ActiveChain { TRON, BSC, SOLANA, ETHEREUM }

// ─── UI State ─────────────────────────────────────────────
data class WalletUiState(
    // المحافظ المتعددة
    val wallets: List<WalletAccount> = emptyList(),
    val activeWalletId: String = "",

    // الشبكة النشطة
    val activeChain: ActiveChain = ActiveChain.TRON,

    // Tron
    val address: String = "",
    val usdtBalance: Double = 0.0,
    val trxBalance: Double = 0.0,

    // BSC
    val bscAddress: String = "",
    val bscUsdtBalance: Double = 0.0,
    val bnbBalance: Double = 0.0,

    // Solana
    val solanaAddress: String = "",
    val solUsdtBalance: Double = 0.0,
    val solBalance: Double = 0.0,

    // الأسعار
    val bnbUsdPrice: Double = 0.0,
    val trxUsdPrice: Double = 0.0,
    val solUsdPrice: Double = 0.0,

    // Ethereum
    val ethAddress: String = "",
    val ethUsdtBalance: Double = 0.0,
    val ethBalance: Double = 0.0,
    val ethUsdPrice: Double = 0.0,

    // مشتركة
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
    private val repository    = WalletRepository()
    private val app           = application

    private val _uiState = MutableStateFlow(WalletUiState(isTestnet = NetworkConfig.isTestnet))
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        migrateAndInit()
        checkForUpdate()
    }

    // ─── ترحيل من النظام القديم ───────────────────────────
    private fun migrateAndInit() {
        try {
            MultiWalletStorage.migrateFromLegacy(app, secureStorage)
        } catch (e: Exception) {
            android.util.Log.e("WalletViewModel", "فشل الترحيل: ${e.message}")
        }
        checkWalletExists()
    }

    // ─── التحقق من وجود محافظ ────────────────────────────
    private fun checkWalletExists() {
        if (MultiWalletStorage.hasWallets(app)) {
            loadActiveWallet()
            _uiState.update { it.copy(currentScreen = Screen.Lock) }
        } else {
            _uiState.update { it.copy(currentScreen = Screen.CreateWallet) }
        }
    }

    private fun loadActiveWallet() {
        try {
            val wallet = MultiWalletStorage.getActive(app) ?: return
            val allWallets = MultiWalletStorage.getAll(app)

            // عرض فوري بالبيانات المخزّنة - بدون أي اشتقاق على المسار الحرج
            _uiState.update {
                it.copy(
                    wallets        = allWallets,
                    activeWalletId = wallet.id,
                    address        = wallet.tronAddress,
                    bscAddress     = wallet.bscAddress,
                    solanaAddress  = wallet.solanaAddress,
                    ethAddress     = wallet.ethereumAddress.ifEmpty { wallet.bscAddress }
                )
            }

            // إصلاح العناوين الناقصة لاحقاً في الخلفية (لا يحجب فتح القفل أبداً)
            if (wallet.bscAddress.isEmpty() && wallet.mnemonic.trim().split(" ").size == 12) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        val words = wallet.mnemonic.trim().split(" ")
                        val bscAddr = WalletManager.deriveBscAddress(words)
                        if (bscAddr.isNotEmpty()) {
                            val bscPriv = WalletManager.deriveBscPrivateKey(words)
                            val solAddr = WalletManager.deriveSolanaAddress(words)
                            val solPriv = WalletManager.deriveSolanaPrivateKeyBase58(words)
                            val updated = wallet.copy(
                                bscAddress = bscAddr, bscPrivateKey = bscPriv,
                                solanaAddress = solAddr, solanaPrivateKey = solPriv,
                                ethereumAddress = bscAddr, ethereumPrivateKey = bscPriv
                            )
                            MultiWalletStorage.save(app, updated)
                            _uiState.update {
                                it.copy(
                                    bscAddress = bscAddr,
                                    solanaAddress = solAddr,
                                    ethAddress = bscAddr,
                                    wallets = MultiWalletStorage.getAll(app)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("WalletViewModel", "فشل اشتقاق العناوين الناقصة: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WalletViewModel", "فشل تحميل المحفظة النشطة: ${e.message}")
            _uiState.update { it.copy(errorMessage = "خطأ في تحميل المحفظة") }
        }
    }

    fun onUnlocked() {
        loadActiveWallet()
        _uiState.update { it.copy(isUnlocked = true, currentScreen = Screen.Home) }
        refreshBalance()
        fetchPrices()
        loadAddressBook()
        loadTransactions()
    }

    // ─── تبديل المحفظة النشطة ────────────────────────────
    fun switchWallet(walletId: String) {
        MultiWalletStorage.setActive(app, walletId)
        loadActiveWallet()
        refreshBalance()
        loadTransactions()
        _uiState.update { it.copy(currentScreen = Screen.Home, errorMessage = null) }
    }

    // ─── إعادة تسمية محفظة ───────────────────────────────
    fun renameWallet(walletId: String, newName: String) {
        MultiWalletStorage.rename(app, walletId, newName)
        _uiState.update { it.copy(wallets = MultiWalletStorage.getAll(app)) }
    }

    // ─── حذف محفظة ───────────────────────────────────────
    fun deleteWalletById(walletId: String) {
        MultiWalletStorage.delete(app, walletId)
        val remaining = MultiWalletStorage.getAll(app)
        if (remaining.isEmpty()) {
            secureStorage.deleteWallet()
            WalletNotificationWorker.cancel(app)
            _uiState.update { WalletUiState(currentScreen = Screen.CreateWallet) }
        } else {
            loadActiveWallet()
            refreshBalance()
            _uiState.update { it.copy(wallets = remaining) }
        }
    }

    // ─── إنشاء محفظة جديدة ───────────────────────────────
    fun createNewWallet(name: String = "محفظة جديدة") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val keys = WalletManager.generateNewWallet()
                val bscPriv    = WalletManager.deriveBscPrivateKey(keys.mnemonic)
                val bscAddr    = WalletManager.deriveBscAddress(keys.mnemonic)
                val solPriv    = WalletManager.deriveSolanaPrivateKeyBase58(keys.mnemonic)
                val solAddr    = WalletManager.deriveSolanaAddress(keys.mnemonic)

                val wallet = WalletAccount(
                    name             = name,
                    mnemonic         = keys.mnemonic.joinToString(" "),
                    tronAddress      = keys.address,
                    tronPrivateKey   = keys.privateKeyHex,
                    bscAddress       = bscAddr,
                    bscPrivateKey    = bscPriv,
                    solanaAddress    = solAddr,
                    solanaPrivateKey = solPriv,
                    ethereumAddress  = bscAddr,
                    ethereumPrivateKey = bscPriv
                )
                MultiWalletStorage.save(app, wallet)
                MultiWalletStorage.setActive(app, wallet.id)

                // حفظ في SecureStorage القديم أيضاً للتوافق
                secureStorage.saveWallet(keys.mnemonic, keys.privateKeyHex, keys.address, bscPriv, bscAddr)

                _uiState.update {
                    it.copy(
                        mnemonic      = keys.mnemonic,
                        address       = keys.address,
                        bscAddress    = bscAddr,
                        solanaAddress = solAddr,
                        wallets       = MultiWalletStorage.getAll(app),
                        activeWalletId = wallet.id,
                        isLoading     = false,
                        currentScreen = Screen.BackupPhrase
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "خطأ في إنشاء المحفظة: ${e.message}") }
            }
        }
    }

    // ─── استيراد محفظة ───────────────────────────────────
    fun importWallet(words: List<String>, name: String = "محفظة مستوردة") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val keys = WalletManager.importFromMnemonic(words)
            if (keys != null) {
                val bscPriv = WalletManager.deriveBscPrivateKey(keys.mnemonic)
                val bscAddr = WalletManager.deriveBscAddress(keys.mnemonic)
                val solPriv = WalletManager.deriveSolanaPrivateKeyBase58(keys.mnemonic)
                val solAddr = WalletManager.deriveSolanaAddress(keys.mnemonic)

                val wallet = WalletAccount(
                    name             = name,
                    mnemonic         = keys.mnemonic.joinToString(" "),
                    tronAddress      = keys.address,
                    tronPrivateKey   = keys.privateKeyHex,
                    bscAddress       = bscAddr,
                    bscPrivateKey    = bscPriv,
                    solanaAddress    = solAddr,
                    solanaPrivateKey = solPriv
                )
                MultiWalletStorage.save(app, wallet)
                MultiWalletStorage.setActive(app, wallet.id)
                secureStorage.saveWallet(keys.mnemonic, keys.privateKeyHex, keys.address, bscPriv, bscAddr)

                _uiState.update {
                    it.copy(
                        address        = keys.address,
                        bscAddress     = bscAddr,
                        solanaAddress  = solAddr,
                        wallets        = MultiWalletStorage.getAll(app),
                        activeWalletId = wallet.id,
                        isLoading      = false,
                        currentScreen  = Screen.Home
                    )
                }
                refreshBalance(); fetchPrices(); loadTransactions()
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "الكلمات غير صحيحة، تحقق منها وأعد المحاولة") }
            }
        }
    }

    fun confirmBackupDone() {
        _uiState.update { it.copy(currentScreen = Screen.Home) }
        refreshBalance(); fetchPrices(); loadTransactions()
    }

    // ─── الشبكة ──────────────────────────────────────────
    fun toggleNetwork() {
        NetworkConfig.setTestnet(!NetworkConfig.isTestnet)
        _uiState.update { it.copy(isTestnet = NetworkConfig.isTestnet) }
        refreshBalance()
    }

    fun switchChain(chain: ActiveChain) {
        _uiState.update { it.copy(activeChain = chain, errorMessage = null) }
        refreshBalance()
    }

    // ─── تحديث الأرصدة ───────────────────────────────────
    fun refreshBalance() {
        // جلب جميع الشبكات دائماً
        refreshTronBalance()
        refreshBscBalance()
        refreshSolanaBalance()
        refreshEthBalance()
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

    private fun refreshEthBalance() {
        val ethAddr = _uiState.value.ethAddress; if (ethAddr.isEmpty()) return
        viewModelScope.launch {
            val ethBal  = EthApiService.getEthBalance(ethAddr)
            val usdtBal = EthApiService.getUsdtBalance(ethAddr)
            _uiState.update { it.copy(ethBalance = ethBal, ethUsdtBalance = usdtBal) }
        }
    }

    private fun refreshSolanaBalance() {
        val solAddr = _uiState.value.solanaAddress; if (solAddr.isEmpty()) return
        viewModelScope.launch {
            val sol  = SolanaTransactionSigner.getSolBalance(solAddr)
            val usdt = SolanaTransactionSigner.getUsdtBalance(solAddr)
            _uiState.update { it.copy(solBalance = sol, solUsdtBalance = usdt) }
        }
    }

    // ─── الأسعار ─────────────────────────────────────────
    fun fetchPrices() {
        viewModelScope.launch {
            try {
                val prices = PriceRepository.getPrices()
                _uiState.update { it.copy(bnbUsdPrice = prices.bnbUsd, trxUsdPrice = prices.trxUsd, solUsdPrice = prices.solUsd, ethUsdPrice = prices.ethUsd) }
            } catch (_: Exception) {}
        }
    }

    // ─── المعاملات ───────────────────────────────────────
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

    // ─── دفتر العناوين ───────────────────────────────────
    fun loadAddressBook() { _uiState.update { it.copy(savedAddresses = AddressBookManager.getAll(app)) } }
    fun saveAddress(name: String, address: String) {
        val chain = when (_uiState.value.activeChain) { ActiveChain.BSC -> "BSC"; ActiveChain.SOLANA -> "SOL"; else -> "TRON" }
        AddressBookManager.save(app, SavedAddress(name = name, address = address, chain = chain))
        loadAddressBook()
    }
    fun deleteAddress(id: String) { AddressBookManager.delete(app, id); loadAddressBook() }

    // ─── الإشعارات ───────────────────────────────────────
    fun toggleNotifications(enabled: Boolean) {
        if (enabled) WalletNotificationWorker.schedule(app) else WalletNotificationWorker.cancel(app)
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    // ─── التنقل ──────────────────────────────────────────
    fun navigate(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
        when (screen) {
            is Screen.History     -> loadTransactions()
            is Screen.AddressBook -> loadAddressBook()
            is Screen.Home        -> { fetchPrices(); loadTransactions() }
            is Screen.Wallets     -> _uiState.update { it.copy(wallets = MultiWalletStorage.getAll(app)) }
            else -> {}
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearSendSuccess() { _uiState.update { it.copy(sendSuccess = false, sendTxId = "") } }

    // ─── الإرسال ─────────────────────────────────────────
    private fun activeWallet() = MultiWalletStorage.getActive(app)

    fun sendUsdt(toAddress: String, amount: Double) {
        when (_uiState.value.activeChain) {
            ActiveChain.TRON -> {
                val pk = activeWallet()?.tronPrivateKey ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    when (val r = TronTransactionSigner.sendUsdt(_uiState.value.address, toAddress, amount, pk)) {
                        is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                        is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                    }
                }
            }
            ActiveChain.BSC -> {
                val pk = activeWallet()?.bscPrivateKey ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    when (val r = BscTransactionSigner.sendUsdt(_uiState.value.bscAddress, toAddress, amount, pk)) {
                        is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                        is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                    }
                }
            }
            ActiveChain.SOLANA -> {
                val pk = activeWallet()?.solanaPrivateKey ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    when (val r = SolanaTransactionSigner.sendUsdtSpl(_uiState.value.solanaAddress, toAddress, amount, pk)) {
                        is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshSolanaBalance() }
                        is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                    }
                }
            }
            ActiveChain.ETHEREUM -> {
                val pk = activeWallet()?.ethereumPrivateKey ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    when (val r = EthTransactionSigner.sendUsdt(_uiState.value.ethAddress, toAddress, amount, pk)) {
                        is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshEthBalance() }
                        is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                    }
                }
            }
        }
    }

    fun sendTrx(toAddress: String, amount: Double) {
        val pk = activeWallet()?.tronPrivateKey ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = TronTransactionSigner.sendTrx(_uiState.value.address, toAddress, amount, pk)) {
                is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun sendBnb(toAddress: String, amount: Double) {
        val pk = activeWallet()?.bscPrivateKey ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = BscTransactionSigner.sendBnb(_uiState.value.bscAddress, toAddress, amount, pk)) {
                is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshBalance() }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun sendSol(toAddress: String, amount: Double) {
        val pk = activeWallet()?.solanaPrivateKey ?: run {
            _uiState.update { it.copy(errorMessage = "لم يتم العثور على مفتاح Solana") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = SolanaTransactionSigner.sendSol(_uiState.value.solanaAddress, toAddress, amount, pk)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }
                    refreshSolanaBalance()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun sendEth(toAddress: String, amount: Double) {
        val pk = activeWallet()?.ethereumPrivateKey ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = EthTransactionSigner.sendEth(_uiState.value.ethAddress, toAddress, amount, pk)) {
                is Result.Success -> { _uiState.update { it.copy(isLoading = false, sendSuccess = true, sendTxId = r.data) }; refreshEthBalance() }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
            }
        }
    }

    fun deleteWallet() {
        val allWallets = MultiWalletStorage.getAll(app)
        if (allWallets.size <= 1) {
            MultiWalletStorage.delete(app, _uiState.value.activeWalletId)
            secureStorage.deleteWallet()
            WalletNotificationWorker.cancel(app)
            _uiState.update { WalletUiState(currentScreen = Screen.CreateWallet) }
        } else {
            deleteWalletById(_uiState.value.activeWalletId)
        }
    }

    fun getMnemonic(): List<String> {
        return activeWallet()?.mnemonic?.split(" ")
            ?: secureStorage.getMnemonic()
            ?: emptyList()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateChecker.checkForUpdate(BuildConfig.BUILD_NUMBER)
            if (info.isUpdateAvailable) {
                _uiState.update { it.copy(updateAvailable = true, updateVersion = info.latestVersion, updateDownloadUrl = info.downloadUrl) }
            }
        }
    }
}
