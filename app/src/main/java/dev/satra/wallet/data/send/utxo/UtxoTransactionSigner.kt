package dev.satra.wallet.data.send.utxo

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.hexToBytes
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toFixedBytes
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import dev.satra.wallet.data.sync.utxo.UtxoScript
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.math.ceil

internal object UtxoTransactionSigner {
    fun sign(
        request: UtxoSigningRequest,
    ): UtxoSignedTransaction {
        val amountSats = request.amountRaw.toLongExactOrNull()
            ?: error("Bitcoin-family amount is too large.")
        require(amountSats > 0L) { "Amount must be positive." }

        val keysByAddress = request.privateKeysHexByAddress.mapValues { (_, keyHex) ->
            val privateKey = SatraSigningCrypto.parseSecp256k1PrivateKey(keyHex)
            UtxoKey(
                privateKey = privateKey,
                publicKey = SatraSigningCrypto.secp256k1PublicKey(privateKey, compressed = true),
            )
        }
        require(keysByAddress.isNotEmpty()) { "No Bitcoin-family signing keys." }

        val spendable = request.utxos
            .filter { utxo -> utxo.valueSats > 0L && keysByAddress.containsKey(utxo.address) }
            .mapNotNull { utxo -> utxo.toSpendableInputOrNull(request.networkId, keysByAddress[utxo.address]!!) }
            .sortedWith(compareBy<UtxoSpendableInput> { it.confirmationRank }.thenByDescending { it.valueSats })
        require(spendable.isNotEmpty()) { "No spendable UTXOs for this wallet." }

        val recipientScript = UtxoScript.scriptPubKey(request.networkId, request.recipientAddress)
        val changeAddress = request.changeAddress.takeIf(String::isNotBlank) ?: spendable.first().address
        val changeScript = UtxoScript.scriptPubKey(request.networkId, changeAddress)
        val dustSats = dustThreshold(request.networkId)
        val feeRate = request.feeRateSatsPerVByte.coerceAtLeast(minFeeRate(request.networkId))

        var selected = emptyList<UtxoSpendableInput>()
        var feeSats = 0L
        var changeSats = 0L
        for (candidate in spendable) {
            selected = selected + candidate
            val inputTotal = selected.sumOf { it.valueSats }
            val outputsWithChange = listOf(
                TxOutput(amountSats, recipientScript),
                TxOutput(0L, changeScript),
            )
            val feeWithChange = ceil(estimateVirtualSize(selected, outputsWithChange) * feeRate).toLong()
            val changeWithFee = inputTotal - amountSats - feeWithChange
            if (changeWithFee >= dustSats) {
                feeSats = feeWithChange
                changeSats = changeWithFee
                break
            }
            val feeWithoutChange = ceil(estimateVirtualSize(selected, listOf(TxOutput(amountSats, recipientScript))) * feeRate).toLong()
            val remainder = inputTotal - amountSats - feeWithoutChange
            if (remainder >= 0L) {
                feeSats = feeWithoutChange + remainder
                changeSats = 0L
                break
            }
        }
        require(feeSats > 0L && selected.sumOf { it.valueSats } >= amountSats + feeSats) {
            "Insufficient balance."
        }

        val outputs = buildList {
            add(TxOutput(amountSats, recipientScript))
            if (changeSats >= dustSats) {
                add(TxOutput(changeSats, changeScript))
            }
        }
        val transaction = MutableUtxoTransaction(
            networkId = request.networkId,
            inputs = selected,
            outputs = outputs,
            lockTime = 0,
        )
        transaction.signAllInputs()

        val rawNoWitness = transaction.serialize(includeWitness = false)
        val raw = transaction.serialize(includeWitness = transaction.hasWitness)
        val txid = SatraSigningCrypto.doubleSha256(rawNoWitness).reversedArray().toHex()
        return UtxoSignedTransaction(
            transactionHash = txid,
            rawTransactionHex = raw.toHex(),
            amountSats = amountSats,
            feeSats = feeSats,
        )
    }

    fun parseUtxos(metadataJson: String): List<UtxoSigningUtxo> {
        val root = runCatching { JSONObject(metadataJson) }.getOrDefault(JSONObject())
        val items = root.optJSONArray("utxos") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val txHash = item.optString("transactionHash").takeIf(String::isNotBlank) ?: continue
                val outputIndex = item.optInt("outputIndex", -1).takeIf { it >= 0 } ?: continue
                val valueSats = item.optLong("valueSats", 0L).takeIf { it > 0L } ?: continue
                val address = item.optString("address").takeIf(String::isNotBlank) ?: continue
                add(
                    UtxoSigningUtxo(
                        transactionHash = txHash,
                        outputIndex = outputIndex,
                        valueSats = valueSats,
                        height = item.optLong("height", 0L),
                        address = address,
                    ),
                )
            }
        }
    }

    private fun UtxoSigningUtxo.toSpendableInputOrNull(
        networkId: String,
        key: UtxoKey,
    ): UtxoSpendableInput? {
        val scriptPubKey = runCatching { UtxoScript.scriptPubKey(networkId, address) }.getOrNull() ?: return null
        val scriptType = UtxoScriptType.fromScript(networkId, address, scriptPubKey) ?: return null
        val publicKeyHash = SatraSigningCrypto.hash160(key.publicKey)
        return UtxoSpendableInput(
            transactionHash = transactionHash,
            outputIndex = outputIndex,
            valueSats = valueSats,
            height = height,
            address = address,
            scriptPubKey = scriptPubKey,
            scriptType = scriptType,
            key = key,
            publicKeyHash = publicKeyHash,
        )
    }

    private fun MutableUtxoTransaction.signAllInputs() {
        inputs.forEachIndexed { inputIndex, input ->
            val digest = when {
                input.scriptType == UtxoScriptType.P2WPKH ||
                    input.scriptType == UtxoScriptType.P2SH_P2WPKH ||
                    networkId == "bitcoinCash" -> signatureHashBip143(inputIndex)
                else -> signatureHashLegacy(inputIndex)
            }
            val der = SatraSigningCrypto.derEncode(
                SatraSigningCrypto.secp256k1SignDigest(digest, input.key.privateKey),
            )
            val signatureWithHashType = der + byteArrayOf(input.sighashType(networkId).toByte())
            when (input.scriptType) {
                UtxoScriptType.P2PKH -> {
                    input.scriptSig = push(signatureWithHashType) + push(input.key.publicKey)
                    input.witness = emptyList()
                }
                UtxoScriptType.P2WPKH -> {
                    input.scriptSig = ByteArray(0)
                    input.witness = listOf(signatureWithHashType, input.key.publicKey)
                }
                UtxoScriptType.P2SH_P2WPKH -> {
                    val redeemScript = byteArrayOf(0x00, 0x14) + input.publicKeyHash
                    input.scriptSig = push(redeemScript)
                    input.witness = listOf(signatureWithHashType, input.key.publicKey)
                }
            }
        }
    }

    private fun MutableUtxoTransaction.signatureHashLegacy(inputIndex: Int): ByteArray {
        val input = inputs[inputIndex]
        val payload = ByteArrayOutputStream().use { out ->
            out.writeInt32LE(VERSION)
            out.writeVarInt(inputs.size.toLong())
            inputs.forEachIndexed { index, txInput ->
                out.write(txInput.outpointBytes())
                out.writeUInt32LE(txInput.outputIndex.toLong())
                val script = if (index == inputIndex) input.scriptCode else ByteArray(0)
                out.writeVarBytes(script)
                out.writeUInt32LE(txInput.sequence)
            }
            out.writeVarInt(outputs.size.toLong())
            outputs.forEach { output -> out.writeOutput(output) }
            out.writeUInt32LE(lockTime.toLong())
            out.writeUInt32LE(input.sighashType(networkId).toLong())
            out.toByteArray()
        }
        return SatraSigningCrypto.doubleSha256(payload)
    }

    private fun MutableUtxoTransaction.signatureHashBip143(inputIndex: Int): ByteArray {
        val input = inputs[inputIndex]
        val hashPrevouts = SatraSigningCrypto.doubleSha256(
            ByteArrayOutputStream().use { out ->
                inputs.forEach { txInput ->
                    out.write(txInput.outpointBytes())
                    out.writeUInt32LE(txInput.outputIndex.toLong())
                }
                out.toByteArray()
            },
        )
        val hashSequence = SatraSigningCrypto.doubleSha256(
            ByteArrayOutputStream().use { out ->
                inputs.forEach { txInput -> out.writeUInt32LE(txInput.sequence) }
                out.toByteArray()
            },
        )
        val hashOutputs = SatraSigningCrypto.doubleSha256(
            ByteArrayOutputStream().use { out ->
                outputs.forEach { output -> out.writeOutput(output) }
                out.toByteArray()
            },
        )
        val payload = ByteArrayOutputStream().use { out ->
            out.writeInt32LE(VERSION)
            out.write(hashPrevouts)
            out.write(hashSequence)
            out.write(input.outpointBytes())
            out.writeUInt32LE(input.outputIndex.toLong())
            out.writeVarBytes(input.scriptCode)
            out.writeInt64LE(input.valueSats)
            out.writeUInt32LE(input.sequence)
            out.write(hashOutputs)
            out.writeUInt32LE(lockTime.toLong())
            out.writeUInt32LE(input.sighashType(networkId).toLong())
            out.toByteArray()
        }
        return SatraSigningCrypto.doubleSha256(payload)
    }

    private fun MutableUtxoTransaction.serialize(includeWitness: Boolean): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.writeInt32LE(VERSION)
            if (includeWitness && hasWitness) {
                out.write(0x00)
                out.write(0x01)
            }
            out.writeVarInt(inputs.size.toLong())
            inputs.forEach { input ->
                out.write(input.outpointBytes())
                out.writeUInt32LE(input.outputIndex.toLong())
                out.writeVarBytes(input.scriptSig)
                out.writeUInt32LE(input.sequence)
            }
            out.writeVarInt(outputs.size.toLong())
            outputs.forEach { output -> out.writeOutput(output) }
            if (includeWitness && hasWitness) {
                inputs.forEach { input ->
                    out.writeVarInt(input.witness.size.toLong())
                    input.witness.forEach { item -> out.writeVarBytes(item) }
                }
            }
            out.writeUInt32LE(lockTime.toLong())
            out.toByteArray()
        }

    private val MutableUtxoTransaction.hasWitness: Boolean
        get() = inputs.any { input -> input.scriptType != UtxoScriptType.P2PKH }

    private val UtxoSpendableInput.scriptCode: ByteArray
        get() = when (scriptType) {
            UtxoScriptType.P2PKH -> scriptPubKey
            UtxoScriptType.P2WPKH,
            UtxoScriptType.P2SH_P2WPKH,
            -> p2pkhScript(publicKeyHash)
        }

    private fun UtxoSpendableInput.sighashType(networkId: String): Int =
        if (networkId == "bitcoinCash") SIGHASH_ALL or SIGHASH_FORKID else SIGHASH_ALL

    private fun UtxoSpendableInput.outpointBytes(): ByteArray =
        transactionHash.hexToBytes().reversedArray()

    private fun UtxoScriptType.Companion.fromScript(
        networkId: String,
        address: String,
        script: ByteArray,
    ): UtxoScriptType? =
        when {
            script.size == 22 && script[0] == 0x00.toByte() && script[1] == 0x14.toByte() -> UtxoScriptType.P2WPKH
            script.size == 23 && script[0] == 0xa9.toByte() && script[1] == 0x14.toByte() && script[22] == 0x87.toByte() ->
                if (networkId in setOf("bitcoin", "litecoin") && (address.startsWith("3") || address.startsWith("M"))) {
                    UtxoScriptType.P2SH_P2WPKH
                } else {
                    null
                }
            script.size == 25 &&
                script[0] == 0x76.toByte() &&
                script[1] == 0xa9.toByte() &&
                script[2] == 0x14.toByte() &&
                script[23] == 0x88.toByte() &&
                script[24] == 0xac.toByte() -> UtxoScriptType.P2PKH
            else -> null
        }

    private fun estimateVirtualSize(
        inputs: List<UtxoSpendableInput>,
        outputs: List<TxOutput>,
    ): Double {
        val base = 4.0 + varIntSize(inputs.size) + varIntSize(outputs.size) + 4.0
        val inputWeight = inputs.sumOf { input ->
            when (input.scriptType) {
                UtxoScriptType.P2PKH -> 148 * 4
                UtxoScriptType.P2WPKH -> 41 * 4 + 108
                UtxoScriptType.P2SH_P2WPKH -> 64 * 4 + 108
            }
        }
        val outputWeight = outputs.sumOf { output -> (8 + varIntSize(output.scriptPubKey.size) + output.scriptPubKey.size) * 4 }
        val witnessMarkerWeight = if (inputs.any { it.scriptType != UtxoScriptType.P2PKH }) 2 else 0
        return (base * 4.0 + inputWeight + outputWeight + witnessMarkerWeight) / 4.0
    }

    private fun p2pkhScript(hash160: ByteArray): ByteArray =
        byteArrayOf(0x76, 0xa9.toByte(), 0x14) + hash160 + byteArrayOf(0x88.toByte(), 0xac.toByte())

    private fun push(data: ByteArray): ByteArray =
        when {
            data.size < 0x4c -> byteArrayOf(data.size.toByte()) + data
            data.size <= 0xff -> byteArrayOf(0x4c, data.size.toByte()) + data
            else -> error("Push data is too large.")
        }

    private fun ByteArrayOutputStream.writeOutput(output: TxOutput) {
        writeInt64LE(output.valueSats)
        writeVarBytes(output.scriptPubKey)
    }

    private fun ByteArrayOutputStream.writeVarBytes(bytes: ByteArray) {
        writeVarInt(bytes.size.toLong())
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Long) {
        require(value >= 0L) { "VarInt cannot be negative." }
        when {
            value < 0xfdL -> write(value.toInt())
            value <= 0xffffL -> {
                write(0xfd)
                write((value and 0xff).toInt())
                write(((value ushr 8) and 0xff).toInt())
            }
            value <= 0xffffffffL -> {
                write(0xfe)
                writeUInt32LE(value)
            }
            else -> {
                write(0xff)
                writeInt64LE(value)
            }
        }
    }

    private fun ByteArrayOutputStream.writeInt32LE(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }

    private fun ByteArrayOutputStream.writeUInt32LE(value: Long) {
        require(value in 0L..0xffffffffL)
        write((value and 0xff).toInt())
        write(((value ushr 8) and 0xff).toInt())
        write(((value ushr 16) and 0xff).toInt())
        write(((value ushr 24) and 0xff).toInt())
    }

    private fun ByteArrayOutputStream.writeInt64LE(value: Long) {
        repeat(8) { shift ->
            write(((value ushr (8 * shift)) and 0xff).toInt())
        }
    }

    private fun varIntSize(value: Int): Int =
        when {
            value < 0xfd -> 1
            value <= 0xffff -> 3
            else -> 5
        }

    private fun BigInteger.toLongExactOrNull(): Long? =
        if (this < Long.MIN_VALUE.toBigInteger() || this > Long.MAX_VALUE.toBigInteger()) {
            null
        } else {
            toLong()
        }

    private fun dustThreshold(networkId: String): Long =
        when (networkId) {
            "dogecoin" -> 1_000_000L
            else -> 546L
        }

    private fun minFeeRate(networkId: String): Double =
        when (networkId) {
            "dogecoin" -> 1.0
            else -> 1.0
        }

    private const val VERSION = 2
    private const val SIGHASH_ALL = 0x01
    private const val SIGHASH_FORKID = 0x40
}

internal data class UtxoSigningRequest(
    val networkId: String,
    val sourceAddress: String,
    val recipientAddress: String,
    val changeAddress: String,
    val amountRaw: BigInteger,
    val feeRateSatsPerVByte: Double,
    val utxos: List<UtxoSigningUtxo>,
    val privateKeysHexByAddress: Map<String, String>,
)

internal data class UtxoSigningUtxo(
    val transactionHash: String,
    val outputIndex: Int,
    val valueSats: Long,
    val height: Long,
    val address: String,
)

internal data class UtxoSignedTransaction(
    val transactionHash: String,
    val rawTransactionHex: String,
    val amountSats: Long,
    val feeSats: Long,
)

private data class UtxoKey(
    val privateKey: BigInteger,
    val publicKey: ByteArray,
)

private data class UtxoSpendableInput(
    val transactionHash: String,
    val outputIndex: Int,
    val valueSats: Long,
    val height: Long,
    val address: String,
    val scriptPubKey: ByteArray,
    val scriptType: UtxoScriptType,
    val key: UtxoKey,
    val publicKeyHash: ByteArray,
    val sequence: Long = 0xffffffffL,
) {
    val confirmationRank: Int = if (height > 0L) 0 else 1
    var scriptSig: ByteArray = ByteArray(0)
    var witness: List<ByteArray> = emptyList()
}

private data class TxOutput(
    val valueSats: Long,
    val scriptPubKey: ByteArray,
)

private data class MutableUtxoTransaction(
    val networkId: String,
    val inputs: List<UtxoSpendableInput>,
    val outputs: List<TxOutput>,
    val lockTime: Int,
)

private enum class UtxoScriptType {
    P2PKH,
    P2WPKH,
    P2SH_P2WPKH,
    ;

    companion object
}
