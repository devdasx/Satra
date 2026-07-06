package dev.satra.wallet.data.send.polkadot

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import io.novasama.substrate_sdk_android.encrypt.EncryptionType
import io.novasama.substrate_sdk_android.encrypt.MultiChainEncryption
import io.novasama.substrate_sdk_android.encrypt.keypair.BaseKeypair
import io.novasama.substrate_sdk_android.runtime.definitions.types.composite.DictEnum
import io.novasama.substrate_sdk_android.runtime.definitions.types.generics.Era
import io.novasama.substrate_sdk_android.runtime.extrinsic.BatchMode
import io.novasama.substrate_sdk_android.runtime.extrinsic.ExtrinsicBuilder
import io.novasama.substrate_sdk_android.runtime.extrinsic.call
import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.KeyPairSigner
import io.novasama.substrate_sdk_android.runtime.extrinsic.v5.transactionExtension.extensions.ChargeAssetTxPayment
import io.novasama.substrate_sdk_android.runtime.metadata.callOrNull
import io.novasama.substrate_sdk_android.runtime.metadata.module
import io.novasama.substrate_sdk_android.ss58.SS58Encoder
import java.math.BigInteger

internal object PolkadotTransactionSigner {
    suspend fun sign(request: PolkadotSigningRequest): PolkadotSignedTransaction {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.ed25519PublicKey(privateKey)
        val sourceAccountId = SS58Encoder.decode(request.sourceAddress)
        val recipientAccountId = SS58Encoder.decode(request.recipientAddress)
        require(sourceAccountId.contentEquals(publicKey)) {
            "Polkadot private key does not match the source address."
        }

        val era = Era.getEraFromBlockPeriod(
            currentBlockNumber = request.context.currentBlockNumber,
            periodInBlocks = MORTAL_ERA_PERIOD_BLOCKS,
        )
        val signer = KeyPairSigner(
            keypair = BaseKeypair(privateKey = privateKey, publicKey = publicKey),
            encryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
        )
        val builder = ExtrinsicBuilder(
            runtime = request.context.runtime,
            nonce = request.nonce,
            runtimeVersion = request.context.runtimeVersion,
            genesisHash = request.context.genesisHash,
            accountId = sourceAccountId,
            signer = signer,
            era = era,
            blockHash = request.context.blockHash,
            batchMode = BatchMode.BATCH,
        )

        builder.setTransactionExtension(ChargeAssetTxPayment(tip = BigInteger.ZERO, assetId = null))
        if (request.assetHubAssetId == null) {
            builder.nativeTransfer(
                recipientAccountId = recipientAccountId,
                amountRaw = request.amountRaw,
            )
        } else {
            builder.assetHubTransfer(
                assetId = request.assetHubAssetId,
                recipientAccountId = recipientAccountId,
                amountRaw = request.amountRaw,
            )
        }

        val extrinsic = builder.buildExtrinsic()
        return PolkadotSignedTransaction(extrinsicHex = extrinsic.extrinsicHex)
    }

    private fun io.novasama.substrate_sdk_android.runtime.extrinsic.builder.ExtrinsicBuilder.nativeTransfer(
        recipientAccountId: ByteArray,
        amountRaw: BigInteger,
    ) {
        val balances = runtime.metadata.module("Balances")
        val callName = listOf("transfer_keep_alive", "transfer_allow_death", "transfer")
            .firstOrNull { name -> balances.callOrNull(name) != null }
            ?: error("Polkadot runtime does not expose a Balances transfer call.")
        call(
            moduleName = "Balances",
            callName = callName,
            arguments = mapOf(
                "dest" to DictEnum.Entry(name = "Id", value = recipientAccountId),
                "value" to amountRaw,
            ),
        )
    }

    private fun io.novasama.substrate_sdk_android.runtime.extrinsic.builder.ExtrinsicBuilder.assetHubTransfer(
        assetId: BigInteger,
        recipientAccountId: ByteArray,
        amountRaw: BigInteger,
    ) {
        call(
            moduleName = "Assets",
            callName = "transfer",
            arguments = mapOf(
                "id" to assetId,
                "target" to DictEnum.Entry(name = "Id", value = recipientAccountId),
                "amount" to amountRaw,
            ),
        )
    }

    private const val MORTAL_ERA_PERIOD_BLOCKS = 64
}

internal data class PolkadotSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountRaw: BigInteger,
    val assetHubAssetId: BigInteger?,
    val nonce: BigInteger,
    val context: PolkadotRuntimeContext,
    val privateKeyHex: String,
)

internal data class PolkadotSignedTransaction(
    val extrinsicHex: String,
)
