package dev.satra.wallet.data.sync.utxo

import java.security.MessageDigest

object UtxoTransactionParser {
    fun parse(rawTransactionHex: String): UtxoTransaction {
        val raw = rawTransactionHex.hexToBytes()
        val reader = ByteReader(raw)
        val versionStart = reader.position
        reader.readBytes(4)
        val marker = reader.peekByteOrNull()
        val hasWitness = marker == 0.toByte() && reader.peekByteOrNull(offset = 1) == 1.toByte()
        if (hasWitness) {
            reader.readByte()
            reader.readByte()
        }

        val inputs = List(reader.readVarInt().toInt()) {
            val previousTransactionHash = reader.readBytes(32).reversedArray().toHex()
            val previousOutputIndex = reader.readUInt32().toInt()
            val scriptLength = reader.readVarInt().toInt()
            reader.readBytes(scriptLength)
            reader.readBytes(4)
            UtxoTransactionInput(
                previousTransactionHash = previousTransactionHash,
                previousOutputIndex = previousOutputIndex,
            )
        }

        val outputs = List(reader.readVarInt().toInt()) { index ->
            val valueSats = reader.readInt64()
            val scriptLength = reader.readVarInt().toInt()
            val scriptPubKey = reader.readBytes(scriptLength)
            UtxoTransactionOutput(
                index = index,
                valueSats = valueSats,
                scriptPubKeyHex = scriptPubKey.toHex(),
            )
        }

        if (hasWitness) {
            repeat(inputs.size) {
                repeat(reader.readVarInt().toInt()) {
                    val itemLength = reader.readVarInt().toInt()
                    reader.readBytes(itemLength)
                }
            }
        }

        reader.readBytes(4)
        val nonWitnessBytes = if (hasWitness) stripWitness(raw, versionStart) else raw
        val transactionHash = sha256(sha256(nonWitnessBytes)).reversedArray().toHex()
        return UtxoTransaction(
            transactionHash = transactionHash,
            inputs = inputs,
            outputs = outputs,
        )
    }

    private fun stripWitness(
        raw: ByteArray,
        versionStart: Int,
    ): ByteArray {
        val fullReader = ByteReader(raw)
        val version = fullReader.readBytes(4)
        fullReader.readByte()
        fullReader.readByte()
        val bodyStart = fullReader.position
        val inputsAndOutputsReader = ByteReader(raw.copyOfRange(bodyStart, raw.size))
        val inputCount = inputsAndOutputsReader.readVarInt().toInt()
        repeat(inputCount) {
            inputsAndOutputsReader.readBytes(32)
            inputsAndOutputsReader.readBytes(4)
            inputsAndOutputsReader.readBytes(inputsAndOutputsReader.readVarInt().toInt())
            inputsAndOutputsReader.readBytes(4)
        }
        val outputCount = inputsAndOutputsReader.readVarInt().toInt()
        repeat(outputCount) {
            inputsAndOutputsReader.readBytes(8)
            inputsAndOutputsReader.readBytes(inputsAndOutputsReader.readVarInt().toInt())
        }
        val inputsOutputsEnd = bodyStart + inputsAndOutputsReader.position
        val locktime = raw.copyOfRange(raw.size - 4, raw.size)
        return raw.copyOfRange(versionStart, versionStart + version.size) +
            raw.copyOfRange(bodyStart, inputsOutputsEnd) +
            locktime
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}

data class UtxoTransaction(
    val transactionHash: String,
    val inputs: List<UtxoTransactionInput>,
    val outputs: List<UtxoTransactionOutput>,
)

data class UtxoTransactionInput(
    val previousTransactionHash: String,
    val previousOutputIndex: Int,
)

data class UtxoTransactionOutput(
    val index: Int,
    val valueSats: Long,
    val scriptPubKeyHex: String,
)

private class ByteReader(
    private val bytes: ByteArray,
) {
    var position: Int = 0
        private set

    fun readByte(): Byte =
        bytes[position++]

    fun peekByteOrNull(offset: Int = 0): Byte? =
        bytes.getOrNull(position + offset)

    fun readBytes(count: Int): ByteArray {
        require(position + count <= bytes.size) { "Transaction ended unexpectedly." }
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun readUInt32(): Long {
        val data = readBytes(4)
        return data.foldIndexed(0L) { index, acc, byte ->
            acc or ((byte.toLong() and 0xffL) shl (8 * index))
        }
    }

    fun readInt64(): Long {
        val data = readBytes(8)
        return data.foldIndexed(0L) { index, acc, byte ->
            acc or ((byte.toLong() and 0xffL) shl (8 * index))
        }
    }

    fun readVarInt(): Long {
        val first = readByte().toInt() and 0xff
        return when (first) {
            in 0x00..0xfc -> first.toLong()
            0xfd -> readBytes(2).foldIndexed(0L) { index, acc, byte ->
                acc or ((byte.toLong() and 0xffL) shl (8 * index))
            }
            0xfe -> readUInt32()
            else -> readInt64()
        }
    }
}

private fun String.hexToBytes(): ByteArray {
    val normalized = removePrefix("0x").removePrefix("0X")
    require(normalized.length % 2 == 0) { "Hex string must have an even length." }
    return normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
