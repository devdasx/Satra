package dev.satra.wallet.data.send.polkadot

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.sync.accountchain.AccountChainHttpClient
import dev.satra.wallet.data.sync.accountchain.AccountChainProvider
import dev.satra.wallet.data.sync.accountchain.PolkadotStorage
import io.novasama.substrate_sdk_android.runtime.RuntimeSnapshot
import io.novasama.substrate_sdk_android.runtime.definitions.dynamic.DynamicTypeResolver
import io.novasama.substrate_sdk_android.runtime.definitions.dynamic.extentsions.GenericsExtension
import io.novasama.substrate_sdk_android.runtime.definitions.registry.TypeRegistry
import io.novasama.substrate_sdk_android.runtime.definitions.registry.v14Preset
import io.novasama.substrate_sdk_android.runtime.definitions.v14.TypesParserV14
import io.novasama.substrate_sdk_android.runtime.metadata.RuntimeMetadataReader
import io.novasama.substrate_sdk_android.runtime.metadata.builder.VersionedRuntimeBuilder
import io.novasama.substrate_sdk_android.wsrpc.request.runtime.chain.RuntimeVersion
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

internal class PolkadotSendClient(
    private val httpClient: AccountChainHttpClient = AccountChainHttpClient(),
) {
    suspend fun context(provider: AccountChainProvider): PolkadotRuntimeContext {
        val metadataHex = rpcString(provider, "state_getMetadata")
            ?: error("Polkadot RPC response missing runtime metadata.")
        val runtimeVersion = runtimeVersion(provider)
        val header = rpcObject(provider, "chain_getHeader")
        val currentBlock = PolkadotStorage.parseHeaderNumber(header)?.toInt()
            ?: error("Polkadot RPC response missing current block number.")
        val genesisHash = rpcString(provider, "chain_getBlockHash", JSONArray().put(0))
            ?.hexToBytes()
            ?: error("Polkadot RPC response missing genesis hash.")
        val blockHash = rpcString(provider, "chain_getBlockHash", JSONArray().put(currentBlock))
            ?.hexToBytes()
            ?: error("Polkadot RPC response missing mortal-era block hash.")

        return PolkadotRuntimeContext(
            runtime = PolkadotRuntimeFactory.build(metadataHex),
            runtimeVersion = runtimeVersion,
            genesisHash = genesisHash,
            blockHash = blockHash,
            currentBlockNumber = currentBlock,
        )
    }

    suspend fun nonce(
        provider: AccountChainProvider,
        address: String,
    ): BigInteger {
        runCatching {
            parseInteger(rpcRaw(provider, "system_accountNextIndex", JSONArray().put(address)))
        }.getOrNull()?.let { return it }

        val accountId = PolkadotStorage.decodeSs58AccountId(address)
        val accountStorage = rpcString(
            provider = provider,
            method = "state_getStorage",
            params = JSONArray().put(PolkadotStorage.systemAccountKey(accountId)),
        )
        return PolkadotStorage.parseSystemAccountNonce(accountStorage) ?: BigInteger.ZERO
    }

    suspend fun nativeBalance(
        provider: AccountChainProvider,
        address: String,
    ): BigInteger {
        val accountId = PolkadotStorage.decodeSs58AccountId(address)
        val storage = rpcString(
            provider = provider,
            method = "state_getStorage",
            params = JSONArray().put(PolkadotStorage.systemAccountKey(accountId)),
        )
        return PolkadotStorage.parseSystemAccountFree(storage) ?: BigInteger.ZERO
    }

    suspend fun assetBalance(
        provider: AccountChainProvider,
        address: String,
        assetId: Long,
    ): BigInteger {
        val accountId = PolkadotStorage.decodeSs58AccountId(address)
        val storage = rpcString(
            provider = provider,
            method = "state_getStorage",
            params = JSONArray().put(PolkadotStorage.assetAccountKey(assetId, accountId)),
        )
        return PolkadotStorage.parseAssetAccountBalance(storage) ?: BigInteger.ZERO
    }

    suspend fun fee(
        provider: AccountChainProvider,
        extrinsicHex: String,
    ): BigInteger {
        val result = rpcObject(
            provider = provider,
            method = "payment_queryInfo",
            params = JSONArray().put(extrinsicHex),
        ) ?: error("Polkadot fee RPC response missing result.")
        return parseInteger(result.opt("partialFee"))
    }

    suspend fun broadcast(
        provider: AccountChainProvider,
        extrinsicHex: String,
    ): String =
        rpcString(provider, "author_submitExtrinsic", JSONArray().put(extrinsicHex))
            ?.takeIf(String::isNotBlank)
            ?: SatraSigningCrypto.blake2b256(extrinsicHex.hexToBytes()).toHexWithPrefix()

    private suspend fun runtimeVersion(provider: AccountChainProvider): RuntimeVersion {
        val result = rpcObject(provider, "state_getRuntimeVersion")
            ?: error("Polkadot RPC response missing runtime version.")
        return RuntimeVersion(
            specVersion = parseInteger(result.opt("specVersion")).toInt(),
            transactionVersion = parseInteger(result.opt("transactionVersion")).toInt(),
        )
    }

    private suspend fun rpcString(
        provider: AccountChainProvider,
        method: String,
        params: JSONArray = JSONArray(),
    ): String? =
        rpcRaw(provider, method, params)
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()

    private suspend fun rpcObject(
        provider: AccountChainProvider,
        method: String,
        params: JSONArray = JSONArray(),
    ): JSONObject? =
        rpcRaw(provider, method, params) as? JSONObject

    private suspend fun rpcRaw(
        provider: AccountChainProvider,
        method: String,
        params: JSONArray = JSONArray(),
    ): Any? {
        val response = JSONObject(
            httpClient.post(
                provider = provider,
                path = "",
                body = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", "satra")
                    .put("method", method)
                    .put("params", params),
            ),
        )
        response.optJSONObject("error")?.let { rpcError ->
            error("Polkadot RPC error ${rpcError.optInt("code")}: ${rpcError.optString("message", rpcError.toString())}")
        }
        return if (response.has("result")) response.opt("result") else null
    }

    private fun parseInteger(value: Any?): BigInteger =
        when (value) {
            is BigInteger -> value
            is Number -> BigInteger.valueOf(value.toLong())
            is String -> {
                val clean = value.trim()
                if (clean.startsWith("0x", ignoreCase = true)) {
                    BigInteger(clean.removePrefix("0x").removePrefix("0X").ifBlank { "0" }, 16)
                } else {
                    BigInteger(clean)
                }
            }
            else -> error("Polkadot RPC integer value is missing.")
        }
}

internal data class PolkadotRuntimeContext(
    val runtime: RuntimeSnapshot,
    val runtimeVersion: RuntimeVersion,
    val genesisHash: ByteArray,
    val blockHash: ByteArray,
    val currentBlockNumber: Int,
)

private object PolkadotRuntimeFactory {
    fun build(metadataHex: String): RuntimeSnapshot {
        val metadataReader = RuntimeMetadataReader.read(metadataHex)
        val postV14Metadata = metadataReader.metadataPostV14
        val postV14Schema = postV14Metadata.schema
        val metadataTypePreset = TypesParserV14.parse(postV14Metadata[postV14Schema.lookup], v14Preset())
        val typeRegistry = TypeRegistry(
            types = metadataTypePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension,
            ),
        )
        return RuntimeSnapshot(
            typeRegistry = typeRegistry,
            metadata = VersionedRuntimeBuilder.buildMetadata(metadataReader, typeRegistry),
        )
    }
}

private fun String.hexToBytes(): ByteArray {
    val normalized = removePrefix("0x").removePrefix("0X")
    require(normalized.length % 2 == 0 && normalized.all(Char::isHexDigit)) {
        "Invalid Polkadot hex data."
    }
    return ByteArray(normalized.length / 2) { index ->
        normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun ByteArray.toHexWithPrefix(): String =
    joinToString(prefix = "0x", separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
