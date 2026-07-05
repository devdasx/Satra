package dev.satra.wallet.data.send.cosmos

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toFixedBytes
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

internal object CosmosDirectSigner {
    fun sign(request: CosmosSigningRequest): CosmosSignedTransaction {
        val privateKey = SatraSigningCrypto.parseSecp256k1PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.secp256k1PublicKey(privateKey, compressed = true)
        val send = protoMessage(
            fieldLen(1, protoString(request.fromAddress)),
            fieldLen(2, protoString(request.toAddress)),
            fieldLen(3, coin(request.denom, request.amountRaw.toString())),
        )
        val messageAny = any("/cosmos.bank.v1beta1.MsgSend", send)
        val bodyBytes = protoMessage(fieldLen(1, messageAny))
        val pubKeyAny = any(
            "/cosmos.crypto.secp256k1.PubKey",
            protoMessage(fieldLen(1, protoBytes(publicKey))),
        )
        val modeInfo = protoMessage(
            fieldLen(1, protoMessage(fieldVarint(1, SIGN_MODE_DIRECT.toLong()))),
        )
        val signerInfo = protoMessage(
            fieldLen(1, pubKeyAny),
            fieldLen(2, modeInfo),
            fieldVarint(3, request.sequence),
        )
        val fee = protoMessage(
            fieldLen(1, coin(KAVA_NATIVE_DENOM, request.feeUkava.toString())),
            fieldVarint(2, request.gasLimit),
        )
        val authInfoBytes = protoMessage(
            fieldLen(1, signerInfo),
            fieldLen(2, fee),
        )
        val signDoc = protoMessage(
            fieldLen(1, protoBytes(bodyBytes)),
            fieldLen(2, protoBytes(authInfoBytes)),
            fieldLen(3, protoString(KAVA_CHAIN_ID)),
            fieldVarint(4, request.accountNumber),
        )
        val digest = SatraSigningCrypto.sha256(signDoc)
        val signature = SatraSigningCrypto.secp256k1SignDigest(digest, privateKey)
        val signatureBytes = signature.r.toFixedBytes(32) + signature.s.toFixedBytes(32)
        val txRaw = protoMessage(
            fieldLen(1, protoBytes(bodyBytes)),
            fieldLen(2, protoBytes(authInfoBytes)),
            fieldLen(3, protoBytes(signatureBytes)),
        )
        return CosmosSignedTransaction(
            txRawBase64 = Base64.getEncoder().encodeToString(txRaw),
            feeUkava = request.feeUkava,
        )
    }

    private fun any(typeUrl: String, value: ByteArray): ByteArray =
        protoMessage(
            fieldLen(1, protoString(typeUrl)),
            fieldLen(2, protoBytes(value)),
        )

    private fun coin(denom: String, amount: String): ByteArray =
        protoMessage(
            fieldLen(1, protoString(denom)),
            fieldLen(2, protoString(amount)),
        )

    private fun protoMessage(vararg fields: ProtoField): ByteArray =
        ByteArrayOutputStream().use { out ->
            fields.forEach { field ->
                out.write(protoVarint(((field.fieldNumber shl 3) or field.wireType).toLong()))
                if (field.wireType == WIRE_LEN) {
                    out.write(protoVarint(field.value.size.toLong()))
                }
                out.write(field.value)
            }
            out.toByteArray()
        }

    private fun fieldLen(fieldNumber: Int, value: ByteArray): ProtoField =
        ProtoField(fieldNumber, WIRE_LEN, value)

    private fun fieldVarint(fieldNumber: Int, value: Long): ProtoField =
        ProtoField(fieldNumber, WIRE_VARINT, protoVarint(value))

    private fun protoBytes(value: ByteArray): ByteArray = value

    private fun protoString(value: String): ByteArray =
        value.toByteArray(Charsets.UTF_8)

    private fun protoVarint(value: Long): ByteArray {
        require(value >= 0L)
        val out = ByteArrayOutputStream()
        var remaining = value
        while (true) {
            if ((remaining and 0x7f.inv().toLong()) == 0L) {
                out.write(remaining.toInt())
                return out.toByteArray()
            }
            out.write(((remaining and 0x7f) or 0x80).toInt())
            remaining = remaining ushr 7
        }
    }

    private const val WIRE_LEN = 2
    private const val WIRE_VARINT = 0
    private const val SIGN_MODE_DIRECT = 1
    const val KAVA_CHAIN_ID = "kava_2222-10"
    const val KAVA_NATIVE_DENOM = "ukava"
}

private data class ProtoField(
    val fieldNumber: Int,
    val wireType: Int,
    val value: ByteArray,
)

internal data class CosmosSigningRequest(
    val fromAddress: String,
    val toAddress: String,
    val denom: String,
    val amountRaw: BigInteger,
    val accountNumber: Long,
    val sequence: Long,
    val gasLimit: Long,
    val feeUkava: BigInteger,
    val privateKeyHex: String,
)

internal data class CosmosSignedTransaction(
    val txRawBase64: String,
    val feeUkava: BigInteger,
)
