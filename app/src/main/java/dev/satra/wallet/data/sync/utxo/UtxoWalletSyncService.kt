package dev.satra.wallet.data.sync.utxo

import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.db.WalletTransactionStatus
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import dev.satra.wallet.wallet.derivation.DerivedReceiveAccount
import dev.satra.wallet.wallet.derivation.SatraAddressDerivation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

data class UtxoWalletSecrets(
    val mnemonic: String,
    val passphrase: String?,
)

class UtxoWalletSyncService(
    private val electrumClient: UtxoElectrumClient = UtxoElectrumClient(),
    private val gapLimit: Int = 20,
    private val maxScanBatches: Int = 12,
    private val maxBatchSize: Int = 64,
    private val maxParallelNetworkSyncs: Int = 4,
) {
    suspend fun syncWallet(
        wallet: WalletRecord,
        addresses: List<WalletAddressRecord>,
        walletSecrets: UtxoWalletSecrets? = null,
        networkId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        onNetworkResult: suspend (UtxoNetworkSyncResult) -> Unit = {},
    ): UtxoWalletSyncResult = coroutineScope {
        val requestedNetworks = if (networkId == null) {
            if (walletSecrets != null) {
                UtxoElectrumProviderRegistry.supportedNetworkIds
            } else {
                addresses
                    .map { it.networkId }
                    .filter { it in UtxoElectrumProviderRegistry.supportedNetworkIds }
                    .toSet()
            }
        } else {
            setOf(networkId).also { UtxoElectrumProviderRegistry.requireConfig(networkId) }
        }

        val networkLimiter = Semaphore(maxParallelNetworkSyncs.coerceAtLeast(1))
        val results = requestedNetworks
            .sorted()
            .map { utxoNetworkId ->
                async {
                    networkLimiter.withPermit {
                        val result = syncNetwork(
                            wallet = wallet,
                            networkId = utxoNetworkId,
                            addresses = addresses.filter { it.networkId == utxoNetworkId },
                            walletSecrets = walletSecrets,
                            nowMillis = nowMillis,
                        )
                        runCatching { onNetworkResult(result) }
                        result
                    }
                }
            }
            .awaitAll()

        UtxoWalletSyncResult(
            walletId = wallet.walletId,
            networkResults = results,
        )
    }

    private suspend fun syncNetwork(
        wallet: WalletRecord,
        networkId: String,
        addresses: List<WalletAddressRecord>,
        walletSecrets: UtxoWalletSecrets?,
        nowMillis: Long,
    ): UtxoNetworkSyncResult {
        val config = UtxoElectrumProviderRegistry.requireConfig(networkId)
        var lastError: String? = null
        config.providers.forEach { provider ->
            val result = runCatching {
                electrumClient.serverVersion(provider)
                val latestBlockHeight = electrumClient.latestBlockHeight(provider)
                val scan = if (walletSecrets != null) {
                    scanMnemonicWallet(
                        networkId = networkId,
                        provider = provider,
                        walletSecrets = walletSecrets,
                    )
                } else {
                    scanStoredAddresses(
                        networkId = networkId,
                        provider = provider,
                        addresses = addresses,
                    )
                }
                val transactions = normalizeTransactions(
                    walletId = wallet.walletId,
                    config = config,
                    provider = provider,
                    latestBlockHeight = latestBlockHeight,
                    scripts = scan.scripts,
                    historiesByScriptHash = scan.historiesByScriptHash,
                )
                val utxos = scan.unspentByScriptHash.values.flatten()
                val balanceRaw = utxos.sumOf { it.valueSats }.coerceAtLeast(0L)
                UtxoNetworkSyncResult(
                    walletId = wallet.walletId,
                    networkId = networkId,
                    addressesScanned = scan.scripts.size,
                    balanceCompleteness = EvmSyncCompleteness.Complete,
                    historyCompleteness = EvmSyncCompleteness.Complete,
                    balances = listOf(
                        UtxoAssetBalance(
                            assetId = config.nativeAssetId,
                            networkId = networkId,
                            balanceRaw = balanceRaw.toString(),
                            balanceDecimal = balanceRaw.toDecimal(config.decimals),
                            providerName = provider.name,
                            blockHeight = latestBlockHeight,
                            syncedAtMillis = nowMillis,
                            utxos = utxos,
                        ),
                    ),
                    transactions = transactions,
                    providerName = provider.name,
                    latestBlockHeight = latestBlockHeight,
                    scannedAccounts = scan.scannedAccounts,
                    error = null,
                )
            }
            result.onSuccess { return it }
            lastError = "${provider.name}: ${result.exceptionOrNull()?.message ?: "Unknown Electrum error."}"
        }
        return UtxoNetworkSyncResult(
            walletId = wallet.walletId,
            networkId = networkId,
            addressesScanned = addresses.size,
            balanceCompleteness = EvmSyncCompleteness.Failed,
            historyCompleteness = EvmSyncCompleteness.Failed,
            balances = emptyList(),
            transactions = emptyList(),
            providerName = null,
            latestBlockHeight = null,
            scannedAccounts = emptyList(),
            error = lastError ?: "No Electrum provider succeeded.",
        )
    }

    private suspend fun scanStoredAddresses(
        networkId: String,
        provider: UtxoElectrumProvider,
        addresses: List<WalletAddressRecord>,
    ): UtxoScanResult {
        val scripts = addresses.mapNotNull { address ->
            runCatching {
                UtxoScript.watchedScriptForAddress(
                    networkId = networkId,
                    address = address.address,
                    walletAddressId = address.addressId,
                    derivationPath = address.derivationPath,
                    derivationName = address.label,
                    addressIndex = address.addressIndex,
                    isChange = address.isChange,
                )
            }.getOrNull()
        }
        require(scripts.isNotEmpty()) { "No valid $networkId address stored for Electrum sync." }
        return fetchScanResult(provider, scripts, scannedAccounts = emptyList())
    }

    private suspend fun scanMnemonicWallet(
        networkId: String,
        provider: UtxoElectrumProvider,
        walletSecrets: UtxoWalletSecrets,
    ): UtxoScanResult {
        val allScripts = mutableListOf<UtxoWatchedScript>()
        val allAccounts = mutableListOf<DerivedReceiveAccount>()
        val balancesByScriptHash = mutableMapOf<String, UtxoScriptBalance>()
        val historiesByScriptHash = mutableMapOf<String, List<UtxoHistoryEntry>>()
        val unspentByScriptHash = mutableMapOf<String, List<UtxoUnspentOutput>>()
        var activeGroups: Set<UtxoScanGroup>? = null

        repeat(maxScanBatches) { batch ->
            val startIndex = batch * gapLimit
            val accounts = SatraAddressDerivation.deriveUtxoScanAccounts(
                mnemonic = walletSecrets.mnemonic,
                passphrase = walletSecrets.passphrase,
                networkId = networkId,
                startIndex = startIndex,
                gapLimit = gapLimit,
            )
            if (accounts.isEmpty()) return@repeat
            if (activeGroups == null) {
                activeGroups = accounts.map { it.scanGroup }.toSet()
            }
            val currentActiveGroups = activeGroups.orEmpty()
            val activeAccounts = accounts.filter { it.scanGroup in currentActiveGroups }
            if (activeAccounts.isEmpty()) {
                activeGroups = emptySet()
                return@repeat
            }
            val scripts = activeAccounts.map { account ->
                UtxoScript.watchedScriptForAddress(
                    networkId = networkId,
                    address = account.address,
                    derivationPath = account.derivationPath,
                    derivationName = account.derivationName,
                    addressIndex = account.addressIndex,
                    isChange = account.isChange,
                )
            }
            val result = fetchScanResult(provider, scripts, activeAccounts)
            allScripts += result.scripts
            allAccounts += result.scannedAccounts
            balancesByScriptHash += result.balancesByScriptHash
            historiesByScriptHash += result.historiesByScriptHash
            unspentByScriptHash += result.unspentByScriptHash
            val activeWithHistory = activeAccounts
                .filter { account ->
                    val script = scripts.first { it.address == account.address }
                    result.historiesByScriptHash[script.scriptHash].orEmpty().isNotEmpty()
                }
                .map { it.scanGroup }
                .toSet()
            activeGroups = currentActiveGroups.intersect(activeWithHistory)
            if (activeGroups.isNullOrEmpty()) return@repeat
        }

        require(allScripts.isNotEmpty()) { "No $networkId scan address could be derived." }
        return UtxoScanResult(
            scripts = allScripts.distinctBy { it.scriptHash },
            scannedAccounts = allAccounts.distinctBy { "${it.networkId}:${it.derivationPath}:${it.address}" },
            balancesByScriptHash = balancesByScriptHash,
            historiesByScriptHash = historiesByScriptHash,
            unspentByScriptHash = unspentByScriptHash,
        )
    }

    private suspend fun fetchScanResult(
        provider: UtxoElectrumProvider,
        scripts: List<UtxoWatchedScript>,
        scannedAccounts: List<DerivedReceiveAccount>,
    ): UtxoScanResult = coroutineScope {
        val hashes = scripts.map { it.scriptHash }
        val balanceDeferred = async { fetchInChunks(hashes) { electrumClient.getBalances(provider, it) } }
        val historyDeferred = async { fetchInChunks(hashes) { electrumClient.getHistories(provider, it) } }
        val unspentDeferred = async { fetchScriptResultsInChunks(scripts) { electrumClient.listUnspent(provider, it) } }
        UtxoScanResult(
            scripts = scripts,
            scannedAccounts = scannedAccounts,
            balancesByScriptHash = balanceDeferred.await(),
            historiesByScriptHash = historyDeferred.await(),
            unspentByScriptHash = unspentDeferred.await(),
        )
    }

    private suspend fun normalizeTransactions(
        walletId: String,
        config: UtxoNetworkConfig,
        provider: UtxoElectrumProvider,
        latestBlockHeight: Long,
        scripts: List<UtxoWatchedScript>,
        historiesByScriptHash: Map<String, List<UtxoHistoryEntry>>,
    ): List<UtxoNormalizedTransaction> {
        val scriptByHex = scripts.associateBy { it.scriptPubKeyHex }
        val histories = historiesByScriptHash.values.flatten()
        val heightByTransactionHash = histories
            .groupBy { it.transactionHash }
            .mapValues { (_, entries) -> entries.maxOfOrNull { it.height } ?: 0L }
        val transactionHashes = histories.map { it.transactionHash }.distinct()
        if (transactionHashes.isEmpty()) return emptyList()

        val transactions = fetchTransactions(provider, transactionHashes)
        val blockHeadersByHeight = fetchBlockHeaders(
            provider = provider,
            heights = heightByTransactionHash.values.filter { height -> height > 0L },
        )
        val parsedTransactions = transactions.mapValues { (_, tx) ->
            runCatching { UtxoTransactionParser.parse(tx.hex) }.getOrNull()
        }.filterValues { it != null }.mapValues { checkNotNull(it.value) }

        val previousHashes = parsedTransactions.values
            .flatMap { transaction -> transaction.inputs.map { it.previousTransactionHash } }
            .filterNot { it.all { char -> char == '0' } }
            .distinct()
        val previousTransactions = fetchTransactions(provider, previousHashes).mapValues { (_, tx) ->
            runCatching { UtxoTransactionParser.parse(tx.hex) }.getOrNull()
        }.filterValues { it != null }.mapValues { checkNotNull(it.value) }

        return transactionHashes.mapNotNull { txHash ->
            val parsed = parsedTransactions[txHash] ?: return@mapNotNull null
            val verbose = transactions[txHash]
            val receivedOutputs = parsed.outputs
                .filter { output -> output.scriptPubKeyHex in scriptByHex }
            val spentOutputs = parsed.inputs.mapNotNull { input ->
                previousTransactions[input.previousTransactionHash]
                    ?.outputs
                    ?.firstOrNull { output -> output.index == input.previousOutputIndex }
                    ?.takeIf { output -> output.scriptPubKeyHex in scriptByHex }
                    ?.let { output -> input to output }
            }
            val receivedSats = receivedOutputs.sumOf { it.valueSats }
            val spentSats = spentOutputs.sumOf { it.second.valueSats }
            if (receivedSats == 0L && spentSats == 0L) return@mapNotNull null

            val netSats = receivedSats - spentSats
            val direction = when {
                spentSats > 0L && netSats == 0L -> WalletTransactionDirection.Self
                netSats > 0L -> WalletTransactionDirection.Incoming
                netSats < 0L -> WalletTransactionDirection.Outgoing
                else -> WalletTransactionDirection.Unknown
            }
            val amountSats = when (direction) {
                WalletTransactionDirection.Incoming -> netSats
                WalletTransactionDirection.Outgoing -> -netSats
                WalletTransactionDirection.Self -> receivedSats
                WalletTransactionDirection.Unknown -> maxOf(receivedSats, spentSats)
            }.coerceAtLeast(0L)
            val height = heightByTransactionHash[txHash]?.takeIf { it > 0L }
            val blockHeader = height?.let(blockHeadersByHeight::get)
            val confirmations = verbose?.confirmations
                ?: height?.let { (latestBlockHeight - it + 1L).coerceAtLeast(0L).toInt() }
                ?: 0
            val status = if (height == null) WalletTransactionStatus.Pending else WalletTransactionStatus.Success
            val feeSats = parsed.inputs
                .map { input ->
                    previousTransactions[input.previousTransactionHash]
                        ?.outputs
                        ?.firstOrNull { output -> output.index == input.previousOutputIndex }
                        ?.valueSats
                }
                .takeIf { values -> values.isNotEmpty() && values.all { it != null } && spentSats > 0L }
                ?.sumOf { checkNotNull(it) }
                ?.minus(parsed.outputs.sumOf { it.valueSats })
                ?.coerceAtLeast(0L)
            val fromAddress = spentOutputs.firstOrNull()
                ?.second
                ?.scriptPubKeyHex
                ?.let { scriptByHex[it]?.address ?: UtxoScript.addressFromScriptPubKeyOrNull(config.networkId, it) }
            val toAddress = when (direction) {
                WalletTransactionDirection.Incoming,
                WalletTransactionDirection.Self,
                -> receivedOutputs.firstOrNull()?.scriptPubKeyHex?.let { scriptByHex[it]?.address }
                WalletTransactionDirection.Outgoing -> parsed.outputs
                    .firstOrNull { output -> output.scriptPubKeyHex !in scriptByHex && output.valueSats > 0L }
                    ?.scriptPubKeyHex
                    ?.let { UtxoScript.addressFromScriptPubKeyOrNull(config.networkId, it) }
                WalletTransactionDirection.Unknown -> null
            }
            UtxoNormalizedTransaction(
                walletId = walletId,
                assetId = config.nativeAssetId,
                networkId = config.networkId,
                transactionHash = txHash,
                direction = direction,
                status = status,
                amountRaw = amountSats.toString(),
                amountDecimal = amountSats.toDecimal(config.decimals),
                feeRaw = feeSats?.toString(),
                feeDecimal = feeSats?.toDecimal(config.decimals),
                feeAssetId = config.nativeAssetId,
                fromAddress = fromAddress,
                toAddress = toAddress,
                blockHeight = height,
                blockHash = verbose?.blockHash ?: blockHeader?.blockHash,
                confirmations = confirmations,
                timestampMillis = verbose?.timestampMillis ?: blockHeader?.timestampMillis ?: 0L,
                providerName = provider.name,
                metadataJson = JSONObject()
                    .put("syncFamily", "utxo")
                    .put("syncProvider", provider.name)
                    .put("syncStatus", if (height == null) "pending" else "complete")
                    .put("latestBlockHeight", latestBlockHeight)
                    .put("receivedSats", receivedSats)
                    .put("spentSats", spentSats)
                    .toString(),
            )
        }.sortedByDescending { it.timestampMillis }
    }

    private suspend fun fetchBlockHeaders(
        provider: UtxoElectrumProvider,
        heights: List<Long>,
    ): Map<Long, UtxoBlockHeader> = coroutineScope {
        heights
            .distinct()
            .chunked(maxBatchSize)
            .map { chunk -> async { electrumClient.getBlockHeaders(provider, chunk) } }
            .awaitAll()
            .fold(mutableMapOf()) { acc, next -> acc.apply { putAll(next) } }
    }

    private suspend fun fetchTransactions(
        provider: UtxoElectrumProvider,
        transactionHashes: List<String>,
    ): Map<String, UtxoVerboseTransaction> = coroutineScope {
        transactionHashes
            .distinct()
            .chunked(maxBatchSize)
            .map { chunk -> async { electrumClient.getTransactions(provider, chunk) } }
            .awaitAll()
            .fold(mutableMapOf()) { acc, next -> acc.apply { putAll(next) } }
    }

    private suspend fun <T> fetchInChunks(
        scriptHashes: List<String>,
        block: suspend (List<String>) -> Map<String, T>,
    ): Map<String, T> = coroutineScope {
        scriptHashes
            .distinct()
            .chunked(maxBatchSize)
            .map { chunk -> async { block(chunk) } }
            .awaitAll()
            .fold(mutableMapOf()) { acc, next -> acc.apply { putAll(next) } }
    }

    private suspend fun <T> fetchScriptResultsInChunks(
        scripts: List<UtxoWatchedScript>,
        block: suspend (List<UtxoWatchedScript>) -> Map<String, T>,
    ): Map<String, T> = coroutineScope {
        scripts
            .distinctBy { it.scriptHash }
            .chunked(maxBatchSize)
            .map { chunk -> async { block(chunk) } }
            .awaitAll()
            .fold(mutableMapOf()) { acc, next -> acc.apply { putAll(next) } }
    }
}

private data class UtxoScanResult(
    val scripts: List<UtxoWatchedScript>,
    val scannedAccounts: List<DerivedReceiveAccount>,
    val balancesByScriptHash: Map<String, UtxoScriptBalance>,
    val historiesByScriptHash: Map<String, List<UtxoHistoryEntry>>,
    val unspentByScriptHash: Map<String, List<UtxoUnspentOutput>>,
)

private data class UtxoScanGroup(
    val derivationName: String,
    val isChange: Boolean,
)

private val DerivedReceiveAccount.scanGroup: UtxoScanGroup
    get() = UtxoScanGroup(derivationName, isChange)

private fun Long.toDecimal(decimals: Int): String =
    BigDecimal(this)
        .movePointLeft(decimals)
        .setScale(decimals, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()
