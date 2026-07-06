package dev.satra.wallet.data.send

import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.assets.SupportedNetwork
import dev.satra.wallet.data.send.aptos.AptosSendClient
import dev.satra.wallet.data.send.aptos.AptosSigningRequest
import dev.satra.wallet.data.send.aptos.AptosTransactionSigner
import dev.satra.wallet.data.send.cosmos.CosmosDirectSigner
import dev.satra.wallet.data.send.cosmos.CosmosSendClient
import dev.satra.wallet.data.send.cosmos.CosmosSigningRequest
import dev.satra.wallet.data.send.evm.EvmLegacyTransaction
import dev.satra.wallet.data.send.evm.EvmLegacyTransactionSigner
import dev.satra.wallet.data.send.near.NearSendClient
import dev.satra.wallet.data.send.near.NearSigningRequest
import dev.satra.wallet.data.send.near.NearTransactionSigner
import dev.satra.wallet.data.send.polkadot.PolkadotSendClient
import dev.satra.wallet.data.send.polkadot.PolkadotSigningRequest
import dev.satra.wallet.data.send.polkadot.PolkadotTransactionSigner
import dev.satra.wallet.data.send.ripple.RippleSendClient
import dev.satra.wallet.data.send.ripple.RippleSigningRequest
import dev.satra.wallet.data.send.ripple.RippleTransactionSigner
import dev.satra.wallet.data.send.solana.SolanaSigningRequest
import dev.satra.wallet.data.send.solana.SolanaTransactionSigner
import dev.satra.wallet.data.send.stellar.StellarSendClient
import dev.satra.wallet.data.send.stellar.StellarSigningRequest
import dev.satra.wallet.data.send.stellar.StellarTransactionSigner
import dev.satra.wallet.data.send.sui.SuiSendClient
import dev.satra.wallet.data.send.ton.TonSendClient
import dev.satra.wallet.data.send.ton.TonSigningRequest
import dev.satra.wallet.data.send.ton.TonTransactionSigner
import dev.satra.wallet.data.send.tron.TronSendClient
import dev.satra.wallet.data.send.utxo.UtxoSigningRequest
import dev.satra.wallet.data.send.utxo.UtxoTransactionSigner
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import dev.satra.wallet.data.sync.accountchain.AccountChainProviderRegistry
import dev.satra.wallet.data.sync.evm.EvmAbi
import dev.satra.wallet.data.sync.evm.EvmJsonRpcClient
import dev.satra.wallet.data.sync.evm.EvmProviderRegistry
import dev.satra.wallet.data.sync.solana.SolanaJsonRpcClient
import dev.satra.wallet.data.sync.solana.SolanaProviderRegistry
import dev.satra.wallet.data.sync.utxo.UtxoElectrumClient
import dev.satra.wallet.data.sync.utxo.UtxoElectrumProvider
import dev.satra.wallet.data.sync.utxo.UtxoElectrumProviderRegistry
import dev.satra.wallet.data.sync.utxo.UtxoScript
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

class SatraSendService {
    suspend fun signAndBroadcast(request: SatraSendRequest): SatraBroadcastResult {
        val network = SupportedAssetCatalog.networks
            .firstOrNull { it.networkId == request.networkId }
            ?: throw SatraSendException.UnsupportedNetwork(request.networkId)

        return when (network.family) {
            "aptos" -> signAndBroadcastAptos(request, network)
            "cosmos" -> signAndBroadcastCosmos(request, network)
            "evm" -> signAndBroadcastEvm(request, network)
            "near" -> signAndBroadcastNear(request, network)
            "polkadot" -> signAndBroadcastPolkadot(request, network)
            "ripple" -> signAndBroadcastRipple(request, network)
            "utxo" -> signAndBroadcastUtxo(request, network)
            "solana" -> signAndBroadcastSolana(request, network)
            "stellar" -> signAndBroadcastStellar(request, network)
            "sui" -> signAndBroadcastSui(request, network)
            "ton" -> signAndBroadcastTon(request, network)
            "tron" -> signAndBroadcastTron(request, network)
            else -> throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
    }

    private suspend fun signAndBroadcastEvm(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val config = EvmProviderRegistry.requireConfig(request.networkId)
        val client = EvmJsonRpcClient(config)
        val fromAddress = try {
            "0x${EvmAbi.normalizeAddress(request.sourceAddress)}"
        } catch (error: Throwable) {
            throw SatraSendException.MissingSigningKey()
        }
        val recipientAddress = try {
            "0x${EvmAbi.normalizeAddress(request.recipientAddress)}"
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }

        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        val contractAddress = if (isNative) {
            null
        } else {
            request.contractAddress?.let { "0x${EvmAbi.normalizeAddress(it)}" }
                ?: throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        val toAddress = contractAddress ?: recipientAddress
        val data = if (contractAddress == null) {
            "0x"
        } else {
            EvmAbi.transferCallData(recipientAddress, amountRaw)
        }
        val txValue = if (isNative) amountRaw else BigInteger.ZERO

        val nonce = client.transactionCount(fromAddress).value
        val gasPrice = client.gasPrice().value
        val estimatedGas = try {
            client.estimateGas(
                fromAddress = fromAddress,
                toAddress = toAddress,
                value = txValue,
                data = data,
            ).value
        } catch (error: Throwable) {
            throw SatraSendException.BroadcastFailed(error)
        }
        val gasLimit = estimatedGas.withGasBuffer()
        val feeRaw = gasLimit.multiply(gasPrice)
        if (isNative && availableRaw < amountRaw.add(feeRaw)) {
            throw SatraSendException.InsufficientBalance()
        }
        if (!isNative) {
            val nativeBalance = try {
                client.nativeBalance(fromAddress).value
            } catch (error: Throwable) {
                throw SatraSendException.BroadcastFailed(error)
            }
            if (nativeBalance < feeRaw) {
                throw SatraSendException.InsufficientBalance()
            }
        }

        val rawTransaction = try {
            EvmLegacyTransactionSigner.sign(
                transaction = EvmLegacyTransaction(
                    nonce = nonce,
                    gasPrice = gasPrice,
                    gasLimit = gasLimit,
                    toAddress = toAddress,
                    value = txValue,
                    data = data.hexToBytes(),
                    chainId = config.chainId,
                ),
                privateKeyHex = request.privateKeyHex,
            )
        } catch (error: Throwable) {
            throw SatraSendException.MissingSigningKey()
        }
        val localTransactionHash = EvmLegacyTransactionSigner.transactionHash(rawTransaction)

        val broadcast = try {
            client.sendRawTransaction(rawTransaction)
        } catch (error: Throwable) {
            throw SatraSendException.BroadcastFailed(error)
        }

        val feeAssetId = SupportedAssetCatalog.assets
            .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
            ?.assetId

        return SatraBroadcastResult(
            transactionHash = localTransactionHash,
            rawTransaction = rawTransaction,
            providerName = broadcast.provider.name,
            fromAddress = fromAddress,
            toAddress = recipientAddress,
            amountRaw = amountRaw,
            amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
            feeRaw = feeRaw,
            feeDecimal = EvmAbi.rawToDecimalString(feeRaw, network.nativeDecimals),
            feeAssetId = feeAssetId,
            nonce = nonce,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun signAndBroadcastAptos(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        try {
            AptosTransactionSigner.normalizeAddress(request.sourceAddress)
            AptosTransactionSigner.normalizeAddress(request.recipientAddress)
            request.contractAddress?.let(AptosTransactionSigner::normalizeAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val fullnodeProviders = config.providers.filter { provider -> provider.name.contains("fullnode") }
            .ifEmpty { config.providers }
        val client = AptosSendClient()
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        var lastError: Throwable? = null
        fullnodeProviders.forEach { provider ->
            try {
                val sequence = client.accountSequence(provider, request.sourceAddress)
                val gasUnitPrice = client.gasUnitPrice(provider)
                val maxGasAmount = DEFAULT_APTOS_MAX_GAS_AMOUNT
                val signed = AptosTransactionSigner.sign(
                    AptosSigningRequest(
                        sourceAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        amountRaw = amountRaw,
                        assetMetadataAddress = if (isNative) null else request.contractAddress,
                        sequenceNumber = sequence,
                        gasUnitPrice = gasUnitPrice,
                        maxGasAmount = maxGasAmount,
                        expirationTimestampSeconds = (System.currentTimeMillis() / 1_000L + APTOS_EXPIRATION_SECONDS).toULong(),
                        privateKeyHex = request.privateKeyHex,
                    ),
                )
                if (isNative && availableRaw < amountRaw.add(signed.maxFeeOctas)) {
                    throw SatraSendException.InsufficientBalance()
                }
                val txHash = client.submitSignedTransaction(provider, signed.signedTransactionBytes)
                val feeAssetId = SupportedAssetCatalog.assets
                    .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
                    ?.assetId
                return SatraBroadcastResult(
                    transactionHash = txHash,
                    rawTransaction = signed.signedTransactionHex,
                    providerName = provider.name,
                    fromAddress = AptosTransactionSigner.normalizeAddress(request.sourceAddress),
                    toAddress = AptosTransactionSigner.normalizeAddress(request.recipientAddress),
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = signed.maxFeeOctas,
                    feeDecimal = EvmAbi.rawToDecimalString(signed.maxFeeOctas, network.nativeDecimals),
                    feeAssetId = feeAssetId,
                    nonce = BigInteger.valueOf(sequence.toLong()),
                    timestampMillis = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No Aptos provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastCosmos(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        val denom = if (isNative) {
            CosmosDirectSigner.KAVA_NATIVE_DENOM
        } else {
            request.contractAddress ?: throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        val feeUkava = BigInteger.valueOf(DEFAULT_KAVA_FEE_UKAVA)
        if (availableRaw < amountRaw || (isNative && availableRaw < amountRaw.add(feeUkava))) {
            throw SatraSendException.InsufficientBalance()
        }
        if (request.sourceAddress.isBlank() || request.recipientAddress.isBlank()) {
            throw SatraSendException.InvalidRecipient()
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val client = CosmosSendClient()
        var lastError: Throwable? = null
        config.providers.forEach { provider ->
            try {
                val account = client.account(provider, request.sourceAddress)
                val signed = CosmosDirectSigner.sign(
                    CosmosSigningRequest(
                        fromAddress = request.sourceAddress,
                        toAddress = request.recipientAddress,
                        denom = denom,
                        amountRaw = amountRaw,
                        accountNumber = account.accountNumber,
                        sequence = account.sequence,
                        gasLimit = DEFAULT_KAVA_GAS_LIMIT,
                        feeUkava = feeUkava,
                        privateKeyHex = request.privateKeyHex,
                    ),
                )
                val txHash = client.broadcast(provider, signed.txRawBase64)
                val feeAssetId = SupportedAssetCatalog.assets
                    .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
                    ?.assetId
                return SatraBroadcastResult(
                    transactionHash = txHash,
                    rawTransaction = signed.txRawBase64,
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = signed.feeUkava,
                    feeDecimal = EvmAbi.rawToDecimalString(signed.feeUkava, network.nativeDecimals),
                    feeAssetId = feeAssetId,
                    nonce = BigInteger.valueOf(account.sequence),
                    timestampMillis = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No Cosmos provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastStellar(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        if (request.assetType.uppercase(Locale.US) != "NATIVE") {
            throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val memoText = normalizedStellarMemo(request.memo)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw.add(BigInteger.valueOf(STELLAR_BASE_FEE_STROOPS.toLong()))) {
            throw SatraSendException.InsufficientBalance()
        }
        try {
            StellarTransactionSigner.validateAddress(request.sourceAddress)
            StellarTransactionSigner.validateAddress(request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val client = StellarSendClient()
        var lastError: Throwable? = null
        config.providers.forEach { provider ->
            try {
                val sequence = client.accountSequence(provider, request.sourceAddress)
                val createAccount = !client.accountExists(provider, request.recipientAddress)
                val signed = StellarTransactionSigner.sign(
                    StellarSigningRequest(
                        sourceAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        amountRaw = amountRaw,
                        accountSequence = sequence,
                        createAccount = createAccount,
                        memoText = memoText,
                        privateKeyHex = request.privateKeyHex,
                    ),
                )
                val txHash = client.submitTransaction(provider, signed.envelopeBase64)
                val feeRaw = BigInteger.valueOf(signed.feeStroops.toLong())
                return SatraBroadcastResult(
                    transactionHash = txHash,
                    rawTransaction = signed.envelopeBase64,
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = feeRaw,
                    feeDecimal = EvmAbi.rawToDecimalString(feeRaw, network.nativeDecimals),
                    feeAssetId = request.assetId,
                    nonce = BigInteger.valueOf(sequence + 1L),
                    timestampMillis = System.currentTimeMillis(),
                    memo = memoText,
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No Stellar provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastNear(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        if (request.sourceAddress.isBlank() || request.recipientAddress.isBlank()) {
            throw SatraSendException.InvalidRecipient()
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val rpcProviders = config.providers.filter { provider -> provider.name.contains("rpc") }
            .ifEmpty { config.providers }
        val client = NearSendClient()
        val publicKey = try {
            NearTransactionSigner.publicKeyForPrivateKey(request.privateKeyHex)
        } catch (error: Throwable) {
            throw SatraSendException.MissingSigningKey()
        }
        var lastError: Throwable? = null
        rpcProviders.forEach { provider ->
            try {
                val accessKey = client.accessKey(provider, request.sourceAddress, publicKey)
                val signed = NearTransactionSigner.sign(
                    NearSigningRequest(
                        sourceAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        amountRaw = amountRaw,
                        tokenContract = if (isNative) null else request.contractAddress
                            ?: throw SatraSendException.UnsupportedNetwork(request.networkId),
                        nonce = accessKey.nonce,
                        recentBlockHash = accessKey.blockHash,
                        privateKeyHex = request.privateKeyHex,
                    ),
                )
                if (isNative && availableRaw < amountRaw.add(signed.feeYocto)) {
                    throw SatraSendException.InsufficientBalance()
                }
                val txHash = client.broadcast(provider, signed.signedTransactionBase64)
                val feeAssetId = SupportedAssetCatalog.assets
                    .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
                    ?.assetId
                return SatraBroadcastResult(
                    transactionHash = txHash.ifBlank { signed.transactionHashBase58 },
                    rawTransaction = signed.signedTransactionBase64,
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = signed.feeYocto,
                    feeDecimal = EvmAbi.rawToDecimalString(signed.feeYocto, network.nativeDecimals),
                    feeAssetId = feeAssetId,
                    nonce = BigInteger(accessKey.nonce.toString()),
                    timestampMillis = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No NEAR provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastPolkadot(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        val assetHubAssetId = if (isNative) {
            null
        } else {
            request.contractAddress?.toBigIntegerOrNull()
                ?: throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        try {
            dev.satra.wallet.data.sync.accountchain.PolkadotStorage.decodeSs58AccountId(request.sourceAddress)
            dev.satra.wallet.data.sync.accountchain.PolkadotStorage.decodeSs58AccountId(request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }

        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val providers = if (isNative) {
            config.providers.filter { provider -> provider.name.startsWith("polkadot-") && !provider.name.contains("asset-hub") }
        } else {
            config.providers.filter { provider -> provider.name.contains("asset-hub") }
        }.ifEmpty { config.providers }
        val client = PolkadotSendClient()
        var lastError: Throwable? = null
        providers.forEach { provider ->
            try {
                val chainBalance = if (assetHubAssetId == null) {
                    client.nativeBalance(provider, request.sourceAddress)
                } else {
                    client.assetBalance(provider, request.sourceAddress, assetHubAssetId.toLong())
                }
                if (chainBalance < amountRaw) {
                    throw SatraSendException.InsufficientBalance()
                }
                val context = client.context(provider)
                val nonce = client.nonce(provider, request.sourceAddress)
                val signed = PolkadotTransactionSigner.sign(
                    PolkadotSigningRequest(
                        sourceAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        amountRaw = amountRaw,
                        assetHubAssetId = assetHubAssetId,
                        nonce = nonce,
                        context = context,
                        privateKeyHex = request.privateKeyHex,
                    ),
                )
                val feeRaw = client.fee(provider, signed.extrinsicHex)
                val feeAssetId = SupportedAssetCatalog.assets
                    .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
                    ?.assetId
                val nativeBalance = if (assetHubAssetId == null) {
                    chainBalance
                } else {
                    client.nativeBalance(provider, request.sourceAddress)
                }
                if (nativeBalance < feeRaw.add(if (assetHubAssetId == null) amountRaw else BigInteger.ZERO)) {
                    throw SatraSendException.InsufficientBalance()
                }
                val txHash = client.broadcast(provider, signed.extrinsicHex)
                return SatraBroadcastResult(
                    transactionHash = txHash,
                    rawTransaction = signed.extrinsicHex,
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = feeRaw,
                    feeDecimal = EvmAbi.rawToDecimalString(feeRaw, network.nativeDecimals),
                    feeAssetId = feeAssetId,
                    nonce = nonce,
                    timestampMillis = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        lastError?.let { error ->
            if (error is SatraSendException) throw error
            throw SatraSendException.BroadcastFailed(error)
        }
        throw SatraSendException.BroadcastFailed(IllegalStateException("No Polkadot provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastRipple(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val destinationTag = normalizedRippleDestinationTag(request.memo)
        val normalizedMemo = destinationTag?.toString()
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        val issuedAmount = if (isNative) {
            null
        } else {
            try {
                RippleTransactionSigner.issuedAmount(
                    contractAddress = request.contractAddress
                        ?: throw SatraSendException.UnsupportedNetwork(request.networkId),
                    amountRaw = amountRaw,
                    decimals = request.decimals,
                )
            } catch (error: Throwable) {
                throw SatraSendException.UnsupportedNetwork(request.networkId)
            }
        }
        try {
            RippleTransactionSigner.validateAddress(request.sourceAddress)
            RippleTransactionSigner.validateAddress(request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val client = RippleSendClient()
        var lastError: Throwable? = null
        config.providers.forEach { provider ->
            try {
                val account = client.accountInfo(provider, request.sourceAddress)
                val ledger = client.currentLedger(provider).takeIf { it > 0L } ?: account.ledgerIndex
                val feeDrops = client.feeDrops(provider).coerceAtLeast(BigInteger.TEN)
                val nativeBalance = if (isNative) availableRaw else account.balanceDrops
                if (nativeBalance < feeDrops.add(if (isNative) amountRaw else BigInteger.ZERO)) {
                    throw SatraSendException.InsufficientBalance()
                }
                val signed = RippleTransactionSigner.sign(
                    RippleSigningRequest(
                        sourceAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        amountDrops = amountRaw,
                        issuedAmount = issuedAmount,
                        feeDrops = feeDrops,
                        sequence = account.sequence,
                        lastLedgerSequence = ledger + XRPL_LAST_LEDGER_OFFSET,
                        destinationTag = destinationTag,
                        privateKeyHex = request.privateKeyHex,
                    ),
                )
                val txHash = client.submit(provider, signed.transactionBlobHex)
                return SatraBroadcastResult(
                    transactionHash = txHash.ifBlank { signed.transactionHash },
                    rawTransaction = signed.transactionBlobHex,
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = signed.feeDrops,
                    feeDecimal = EvmAbi.rawToDecimalString(signed.feeDrops, network.nativeDecimals),
                    feeAssetId = SupportedAssetCatalog.assets
                        .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
                        ?.assetId,
                    nonce = BigInteger.valueOf(account.sequence),
                    timestampMillis = System.currentTimeMillis(),
                    memo = normalizedMemo,
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No XRPL provider broadcast succeeded."))
    }

    private fun normalizedRippleDestinationTag(memo: String?): Long? {
        val value = memo?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (!value.all(Char::isDigit)) {
            throw SatraSendException.InvalidMemo()
        }
        val tag = value.toLongOrNull() ?: throw SatraSendException.InvalidMemo()
        if (tag !in 0L..XRPL_DESTINATION_TAG_MAX) {
            throw SatraSendException.InvalidMemo()
        }
        return tag
    }

    private fun normalizedStellarMemo(memo: String?): String? {
        val value = memo?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (value.toByteArray(Charsets.UTF_8).size > STELLAR_MEMO_TEXT_MAX_BYTES) {
            throw SatraSendException.InvalidMemo()
        }
        return value
    }

    private suspend fun signAndBroadcastSui(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        if (request.assetType.uppercase(Locale.US) != "NATIVE") {
            throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        val gasBudget = BigInteger.valueOf(DEFAULT_SUI_GAS_BUDGET_MIST)
        if (availableRaw < amountRaw.add(gasBudget)) {
            throw SatraSendException.InsufficientBalance()
        }
        if (!request.sourceAddress.isSuiAddress() || !request.recipientAddress.isSuiAddress()) {
            throw SatraSendException.InvalidRecipient()
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val client = SuiSendClient()
        var lastError: Throwable? = null
        config.providers.forEach { provider ->
            try {
                val coins = client.coins(provider, request.sourceAddress, SUI_COIN_TYPE)
                    .sortedByDescending { coin -> coin.balance }
                val selected = mutableListOf<String>()
                var total = BigInteger.ZERO
                for (coin in coins) {
                    selected += coin.objectId
                    total = total.add(coin.balance)
                    if (total >= amountRaw.add(gasBudget)) break
                }
                if (total < amountRaw.add(gasBudget)) {
                    throw SatraSendException.InsufficientBalance()
                }
                val txBytes = client.buildNativePay(
                    provider = provider,
                    signer = request.sourceAddress,
                    inputCoins = selected,
                    recipient = request.recipientAddress,
                    amountMist = amountRaw,
                    gasBudgetMist = gasBudget,
                )
                val signature = client.signatureBase64(txBytes, request.privateKeyHex, request.sourceAddress)
                val digest = client.executeTransaction(provider, txBytes, signature)
                return SatraBroadcastResult(
                    transactionHash = digest,
                    rawTransaction = "$txBytes:$signature",
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = gasBudget,
                    feeDecimal = EvmAbi.rawToDecimalString(gasBudget, network.nativeDecimals),
                    feeAssetId = request.assetId,
                    nonce = BigInteger.ZERO,
                    timestampMillis = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No Sui provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastTon(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        if (!isNative && request.contractAddress.isNullOrBlank()) {
            throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        try {
            TonTransactionSigner.validateAddress(request.sourceAddress)
            TonTransactionSigner.validateAddress(request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }

        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val providers = config.providers
            .filter { provider -> provider.name.contains("tonapi", ignoreCase = true) }
        if (providers.isEmpty()) {
            throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        val client = TonSendClient()
        var lastError: Throwable? = null
        providers.forEach { provider ->
            try {
                val nativeBalance = client.nativeBalance(provider, request.sourceAddress)
                val jetton = if (isNative) {
                    null
                } else {
                    client.jettonBalance(
                        provider = provider,
                        ownerAddress = request.sourceAddress,
                        jettonMasterAddress = checkNotNull(request.contractAddress),
                    )
                }
                if (jetton != null && jetton.balanceRaw < amountRaw) {
                    throw SatraSendException.InsufficientBalance()
                }
                val seqno = client.seqno(provider, request.sourceAddress)
                val attachedTonRaw = if (isNative) amountRaw else TON_JETTON_TRANSFER_ATTACHED_NANOTON
                val signed = try {
                    TonTransactionSigner.sign(
                        TonSigningRequest(
                            sourceAddress = request.sourceAddress,
                            recipientAddress = request.recipientAddress,
                            amountRaw = amountRaw,
                            seqno = seqno,
                            attachedTonRaw = attachedTonRaw,
                            jettonWalletAddress = jetton?.walletAddress,
                            privateKeyHex = request.privateKeyHex,
                        ),
                    )
                } catch (error: Throwable) {
                    throw SatraSendException.MissingSigningKey()
                }
                val emulation = client.emulateFee(provider, signed.bocBase64)
                val feeRaw = emulation?.feeRaw
                    ?.takeIf { fee -> fee > BigInteger.ZERO }
                    ?: DEFAULT_TON_FEE_NANOTON
                val requiredNativeRaw = if (isNative) {
                    emulation?.nativeLossRaw
                        ?.takeIf { loss -> loss >= amountRaw }
                        ?: amountRaw.add(feeRaw)
                } else {
                    emulation?.nativeLossRaw
                        ?.takeIf { loss -> loss >= attachedTonRaw }
                        ?: attachedTonRaw.add(feeRaw)
                }
                if (nativeBalance < requiredNativeRaw) {
                    throw SatraSendException.InsufficientBalance()
                }
                val txHash = client.broadcast(provider, signed.bocBase64, signed.messageHash)
                val feeAssetId = SupportedAssetCatalog.assets
                    .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
                    ?.assetId
                return SatraBroadcastResult(
                    transactionHash = txHash,
                    rawTransaction = signed.bocBase64,
                    providerName = provider.name,
                    fromAddress = request.sourceAddress,
                    toAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
                    feeRaw = feeRaw,
                    feeDecimal = EvmAbi.rawToDecimalString(feeRaw, network.nativeDecimals),
                    feeAssetId = feeAssetId,
                    nonce = BigInteger.valueOf(seqno.toLong()),
                    timestampMillis = System.currentTimeMillis(),
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        lastError?.let { error ->
            if (error is SatraSendException) throw error
            throw SatraSendException.BroadcastFailed(error)
        }
        throw SatraSendException.BroadcastFailed(IllegalStateException("No TON provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastSolana(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        try {
            SolanaTransactionSigner.publicKeyBytes(request.sourceAddress)
            SolanaTransactionSigner.publicKeyBytes(request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }
        val client = SolanaJsonRpcClient(SolanaProviderRegistry.requireConfig(request.networkId))
        val tokenAccounts = SolanaTransactionSigner.parseTokenAccounts(request.walletAssetMetadataJson)
        val contractAddress = if (isNative) null else request.contractAddress
            ?: throw SatraSendException.UnsupportedNetwork(request.networkId)
        val recipientTokenAccount = if (contractAddress == null) {
            null
        } else {
            val senderTokenAccount = tokenAccounts
                .filter { account -> account.owner == request.sourceAddress && account.mint == contractAddress }
                .filter { account -> account.amountRaw.toBigIntegerOrZero() >= amountRaw }
                .maxByOrNull { account -> account.amountRaw.toBigIntegerOrZero() }
                ?: throw SatraSendException.InsufficientBalance()
            SolanaTransactionSigner.associatedTokenAddress(
                ownerAddress = request.recipientAddress,
                mintAddress = contractAddress,
                tokenProgramId = senderTokenAccount.programId,
            )
        }
        val createRecipientTokenAccount = if (recipientTokenAccount == null) {
            false
        } else {
            try {
                client.parsedAccountInfo(recipientTokenAccount).value == null
            } catch (error: Throwable) {
                throw SatraSendException.BroadcastFailed(error)
            }
        }
        val blockhash = try {
            client.latestBlockhash().value
        } catch (error: Throwable) {
            throw SatraSendException.BroadcastFailed(error)
        }
        val signed = try {
            SolanaTransactionSigner.sign(
                SolanaSigningRequest(
                    sourceAddress = request.sourceAddress,
                    recipientAddress = request.recipientAddress,
                    amountRaw = amountRaw,
                    decimals = request.decimals,
                    contractAddress = contractAddress,
                    recentBlockhash = blockhash,
                    privateKeyHex = request.privateKeyHex,
                    senderTokenAccounts = tokenAccounts,
                    recipientTokenAccount = recipientTokenAccount,
                    createRecipientTokenAccount = createRecipientTokenAccount,
                ),
            )
        } catch (error: IllegalArgumentException) {
            throw SatraSendException.InvalidAmount(error)
        } catch (error: Throwable) {
            throw SatraSendException.MissingSigningKey()
        }
        if (isNative && availableRaw < amountRaw.add(BigInteger.valueOf(signed.feeLamports))) {
            throw SatraSendException.InsufficientBalance()
        }
        val broadcast = try {
            client.sendTransaction(signed.transactionBase64)
        } catch (error: Throwable) {
            throw SatraSendException.BroadcastFailed(error)
        }
        val feeAssetId = SupportedAssetCatalog.assets
            .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
            ?.assetId
        return SatraBroadcastResult(
            transactionHash = broadcast.value.ifBlank { signed.signature },
            rawTransaction = signed.transactionBase64,
            providerName = broadcast.provider.name,
            fromAddress = request.sourceAddress,
            toAddress = request.recipientAddress,
            amountRaw = amountRaw,
            amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
            feeRaw = BigInteger.valueOf(signed.feeLamports),
            feeDecimal = EvmAbi.rawToDecimalString(BigInteger.valueOf(signed.feeLamports), network.nativeDecimals),
            feeAssetId = feeAssetId,
            nonce = BigInteger.ZERO,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun signAndBroadcastTron(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        try {
            TronSendClient.validateTronAddress(request.sourceAddress)
            TronSendClient.validateTronAddress(request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }
        val config = AccountChainProviderRegistry.requireConfig(request.networkId)
        val client = TronSendClient()
        val isNative = request.assetType.uppercase(Locale.US) == "NATIVE"
        val feeRaw = if (isNative) {
            BigInteger.ZERO
        } else {
            BigInteger.valueOf(TronSendClient.DEFAULT_TRC20_FEE_LIMIT_SUN)
        }
        val broadcast = signAndBroadcastWithTronProviders(
            client = client,
            providers = config.providers,
            request = request,
            amountRaw = amountRaw,
            isNative = isNative,
        )
        val feeAssetId = SupportedAssetCatalog.assets
            .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
            ?.assetId
        return SatraBroadcastResult(
            transactionHash = broadcast.transactionHash,
            rawTransaction = broadcast.rawTransaction,
            providerName = broadcast.provider.name,
            fromAddress = request.sourceAddress,
            toAddress = request.recipientAddress,
            amountRaw = amountRaw,
            amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
            feeRaw = feeRaw,
            feeDecimal = EvmAbi.rawToDecimalString(feeRaw, network.nativeDecimals),
            feeAssetId = feeAssetId,
            nonce = BigInteger.ZERO,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun signAndBroadcastWithTronProviders(
        client: TronSendClient,
        providers: List<AccountChainProvider>,
        request: SatraSendRequest,
        amountRaw: BigInteger,
        isNative: Boolean,
    ): TronBroadcast {
        var lastError: Throwable? = null
        providers.forEach { provider ->
            try {
                val unsigned = if (isNative) {
                    client.createNativeTransfer(
                        provider = provider,
                        ownerAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        amountSun = amountRaw,
                    )
                } else {
                    val contractAddress = request.contractAddress
                        ?: throw SatraSendException.UnsupportedNetwork(request.networkId)
                    client.createTrc20Transfer(
                        provider = provider,
                        ownerAddress = request.sourceAddress,
                        recipientAddress = request.recipientAddress,
                        contractAddress = contractAddress,
                        amountRaw = amountRaw,
                    )
                }
                val signed = client.signTransaction(unsigned, request.privateKeyHex)
                val txHash = client.broadcast(provider, signed)
                return TronBroadcast(
                    transactionHash = txHash,
                    rawTransaction = signed.signedTransactionJson.toString(),
                    provider = provider,
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No TRON provider broadcast succeeded."))
    }

    private suspend fun signAndBroadcastUtxo(
        request: SatraSendRequest,
        network: SupportedNetwork,
    ): SatraBroadcastResult {
        if (request.assetType.uppercase(Locale.US) != "NATIVE") {
            throw SatraSendException.UnsupportedNetwork(request.networkId)
        }
        val config = UtxoElectrumProviderRegistry.requireConfig(request.networkId)
        val client = UtxoElectrumClient()
        val amountRaw = decimalToRaw(request.amountDecimal, request.decimals)
        val availableRaw = request.balanceRaw.toBigIntegerOrZero()
        if (availableRaw < amountRaw) {
            throw SatraSendException.InsufficientBalance()
        }
        try {
            UtxoScript.scriptPubKey(request.networkId, request.recipientAddress)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidRecipient(error)
        }

        val utxos = UtxoTransactionSigner.parseUtxos(request.walletAssetMetadataJson)
        val keysByAddress = request.privateKeysHexByAddress
            .ifEmpty { mapOf(request.sourceAddress to request.privateKeyHex) }
        val feeRate = estimateUtxoFeeRateSatsPerVByte(client, config.providers, request.networkId)
        val signed = try {
            UtxoTransactionSigner.sign(
                UtxoSigningRequest(
                    networkId = request.networkId,
                    sourceAddress = request.sourceAddress,
                    recipientAddress = request.recipientAddress,
                    changeAddress = request.sourceAddress,
                    amountRaw = amountRaw,
                    feeRateSatsPerVByte = feeRate,
                    utxos = utxos,
                    privateKeysHexByAddress = keysByAddress,
                ),
            )
        } catch (error: IllegalArgumentException) {
            if (error.message?.contains("Insufficient", ignoreCase = true) == true) {
                throw SatraSendException.InsufficientBalance()
            }
            throw SatraSendException.MissingSigningKey()
        } catch (error: Throwable) {
            throw SatraSendException.BroadcastFailed(error)
        }

        val broadcast = broadcastUtxoTransaction(client, config.providers, signed.rawTransactionHex)
        val feeAssetId = SupportedAssetCatalog.assets
            .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
            ?.assetId
        return SatraBroadcastResult(
            transactionHash = broadcast.transactionHash.ifBlank { signed.transactionHash },
            rawTransaction = signed.rawTransactionHex,
            providerName = broadcast.provider.name,
            fromAddress = request.sourceAddress,
            toAddress = request.recipientAddress,
            amountRaw = amountRaw,
            amountDecimal = EvmAbi.rawToDecimalString(amountRaw, request.decimals),
            feeRaw = BigInteger.valueOf(signed.feeSats),
            feeDecimal = EvmAbi.rawToDecimalString(BigInteger.valueOf(signed.feeSats), network.nativeDecimals),
            feeAssetId = feeAssetId,
            nonce = BigInteger.ZERO,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun estimateUtxoFeeRateSatsPerVByte(
        client: UtxoElectrumClient,
        providers: List<UtxoElectrumProvider>,
        networkId: String,
    ): Double {
        providers.forEach { provider ->
            val estimate = runCatching { client.estimateFeePerKilobyte(provider, targetBlocks = 3) }.getOrNull()
            if (estimate != null && estimate > 0.0) {
                return (estimate * SATOSHIS_PER_COIN / BYTES_PER_KILOBYTE)
                    .coerceAtLeast(defaultUtxoFeeRate(networkId))
            }
        }
        return defaultUtxoFeeRate(networkId)
    }

    private suspend fun broadcastUtxoTransaction(
        client: UtxoElectrumClient,
        providers: List<UtxoElectrumProvider>,
        rawTransactionHex: String,
    ): UtxoBroadcast {
        var lastError: Throwable? = null
        providers.forEach { provider ->
            try {
                return UtxoBroadcast(
                    transactionHash = client.broadcastTransaction(provider, rawTransactionHex),
                    provider = provider,
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw SatraSendException.BroadcastFailed(lastError ?: IllegalStateException("No Electrum provider broadcast succeeded."))
    }

    private fun decimalToRaw(
        amountDecimal: String,
        decimals: Int,
    ): BigInteger {
        val amount = try {
            BigDecimal(amountDecimal.trim())
        } catch (error: Throwable) {
            throw SatraSendException.InvalidAmount(error)
        }
        if (amount <= BigDecimal.ZERO) {
            throw SatraSendException.InvalidAmount()
        }
        val scaled = try {
            amount.setScale(decimals, RoundingMode.UNNECESSARY)
        } catch (error: Throwable) {
            throw SatraSendException.InvalidAmount(error)
        }
        return scaled.movePointRight(decimals).toBigIntegerExact()
    }

    private fun BigInteger.withGasBuffer(): BigInteger {
        val buffered = multiply(BigInteger.valueOf(GAS_BUFFER_NUMERATOR))
            .add(BigInteger.valueOf(GAS_BUFFER_DENOMINATOR - 1L))
            .divide(BigInteger.valueOf(GAS_BUFFER_DENOMINATOR))
        return buffered.max(BigInteger.valueOf(MIN_EVM_GAS_LIMIT))
    }

    private fun String.toBigIntegerOrZero(): BigInteger =
        runCatching { BigInteger(this) }.getOrDefault(BigInteger.ZERO)

    private fun String.hexToBytes(): ByteArray {
        val normalized = removePrefix("0x").removePrefix("0X")
        require(normalized.length % 2 == 0 && normalized.all(Char::isHexDigit)) {
            "Invalid hex data."
        }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        fun canSignAndBroadcast(
            asset: SupportedAsset,
            network: SupportedNetwork,
        ): Boolean =
            when (network.family) {
                "evm",
                "utxo",
                "solana",
                "tron",
                "aptos",
                "near",
                "cosmos",
                "polkadot",
                "ripple",
                "ton",
                -> true
                "stellar",
                "sui",
                -> asset.assetType.uppercase(Locale.US) == "NATIVE"
                else -> false
            }

        private const val GAS_BUFFER_NUMERATOR = 120L
        private const val GAS_BUFFER_DENOMINATOR = 100L
        private const val MIN_EVM_GAS_LIMIT = 21_000L
        private const val SATOSHIS_PER_COIN = 100_000_000.0
        private const val BYTES_PER_KILOBYTE = 1_000.0
        private const val APTOS_EXPIRATION_SECONDS = 600L
        private const val DEFAULT_APTOS_MAX_GAS_AMOUNT = 100_000UL
        private const val STELLAR_BASE_FEE_STROOPS = 100
        private const val STELLAR_MEMO_TEXT_MAX_BYTES = 28
        private const val DEFAULT_SUI_GAS_BUDGET_MIST = 5_000_000L
        private const val SUI_COIN_TYPE = "0x2::sui::SUI"
        private const val DEFAULT_KAVA_GAS_LIMIT = 200_000L
        private const val DEFAULT_KAVA_FEE_UKAVA = 20_000L
        private const val XRPL_LAST_LEDGER_OFFSET = 20L
        private const val XRPL_DESTINATION_TAG_MAX = 0xffffffffL
        private val DEFAULT_TON_FEE_NANOTON = BigInteger.valueOf(15_000_000L)
        private val TON_JETTON_TRANSFER_ATTACHED_NANOTON = BigInteger.valueOf(50_000_000L)
    }
}

private data class UtxoBroadcast(
    val transactionHash: String,
    val provider: UtxoElectrumProvider,
)

private data class TronBroadcast(
    val transactionHash: String,
    val rawTransaction: String,
    val provider: AccountChainProvider,
)

private fun defaultUtxoFeeRate(networkId: String): Double =
    when (networkId) {
        "bitcoin" -> 5.0
        "dogecoin" -> 2.0
        else -> 2.0
    }

private fun String.isSuiAddress(): Boolean {
    val normalized = removePrefix("0x").removePrefix("0X")
    return normalized.length == 64 && normalized.all(Char::isHexDigit)
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
