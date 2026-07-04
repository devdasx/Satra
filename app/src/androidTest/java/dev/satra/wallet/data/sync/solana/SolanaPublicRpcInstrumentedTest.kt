package dev.satra.wallet.data.sync.solana

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.satra.wallet.data.assets.SupportedAsset
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class SolanaPublicRpcInstrumentedTest {
    @Test
    fun publicSolanaRpcReturnsMainnetSlotNativeBalanceAndSplTokenAccounts() = runBlocking {
        val client = SolanaJsonRpcClient(SolanaProviderRegistry.config)
        val genesis = client.genesisHash()
        val slot = client.slot()
        val nativeBalance = client.nativeBalance(FIXTURE_SOLANA_ADDRESS)
        val tokenAccounts = client.tokenAccountsByOwner(
            ownerAddress = FIXTURE_SOLANA_ADDRESS,
            programId = SolanaProviderRegistry.TOKEN_PROGRAM_ID,
        )

        assertEquals(SolanaProviderRegistry.MAINNET_GENESIS_HASH, genesis.value)
        assertTrue("Solana slot should be nonzero", slot.value > 0L)
        assertTrue("SOL balance should be valid", nativeBalance.value >= BigInteger.ZERO)
        assertTrue("SPL token account response should be valid", tokenAccounts.value.size >= 0)
    }

    @Test
    fun solanaWalletSyncReturnsRealSupportedSplBalanceAndHistory() = runBlocking {
        val client = SolanaJsonRpcClient(SolanaProviderRegistry.config)
        val fixture = discoverSupportedSplFixture(client)
        val service = SolanaWalletSyncService(
            maxSignaturesPerAddress = 20,
            maxParallelTransactionFetches = 6,
        )

        val result = service.syncWallet(
            walletId = "fixture-solana",
            addresses = listOf(address(fixture.ownerAddress)),
        )

        val network = result.networkResults.single()
        val tokenBalance = network.balances.first { it.assetId == fixture.asset.assetId }
        val tokenTransactions = network.transactions.filter { it.assetId == fixture.asset.assetId }

        assertTrue(
            "Expected Solana balance sync not to fail: ${network.error}",
            network.balanceCompleteness != EvmSyncCompleteness.Failed,
        )
        assertTrue(
            "Expected Solana history sync not to fail: ${network.error}",
            network.historyCompleteness != EvmSyncCompleteness.Failed,
        )
        assertTrue(
            "Expected a real ${fixture.asset.symbol} balance for ${fixture.ownerAddress}",
            tokenBalance.balanceRaw.toBigInteger() >= BigInteger.ZERO,
        )
        assertTrue(
            "Expected real ${fixture.asset.symbol} history for ${fixture.ownerAddress}; " +
                "fixture=${fixture.signature}, error=${network.error}",
            tokenTransactions.isNotEmpty(),
        )
    }

    private suspend fun discoverSupportedSplFixture(client: SolanaRpcClient): SolanaSplFixture {
        val supportedTokens = SupportedAssetCatalog.assets.filter { asset ->
            asset.networkId == SolanaProviderRegistry.NETWORK_ID &&
                asset.contractAddress != null
        }
        supportedTokens.forEach { asset ->
            val mint = checkNotNull(asset.contractAddress)
            val signatures = runCatching {
                client.signaturesForAddress(mint, limit = 20).value
            }.getOrNull().orEmpty()
            signatures.forEach { signature ->
                val transaction = runCatching {
                    client.parsedTransaction(signature.signature).value
                }.getOrNull() ?: return@forEach
                val tokenDelta = transaction.supportedTokenDeltaForMint(mint) ?: return@forEach
                val ownerTokenAccounts = (
                    runCatching {
                        client.tokenAccountsByOwner(
                            ownerAddress = tokenDelta.ownerAddress,
                            programId = SolanaProviderRegistry.TOKEN_PROGRAM_ID,
                        ).value
                    }.getOrNull().orEmpty() +
                        runCatching {
                            client.tokenAccountsByOwner(
                                ownerAddress = tokenDelta.ownerAddress,
                                programId = SolanaProviderRegistry.TOKEN_2022_PROGRAM_ID,
                            ).value
                        }.getOrNull().orEmpty()
                    )
                if (ownerTokenAccounts.none { it.mint == mint }) return@forEach
                return SolanaSplFixture(
                    asset = asset,
                    ownerAddress = tokenDelta.ownerAddress,
                    tokenAccountAddress = tokenDelta.tokenAccountAddress,
                    signature = signature.signature,
                )
            }
        }
        error("No live supported Solana SPL token fixture was discovered.")
    }

    private fun JSONObject.supportedTokenDeltaForMint(mint: String): SolanaFixtureTokenDelta? {
        val accountKeys = optJSONObject("transaction")
            ?.optJSONObject("message")
            ?.accountKeys()
            .orEmpty()
        val pre = tokenAmountsByOwner(
            key = "preTokenBalances",
            mint = mint,
            accountKeys = accountKeys,
        )
        val post = tokenAmountsByOwner(
            key = "postTokenBalances",
            mint = mint,
            accountKeys = accountKeys,
        )
        return (pre.keys + post.keys)
            .firstNotNullOfOrNull { owner ->
                val before = pre[owner]?.amountRaw ?: BigInteger.ZERO
                val after = post[owner]?.amountRaw ?: BigInteger.ZERO
                val delta = after - before
                val tokenAccountAddress = post[owner]?.tokenAccountAddress
                    ?: pre[owner]?.tokenAccountAddress
                if (delta == BigInteger.ZERO || tokenAccountAddress.isNullOrBlank()) {
                    null
                } else {
                    SolanaFixtureTokenDelta(
                        ownerAddress = owner,
                        tokenAccountAddress = tokenAccountAddress,
                    )
                }
            }
    }

    private fun JSONObject.tokenAmountsByOwner(
        key: String,
        mint: String,
        accountKeys: List<String>,
    ): Map<String, SolanaFixtureTokenAmount> {
        val balances = optJSONObject("meta")?.optJSONArray(key) ?: return emptyMap()
        val totals = mutableMapOf<String, SolanaFixtureTokenAmount>()
        for (index in 0 until balances.length()) {
            val item = balances.optJSONObject(index) ?: continue
            if (item.optString("mint") != mint) continue
            val owner = item.optString("owner").takeIf(String::isNotBlank) ?: continue
            val accountIndex = item.optInt("accountIndex", -1)
            val tokenAccountAddress = accountKeys.getOrNull(accountIndex) ?: continue
            val amountRaw = item.optJSONObject("uiTokenAmount")
                ?.optString("amount")
                ?.takeIf(String::isNotBlank)
                ?.let(::BigInteger) ?: BigInteger.ZERO
            val previous = totals[owner]
            totals[owner] = SolanaFixtureTokenAmount(
                tokenAccountAddress = tokenAccountAddress,
                amountRaw = (previous?.amountRaw ?: BigInteger.ZERO) + amountRaw,
            )
        }
        return totals
    }

    private fun JSONObject.accountKeys(): List<String> {
        val keys = optJSONArray("accountKeys") ?: return emptyList()
        return buildList {
            for (index in 0 until keys.length()) {
                when (val item = keys.opt(index)) {
                    is JSONObject -> item.optString("pubkey").takeIf(String::isNotBlank)?.let(::add)
                    is String -> add(item)
                }
            }
        }
    }

    private fun address(value: String): WalletAddressRecord =
        WalletAddressRecord(
            addressId = "solana-fixture-address",
            walletId = "fixture-solana",
            networkId = SolanaProviderRegistry.NETWORK_ID,
            address = value,
            addressType = "receive",
            derivationPath = null,
            publicKey = null,
            privateKeyId = null,
            isPrimary = true,
            isChange = false,
            addressIndex = 0,
            label = null,
            createdAt = 1L,
            updatedAt = 1L,
            lastUsedAt = null,
            metadataJson = "{}",
        )

    private companion object {
        const val FIXTURE_SOLANA_ADDRESS = "5Q544fKrFoe6tsF9LxLUjn57gk9UC4ySmoYJM2T73eG1"
    }
}

private data class SolanaSplFixture(
    val asset: SupportedAsset,
    val ownerAddress: String,
    val tokenAccountAddress: String,
    val signature: String,
)

private data class SolanaFixtureTokenDelta(
    val ownerAddress: String,
    val tokenAccountAddress: String,
)

private data class SolanaFixtureTokenAmount(
    val tokenAccountAddress: String,
    val amountRaw: BigInteger,
)
