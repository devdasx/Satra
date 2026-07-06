package dev.satra.wallet.data.send.ton

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import org.ton.block.Coins
import org.ton.block.CurrencyCollection
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.kotlin.crypto.PrivateKeyEd25519
import java.math.BigInteger
import java.util.Base64

internal object TonTransactionSigner {
    fun sign(request: TonSigningRequest): TonSignedTransaction {
        val privateKey = PrivateKeyEd25519(SatraSigningCrypto.parseEd25519PrivateKey(request.privateKeyHex))
        val derivedAddress = WalletV4R2Contract.address(privateKey)
        val sourceAddress = MsgAddressInt.parse(request.sourceAddress).toAddrStd()
        require(sourceAddress.toCanonicalTonAddress() == derivedAddress.toCanonicalTonAddress()) {
            "TON private key does not match the source address."
        }

        val destination = if (request.jettonWalletAddress == null) {
            MsgAddressInt.parse(request.recipientAddress)
        } else {
            MsgAddressInt.parse(request.jettonWalletAddress)
        }
        val messageBody = if (request.jettonWalletAddress == null) {
            Cell.empty()
        } else {
            jettonTransferBody(
                amountRaw = request.amountRaw,
                recipientAddress = MsgAddressInt.parse(request.recipientAddress),
                responseAddress = sourceAddress,
            )
        }
        val transfer = TonSdkInterop.walletTransfer(
            destination,
            request.jettonWalletAddress != null,
            CurrencyCollection(Coins(TonSdkInterop.bigInt(request.attachedTonRaw))),
            SEND_MODE_PAY_FEES_SEPARATELY,
            MessageData.raw(messageBody),
        )
        val walletData = WalletV4R2Contract.Data(
            request.seqno,
            WALLET_V4R2_SUBWALLET_ID,
            privateKey.publicKey(),
        )
        val stateInit = WalletV4R2Contract.stateInit(walletData).load()
        val validUntil = (System.currentTimeMillis() / 1_000L + VALID_UNTIL_SECONDS).toInt()
        val externalMessage = WalletV4R2Contract.transferMessage(
            derivedAddress,
            if (request.seqno == 0) stateInit else null,
            privateKey,
            WALLET_V4R2_SUBWALLET_ID,
            request.seqno,
            validUntil,
            transfer,
        )
        val messageCell = TonSdkInterop.messageCell(externalMessage)
        val boc = BagOfCells.of(messageCell).toByteArray()
        return TonSignedTransaction(
            bocBase64 = Base64.getEncoder().encodeToString(boc),
            messageHash = messageCell.hash().toByteArray().toHex(),
            validUntil = validUntil,
        )
    }

    fun validateAddress(address: String) {
        MsgAddressInt.parse(address)
    }

    private fun jettonTransferBody(
        amountRaw: BigInteger,
        recipientAddress: MsgAddressInt,
        responseAddress: MsgAddressInt,
    ): Cell {
        val builder = CellBuilder.beginCell()
        builder.storeUInt(TonSdkInterop.bigInt(JETTON_TRANSFER_OPERATION), 32)
        builder.storeUInt(0, 64)
        Coins.tlbCodec().storeTlb(builder, Coins(TonSdkInterop.bigInt(amountRaw)))
        MsgAddress.storeTlb(builder, recipientAddress)
        MsgAddress.storeTlb(builder, responseAddress)
        builder.storeBoolean(false)
        Coins.tlbCodec().storeTlb(builder, Coins(TonSdkInterop.bigInt(JETTON_FORWARD_TON_RAW)))
        builder.storeBoolean(false)
        return builder.endCell()
    }

    private fun org.ton.block.AddrStd.toCanonicalTonAddress(): String =
        toString(userFriendly = true, urlSafe = true, testOnly = false, bounceable = false)

    private fun Cell.hash(): org.ton.bitstring.BitString =
        hash(0)

    private fun org.ton.bitstring.BitString.toByteArray(): ByteArray =
        toByteArray(false)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private val JETTON_TRANSFER_OPERATION = BigInteger("0f8a7ea5", 16)
    private val JETTON_FORWARD_TON_RAW = BigInteger.ONE
    private const val WALLET_V4R2_SUBWALLET_ID = 698_983_191
    private const val VALID_UNTIL_SECONDS = 600L
    private const val SEND_MODE_PAY_FEES_SEPARATELY = 3
}

internal data class TonSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountRaw: BigInteger,
    val seqno: Int,
    val attachedTonRaw: BigInteger,
    val jettonWalletAddress: String?,
    val privateKeyHex: String,
)

internal data class TonSignedTransaction(
    val bocBase64: String,
    val messageHash: String,
    val validUntil: Int,
)
