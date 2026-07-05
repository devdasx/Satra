package dev.satra.wallet.data.sync.solana

import dev.satra.wallet.data.db.WalletAddressRecord
import dev.satra.wallet.data.db.WalletTransactionDirection
import dev.satra.wallet.data.sync.evm.EvmSyncCompleteness
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class SolanaWalletSyncServiceTest {
    @Test
    fun walletWideSyncIgnoresStoredNonSolanaAddresses() = runBlocking {
        val service = SolanaWalletSyncService(
            clientFactory = { error("Solana RPC should not be called for non-Solana addresses.") },
        )

        val result = service.syncWallet(
            walletId = "wallet",
            addresses = listOf(address(networkId = "bitcoin", address = "bc1qexample")),
        )

        assertEquals(emptyList<SolanaNetworkSyncResult>(), result.networkResults)
    }

    @Test
    fun syncAggregatesSplBalancesAndNormalizesTokenHistory() = runBlocking {
        val service = SolanaWalletSyncService(
            clientFactory = { FakeSolanaRpcClient() },
            maxSignaturesPerAddress = 10,
        )
        val emitted = mutableListOf<SolanaNetworkSyncResult>()

        val result = service.syncWallet(
            walletId = "wallet",
            addresses = listOf(address(networkId = "solana", address = WALLET_ADDRESS)),
            onNetworkResult = { networkResult -> emitted += networkResult },
        )

        val network = result.networkResults.single()
        val solBalance = network.balances.first { it.assetId == "solana:sol" }
        val usdcBalance = network.balances.first { it.assetId == "solana:usdc" }
        val usdcTransaction = network.transactions.single { it.assetId == "solana:usdc" }

        assertTrue(emitted.isNotEmpty())
        assertEquals(EvmSyncCompleteness.Complete, network.balanceCompleteness)
        assertEquals(EvmSyncCompleteness.Complete, network.historyCompleteness)
        assertEquals("2", solBalance.balanceDecimal)
        assertEquals("1.5", usdcBalance.balanceDecimal)
        assertEquals("1.5", usdcTransaction.amountDecimal)
        assertEquals(WalletTransactionDirection.Incoming, usdcTransaction.direction)
        assertEquals(SIGNATURE, usdcTransaction.transactionHash)
    }

    @Test
    fun syncUsesSlotBlockTimeWhenTransactionAndSignatureTimeAreMissing() = runBlocking {
        val service = SolanaWalletSyncService(
            clientFactory = {
                FakeSolanaRpcClient(
                    signatureBlockTimeSeconds = null,
                    parsedTransactionJson = parsedUsdcReceiveTransaction(includeBlockTime = false),
                    blockTimeSecondsBySlot = mapOf((SLOT - 1) to 1_785_000_000L),
                )
            },
            maxSignaturesPerAddress = 10,
        )

        val result = service.syncWallet(
            walletId = "wallet",
            addresses = listOf(address(networkId = "solana", address = WALLET_ADDRESS)),
        )

        val transaction = result.networkResults.single().transactions.single { it.assetId == "solana:usdc" }
        assertEquals(1_785_000_000_000L, transaction.timestampMillis)
    }

    @Test
    fun syncDoesNotUseLocalClockWhenSolanaTimestampIsUnavailable() = runBlocking {
        val service = SolanaWalletSyncService(
            clientFactory = {
                FakeSolanaRpcClient(
                    signatureBlockTimeSeconds = null,
                    parsedTransactionJson = parsedUsdcReceiveTransaction(includeBlockTime = false),
                    blockTimeSecondsBySlot = mapOf((SLOT - 1) to null),
                )
            },
            maxSignaturesPerAddress = 10,
        )

        val result = service.syncWallet(
            walletId = "wallet",
            addresses = listOf(address(networkId = "solana", address = WALLET_ADDRESS)),
        )

        val transaction = result.networkResults.single().transactions.single { it.assetId == "solana:usdc" }
        assertEquals(0L, transaction.timestampMillis)
    }

    private inner class FakeSolanaRpcClient(
        private val signatureBlockTimeSeconds: Long? = 1_784_000_000L,
        private val parsedTransactionJson: JSONObject? = parsedUsdcReceiveTransaction(),
        private val blockTimeSecondsBySlot: Map<Long, Long?> = emptyMap(),
    ) : SolanaRpcClient {
        private val provider = SolanaRpcProvider("fake-solana", "memory://solana")

        override suspend fun genesisHash(): SolanaRpcCallResult<String> =
            SolanaRpcCallResult(SolanaProviderRegistry.MAINNET_GENESIS_HASH, provider, SLOT)

        override suspend fun slot(): SolanaRpcCallResult<Long> =
            SolanaRpcCallResult(SLOT, provider, SLOT)

        override suspend fun nativeBalance(address: String): SolanaRpcCallResult<BigInteger> =
            SolanaRpcCallResult(BigInteger("2000000000"), provider, SLOT)

        override suspend fun tokenAccountsByOwner(
            ownerAddress: String,
            programId: String,
        ): SolanaRpcCallResult<List<SolanaTokenAccount>> =
            SolanaRpcCallResult(
                value = if (programId == SolanaProviderRegistry.TOKEN_PROGRAM_ID) {
                    listOf(
                        SolanaTokenAccount(
                            address = TOKEN_ACCOUNT,
                            owner = ownerAddress,
                            mint = USDC_MINT,
                            amountRaw = BigInteger("1500000"),
                            decimals = 6,
                            programId = programId,
                        ),
                    )
                } else {
                    emptyList()
                },
                provider = provider,
                slot = SLOT,
            )

        override suspend fun signaturesForAddress(
            address: String,
            limit: Int,
            beforeSignature: String?,
        ): SolanaRpcCallResult<List<SolanaSignatureInfo>> =
            SolanaRpcCallResult(
                listOf(
                    SolanaSignatureInfo(
                        signature = SIGNATURE,
                        slot = SLOT - 1,
                        blockTimeSeconds = signatureBlockTimeSeconds,
                        memo = null,
                        err = null,
                    ),
                ),
                provider,
                SLOT,
            )

        override suspend fun parsedTransaction(signature: String): SolanaRpcCallResult<JSONObject?> =
            SolanaRpcCallResult(parsedTransactionJson, provider, SLOT)

        override suspend fun blockTime(slot: Long): SolanaRpcCallResult<Long?> =
            SolanaRpcCallResult(blockTimeSecondsBySlot[slot], provider, SLOT)

        override suspend fun tokenLargestAccounts(mint: String): SolanaRpcCallResult<JSONArray> =
            SolanaRpcCallResult(JSONArray(), provider, SLOT)

        override suspend fun parsedAccountInfo(address: String): SolanaRpcCallResult<JSONObject?> =
            SolanaRpcCallResult(null, provider, SLOT)

        override suspend fun latestBlockhash(): SolanaRpcCallResult<String> =
            SolanaRpcCallResult("11111111111111111111111111111111", provider, SLOT)

        override suspend fun sendTransaction(base64Transaction: String): SolanaRpcCallResult<String> =
            SolanaRpcCallResult(SIGNATURE, provider, SLOT)
    }

    private fun parsedUsdcReceiveTransaction(includeBlockTime: Boolean = true): JSONObject =
        JSONObject()
            .put("slot", SLOT - 1)
            .also { transaction ->
                if (includeBlockTime) {
                    transaction.put("blockTime", 1_784_000_000L)
                }
            }
            .put(
                "meta",
                JSONObject()
                    .put("err", JSONObject.NULL)
                    .put("fee", 5_000)
                    .put("preBalances", JSONArray().put(2_000_005_000L).put(2_039_280L).put(0L))
                    .put("postBalances", JSONArray().put(2_000_000_000L).put(2_039_280L).put(0L))
                    .put(
                        "preTokenBalances",
                        JSONArray().put(
                            tokenBalance(
                                accountIndex = 1,
                                owner = WALLET_ADDRESS,
                                mint = USDC_MINT,
                                amount = "0",
                                decimals = 6,
                            ),
                        ),
                    )
                    .put(
                        "postTokenBalances",
                        JSONArray().put(
                            tokenBalance(
                                accountIndex = 1,
                                owner = WALLET_ADDRESS,
                                mint = USDC_MINT,
                                amount = "1500000",
                                decimals = 6,
                            ),
                        ),
                    ),
            )
            .put(
                "transaction",
                JSONObject()
                    .put("signatures", JSONArray().put(SIGNATURE))
                    .put(
                        "message",
                        JSONObject()
                            .put(
                                "accountKeys",
                                JSONArray()
                                    .put(JSONObject().put("pubkey", WALLET_ADDRESS))
                                    .put(JSONObject().put("pubkey", TOKEN_ACCOUNT))
                                    .put(JSONObject().put("pubkey", OTHER_TOKEN_ACCOUNT)),
                            )
                            .put(
                                "instructions",
                                JSONArray().put(
                                    JSONObject()
                                        .put("program", "spl-token")
                                        .put(
                                            "parsed",
                                            JSONObject()
                                                .put("type", "transferChecked")
                                                .put(
                                                    "info",
                                                    JSONObject()
                                                        .put("source", OTHER_TOKEN_ACCOUNT)
                                                        .put("destination", TOKEN_ACCOUNT)
                                                        .put("authority", "sender"),
                                                ),
                                        ),
                                ),
                            ),
                    ),
            )

    private fun tokenBalance(
        accountIndex: Int,
        owner: String,
        mint: String,
        amount: String,
        decimals: Int,
    ): JSONObject =
        JSONObject()
            .put("accountIndex", accountIndex)
            .put("mint", mint)
            .put("owner", owner)
            .put(
                "uiTokenAmount",
                JSONObject()
                    .put("amount", amount)
                    .put("decimals", decimals),
            )

    private fun address(
        networkId: String,
        address: String,
    ): WalletAddressRecord =
        WalletAddressRecord(
            addressId = "$networkId-address",
            walletId = "wallet",
            networkId = networkId,
            address = address,
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
        const val WALLET_ADDRESS = "7UXMSBupWG6gPYC8wGuegX5EqKcJmgoHf1cv8KVv6WJ6"
        const val TOKEN_ACCOUNT = "7gjVPGnGoFK3HN1WnM4LcDWaNB1SutB781N38Us6FZ8M"
        const val OTHER_TOKEN_ACCOUNT = "7cXXVXXD7pmT7r4Rh1qfTXRJ8wSrEo6ue2r2ADkrE9F4"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SIGNATURE = "3wHz6t2e3RtMz5p3HBGai8FHSCqoPZHRSN4czoxUoR8qgNKqDqUuGZmbsNyQ7wA1R3kYH7tvP2i3VrS3yWfP3mW4"
        const val SLOT = 430_000_000L
    }
}
