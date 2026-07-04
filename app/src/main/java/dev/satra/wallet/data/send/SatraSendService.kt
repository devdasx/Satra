package dev.satra.wallet.data.send

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.assets.SupportedNetwork
import dev.satra.wallet.data.send.evm.EvmLegacyTransaction
import dev.satra.wallet.data.send.evm.EvmLegacyTransactionSigner
import dev.satra.wallet.data.sync.evm.EvmAbi
import dev.satra.wallet.data.sync.evm.EvmJsonRpcClient
import dev.satra.wallet.data.sync.evm.EvmProviderRegistry
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
            "evm" -> signAndBroadcastEvm(request, network)
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

        val broadcast = try {
            client.sendRawTransaction(rawTransaction)
        } catch (error: Throwable) {
            throw SatraSendException.BroadcastFailed(error)
        }

        val feeAssetId = SupportedAssetCatalog.assets
            .firstOrNull { asset -> asset.networkId == request.networkId && asset.assetType == "NATIVE" }
            ?.assetId

        return SatraBroadcastResult(
            transactionHash = broadcast.value,
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

    private companion object {
        const val GAS_BUFFER_NUMERATOR = 120L
        const val GAS_BUFFER_DENOMINATOR = 100L
        const val MIN_EVM_GAS_LIMIT = 21_000L
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
