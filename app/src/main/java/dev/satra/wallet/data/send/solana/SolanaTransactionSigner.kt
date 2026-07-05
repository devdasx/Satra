package dev.satra.wallet.data.send.solana

import dev.satra.wallet.data.send.crypto.SatraSigningCrypto
import dev.satra.wallet.data.send.crypto.SatraSigningCrypto.toHex
import dev.satra.wallet.data.sync.solana.SolanaProviderRegistry
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

internal object SolanaTransactionSigner {
    fun sign(
        request: SolanaSigningRequest,
    ): SolanaSignedTransaction {
        val privateKey = SatraSigningCrypto.parseEd25519PrivateKey(request.privateKeyHex)
        val publicKey = SatraSigningCrypto.ed25519PublicKey(privateKey)
        val signerAddress = SatraSigningCrypto.base58(publicKey)
        require(signerAddress == request.sourceAddress) {
            "Solana private key does not match source address."
        }
        val amountRaw = request.amountRaw.toULongExact()
        val blockhashBytes = publicKeyBytes(request.recentBlockhash)
        val instructions = if (request.contractAddress == null) {
            listOf(systemTransferInstruction(request.sourceAddress, request.recipientAddress, amountRaw))
        } else {
            splTokenTransferInstructions(request, amountRaw)
        }
        val message = compileMessage(
            payerAddress = request.sourceAddress,
            recentBlockhash = blockhashBytes,
            instructions = instructions,
        )
        val signature = SatraSigningCrypto.ed25519Sign(message, privateKey)
        val transaction = ByteArrayOutputStream().use { out ->
            out.writeShortVecLength(1)
            out.write(signature)
            out.write(message)
            out.toByteArray()
        }
        return SolanaSignedTransaction(
            transactionBase64 = Base64.getEncoder().encodeToString(transaction),
            transactionBytes = transaction,
            signature = SatraSigningCrypto.base58(signature),
            feeLamports = SOLANA_BASE_FEE_LAMPORTS,
        )
    }

    fun parseTokenAccounts(metadataJson: String): List<SolanaSigningTokenAccount> {
        val root = runCatching { JSONObject(metadataJson) }.getOrDefault(JSONObject())
        val items = root.optJSONArray("tokenAccounts") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val address = item.optString("address").takeIf(String::isNotBlank) ?: continue
                val owner = item.optString("owner").takeIf(String::isNotBlank) ?: continue
                val mint = item.optString("mint").takeIf(String::isNotBlank) ?: continue
                val programId = item.optString("programId")
                    .takeIf(String::isNotBlank)
                    ?: SolanaProviderRegistry.TOKEN_PROGRAM_ID
                add(
                    SolanaSigningTokenAccount(
                        address = address,
                        owner = owner,
                        mint = mint,
                        amountRaw = item.optString("amountRaw").takeIf(String::isNotBlank) ?: "0",
                        decimals = item.optInt("decimals", 0),
                        programId = programId,
                    ),
                )
            }
        }
    }

    fun associatedTokenAddress(
        ownerAddress: String,
        mintAddress: String,
        tokenProgramId: String,
    ): String {
        val owner = publicKeyBytes(ownerAddress)
        val mint = publicKeyBytes(mintAddress)
        val tokenProgram = publicKeyBytes(tokenProgramId)
        val associatedProgram = publicKeyBytes(ASSOCIATED_TOKEN_PROGRAM_ID)
        for (bump in 255 downTo 0) {
            val candidate = SatraSigningCrypto.sha256(
                owner +
                    tokenProgram +
                    mint +
                    byteArrayOf(bump.toByte()) +
                    associatedProgram +
                    PROGRAM_DERIVED_ADDRESS_MARKER,
            )
            if (!Ed25519.validatePublicKeyFull(candidate, 0)) {
                return SatraSigningCrypto.base58(candidate)
            }
        }
        error("Could not derive associated token address.")
    }

    fun publicKeyBytes(address: String): ByteArray {
        val decoded = SatraSigningCrypto.base58Decode(address)
        require(decoded.size == SOLANA_PUBLIC_KEY_SIZE) { "Invalid Solana public key." }
        return decoded
    }

    private fun splTokenTransferInstructions(
        request: SolanaSigningRequest,
        amountRaw: ULong,
    ): List<SolanaInstruction> {
        val mint = checkNotNull(request.contractAddress) { "Missing SPL token mint." }
        val sourceAccount = request.senderTokenAccounts
            .filter { account -> account.owner == request.sourceAddress && account.mint == mint }
            .filter { account -> BigInteger(account.amountRaw) >= request.amountRaw }
            .maxByOrNull { account -> BigInteger(account.amountRaw) }
            ?: error("No funded sender token account for SPL transfer.")
        val tokenProgramId = sourceAccount.programId
        val recipientTokenAccount = request.recipientTokenAccount
            ?: associatedTokenAddress(request.recipientAddress, mint, tokenProgramId)
        return buildList {
            if (request.createRecipientTokenAccount) {
                add(
                    SolanaInstruction(
                        programId = ASSOCIATED_TOKEN_PROGRAM_ID,
                        accounts = listOf(
                            SolanaAccountMeta(request.sourceAddress, isSigner = true, isWritable = true),
                            SolanaAccountMeta(recipientTokenAccount, isSigner = false, isWritable = true),
                            SolanaAccountMeta(request.recipientAddress, isSigner = false, isWritable = false),
                            SolanaAccountMeta(mint, isSigner = false, isWritable = false),
                            SolanaAccountMeta(SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false),
                            SolanaAccountMeta(tokenProgramId, isSigner = false, isWritable = false),
                        ),
                        data = ByteArray(0),
                    ),
                )
            }
            add(
                SolanaInstruction(
                    programId = tokenProgramId,
                    accounts = listOf(
                        SolanaAccountMeta(sourceAccount.address, isSigner = false, isWritable = true),
                        SolanaAccountMeta(mint, isSigner = false, isWritable = false),
                        SolanaAccountMeta(recipientTokenAccount, isSigner = false, isWritable = true),
                        SolanaAccountMeta(request.sourceAddress, isSigner = true, isWritable = false),
                    ),
                    data = ByteArrayOutputStream().use { out ->
                        out.write(SPL_TOKEN_TRANSFER_CHECKED_INSTRUCTION)
                        out.writeUInt64LE(amountRaw)
                        out.write(request.decimals)
                        out.toByteArray()
                    },
                ),
            )
        }
    }

    private fun systemTransferInstruction(
        fromAddress: String,
        toAddress: String,
        lamports: ULong,
    ): SolanaInstruction =
        SolanaInstruction(
            programId = SYSTEM_PROGRAM_ID,
            accounts = listOf(
                SolanaAccountMeta(fromAddress, isSigner = true, isWritable = true),
                SolanaAccountMeta(toAddress, isSigner = false, isWritable = true),
            ),
            data = ByteArrayOutputStream().use { out ->
                out.writeUInt32LE(SYSTEM_TRANSFER_INSTRUCTION.toLong())
                out.writeUInt64LE(lamports)
                out.toByteArray()
            },
        )

    private fun compileMessage(
        payerAddress: String,
        recentBlockhash: ByteArray,
        instructions: List<SolanaInstruction>,
    ): ByteArray {
        val metas = LinkedHashMap<String, SolanaAccountMeta>()
        fun addMeta(meta: SolanaAccountMeta) {
            val current = metas[meta.address]
            metas[meta.address] = if (current == null) {
                meta
            } else {
                SolanaAccountMeta(
                    address = meta.address,
                    isSigner = current.isSigner || meta.isSigner,
                    isWritable = current.isWritable || meta.isWritable,
                )
            }
        }
        addMeta(SolanaAccountMeta(payerAddress, isSigner = true, isWritable = true))
        instructions.forEach { instruction ->
            instruction.accounts.forEach(::addMeta)
            addMeta(SolanaAccountMeta(instruction.programId, isSigner = false, isWritable = false))
        }

        val ordered = metas.values
            .sortedWith(
                compareByDescending<SolanaAccountMeta> { it.isSigner }
                    .thenBy { !it.isWritable }
                    .thenBy { if (it.address == payerAddress) 0 else 1 },
            )
        val accountIndex = ordered.mapIndexed { index, meta -> meta.address to index }.toMap()
        val readonlySigned = ordered.count { it.isSigner && !it.isWritable }
        val readonlyUnsigned = ordered.count { !it.isSigner && !it.isWritable }

        return ByteArrayOutputStream().use { out ->
            out.write(ordered.count { it.isSigner })
            out.write(readonlySigned)
            out.write(readonlyUnsigned)
            out.writeShortVecLength(ordered.size)
            ordered.forEach { meta -> out.write(publicKeyBytes(meta.address)) }
            out.write(recentBlockhash)
            out.writeShortVecLength(instructions.size)
            instructions.forEach { instruction ->
                out.write(accountIndex.getValue(instruction.programId))
                out.writeShortVecLength(instruction.accounts.size)
                instruction.accounts.forEach { meta -> out.write(accountIndex.getValue(meta.address)) }
                out.writeShortVecBytes(instruction.data)
            }
            out.toByteArray()
        }
    }

    private fun BigInteger.toULongExact(): ULong {
        require(this >= BigInteger.ZERO && bitLength() <= 64) { "Amount does not fit in Solana u64." }
        return toString().toULong()
    }

    private fun ByteArrayOutputStream.writeUInt32LE(value: Long) {
        require(value in 0L..0xffffffffL)
        repeat(4) { shift -> write(((value ushr (8 * shift)) and 0xff).toInt()) }
    }

    private fun ByteArrayOutputStream.writeUInt64LE(value: ULong) {
        var remaining = value
        repeat(8) {
            write((remaining and 0xffu).toInt())
            remaining = remaining shr 8
        }
    }

    private fun ByteArrayOutputStream.writeShortVecBytes(bytes: ByteArray) {
        writeShortVecLength(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeShortVecLength(length: Int) {
        require(length >= 0)
        var rem = length
        while (true) {
            var element = rem and 0x7f
            rem = rem ushr 7
            if (rem == 0) {
                write(element)
                break
            }
            element = element or 0x80
            write(element)
        }
    }

    private const val SOLANA_PUBLIC_KEY_SIZE = 32
    private const val SYSTEM_PROGRAM_ID = "11111111111111111111111111111111"
    private const val ASSOCIATED_TOKEN_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    private const val SYSTEM_TRANSFER_INSTRUCTION = 2
    private const val SPL_TOKEN_TRANSFER_CHECKED_INSTRUCTION = 12
    private const val SOLANA_BASE_FEE_LAMPORTS = 5_000L
    private val PROGRAM_DERIVED_ADDRESS_MARKER = "ProgramDerivedAddress".toByteArray(Charsets.UTF_8)
}

internal data class SolanaSigningRequest(
    val sourceAddress: String,
    val recipientAddress: String,
    val amountRaw: BigInteger,
    val decimals: Int,
    val contractAddress: String?,
    val recentBlockhash: String,
    val privateKeyHex: String,
    val senderTokenAccounts: List<SolanaSigningTokenAccount>,
    val recipientTokenAccount: String?,
    val createRecipientTokenAccount: Boolean,
)

internal data class SolanaSigningTokenAccount(
    val address: String,
    val owner: String,
    val mint: String,
    val amountRaw: String,
    val decimals: Int,
    val programId: String,
)

internal data class SolanaSignedTransaction(
    val transactionBase64: String,
    val transactionBytes: ByteArray,
    val signature: String,
    val feeLamports: Long,
)

private data class SolanaAccountMeta(
    val address: String,
    val isSigner: Boolean,
    val isWritable: Boolean,
)

private data class SolanaInstruction(
    val programId: String,
    val accounts: List<SolanaAccountMeta>,
    val data: ByteArray,
)
