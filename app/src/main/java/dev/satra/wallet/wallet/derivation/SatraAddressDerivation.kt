package dev.satra.wallet.wallet.derivation

import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.wallet.evm.EvmWalletDerivation
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.digests.SHA3Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.kotlin.crypto.PrivateKeyEd25519
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SatraAddressDerivation {
    const val SOURCE = "trustwallet/wallet-core registry.json"
    private const val EVM_DERIVATION_PATH = "m/44'/60'/0'/0/0"

    fun deriveReceiveAccounts(
        mnemonic: String,
        passphrase: String? = null,
    ): List<DerivedReceiveAccount> {
        val seed = EvmWalletDerivation.mnemonicToSeed(mnemonic, passphrase.orEmpty())
        return SupportedAssetCatalog.networks.flatMap { network ->
            specsForNetwork(network.networkId).map { spec ->
                deriveAccount(
                    networkId = network.networkId,
                    spec = spec,
                    seed = seed,
                    isImported = false,
                )
            }
        }
    }

    fun deriveUtxoScanAccounts(
        mnemonic: String,
        passphrase: String? = null,
        networkId: String,
        startIndex: Int = 0,
        gapLimit: Int = 20,
    ): List<DerivedReceiveAccount> {
        require(startIndex >= 0) { "Start index cannot be negative." }
        require(gapLimit > 0) { "Gap limit must be positive." }
        val seed = EvmWalletDerivation.mnemonicToSeed(mnemonic, passphrase.orEmpty())
        return utxoScanSpecsForNetwork(networkId, startIndex, gapLimit).map { spec ->
            deriveAccount(
                networkId = networkId,
                spec = spec,
                seed = seed,
                isImported = false,
            )
        }
    }

    fun derivePrivateKeyAccount(
        networkId: String,
        privateKey: String,
    ): DerivedReceiveAccount? {
        val normalized = privateKey.removePrefix("0x").removePrefix("0X")
        if (normalized.length != 64 || !normalized.all(Char::isHexDigit)) {
            return null
        }
        val spec = specsForNetwork(networkId).firstOrNull { it.curve == DerivationCurve.Secp256k1 }
            ?: return null
        val keyBytes = normalized.hexToBytes()
        val publicKey = secp256k1PublicKey(keyBytes.toPositiveBigInteger(), compressed = true)
        val uncompressedPublicKey = secp256k1PublicKey(keyBytes.toPositiveBigInteger(), compressed = false)
        val address = addressFromPublicKey(spec, publicKey, uncompressedPublicKey)
        return DerivedReceiveAccount(
            networkId = networkId,
            address = address,
            derivationPath = null,
            derivationName = spec.name,
            privateKeyHex = keyBytes.toHex(),
            publicKeyHex = uncompressedPublicKey.toHex(),
            compressedPublicKeyHex = publicKey.toHex(),
            keyFingerprint = hash160(publicKey).take(4).toByteArray().toHex(),
            isPrimary = true,
            addressIndex = 0,
            source = "private_key_imported",
        )
    }

    fun specsForNetwork(networkId: String): List<ReceiveDerivationSpec> =
        when (networkId) {
            "bitcoin" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "segwit",
                    path = "m/84'/0'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.BitcoinSegwit(hrp = "bc"),
                    isPrimary = true,
                ),
            )
            "bitcoinCash" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "cashaddr",
                    path = "m/44'/145'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.CashAddr(prefix = "bitcoincash"),
                    isPrimary = true,
                ),
            )
            "dogecoin" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "legacy",
                    path = "m/44'/3'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.Base58P2pkh(prefix = 30),
                    isPrimary = true,
                ),
            )
            "litecoin" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "segwit",
                    path = "m/84'/2'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.BitcoinSegwit(hrp = "ltc"),
                    isPrimary = true,
                ),
            )
            "ethereum",
            "arbitrum",
            "base",
            "optimism",
            "scroll",
            "zkSync",
            "polygon",
            "bnbChain",
            "opBNB",
            "avalanche",
            "celo",
            "kavaEvm",
            -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = EVM_DERIVATION_PATH,
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.Evm,
                    isPrimary = true,
                ),
            )
            "aptos" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/637'/0'/0'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.Aptos,
                    isPrimary = true,
                ),
            )
            "near" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/397'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.NearImplicit,
                    isPrimary = true,
                ),
            )
            "polkadot" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/354'/0'/0'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.Ss58(prefix = 0),
                    isPrimary = true,
                ),
            )
            "ripple" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/144'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.Ripple,
                    isPrimary = true,
                ),
            )
            "solana" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/501'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.Solana,
                    isPrimary = true,
                ),
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "phantom",
                    path = "m/44'/501'/0'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.Solana,
                    isPrimary = false,
                    addressIndex = 1,
                ),
            )
            "stellar" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/148'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.Stellar,
                    isPrimary = true,
                ),
            )
            "sui" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/784'/0'/0'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.Sui,
                    isPrimary = true,
                ),
            )
            "ton" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/607'/0'",
                    curve = DerivationCurve.Ed25519,
                    addressFormat = AddressFormat.TonV4R2,
                    isPrimary = true,
                ),
            )
            "tron" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/195'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.Tron,
                    isPrimary = true,
                ),
            )
            "kava" -> listOf(
                ReceiveDerivationSpec(
                    networkId = networkId,
                    name = "trust",
                    path = "m/44'/459'/0'/0/0",
                    curve = DerivationCurve.Secp256k1,
                    addressFormat = AddressFormat.Cosmos(hrp = "kava"),
                    isPrimary = true,
                ),
            )
            else -> emptyList()
        }

    private fun utxoScanSpecsForNetwork(
        networkId: String,
        startIndex: Int,
        gapLimit: Int,
    ): List<ReceiveDerivationSpec> {
        val templates = when (networkId) {
            "bitcoin" -> listOf(
                UtxoScanTemplate(
                    name = "taproot",
                    purpose = 86,
                    coinType = 0,
                    format = AddressFormat.BitcoinTaproot(hrp = "bc"),
                ),
                UtxoScanTemplate(
                    name = "segwit",
                    purpose = 84,
                    coinType = 0,
                    format = AddressFormat.BitcoinSegwit(hrp = "bc"),
                ),
                UtxoScanTemplate(
                    name = "nested_segwit",
                    purpose = 49,
                    coinType = 0,
                    format = AddressFormat.BitcoinNestedSegwit(p2shPrefix = 5),
                ),
                UtxoScanTemplate(
                    name = "legacy",
                    purpose = 44,
                    coinType = 0,
                    format = AddressFormat.Base58P2pkh(prefix = 0),
                ),
            )
            "bitcoinCash" -> listOf(
                UtxoScanTemplate(
                    name = "cashaddr",
                    purpose = 44,
                    coinType = 145,
                    format = AddressFormat.CashAddr(prefix = "bitcoincash"),
                ),
            )
            "dogecoin" -> listOf(
                UtxoScanTemplate(
                    name = "legacy",
                    purpose = 44,
                    coinType = 3,
                    format = AddressFormat.Base58P2pkh(prefix = 30),
                ),
            )
            "litecoin" -> listOf(
                UtxoScanTemplate(
                    name = "segwit",
                    purpose = 84,
                    coinType = 2,
                    format = AddressFormat.BitcoinSegwit(hrp = "ltc"),
                ),
                UtxoScanTemplate(
                    name = "nested_segwit",
                    purpose = 49,
                    coinType = 2,
                    format = AddressFormat.BitcoinNestedSegwit(p2shPrefix = 50),
                ),
                UtxoScanTemplate(
                    name = "legacy",
                    purpose = 44,
                    coinType = 2,
                    format = AddressFormat.Base58P2pkh(prefix = 48),
                ),
            )
            else -> emptyList()
        }

        return templates.flatMap { template ->
            listOf(false, true).flatMap { isChange ->
                (startIndex until startIndex + gapLimit).map { index ->
                    ReceiveDerivationSpec(
                        networkId = networkId,
                        name = template.name,
                        path = "m/${template.purpose}'/${template.coinType}'/0'/${if (isChange) 1 else 0}/$index",
                        curve = DerivationCurve.Secp256k1,
                        addressFormat = template.format,
                        isPrimary = false,
                        addressIndex = index,
                        isChange = isChange,
                    )
                }
            }
        }
    }

    private fun deriveAccount(
        networkId: String,
        spec: ReceiveDerivationSpec,
        seed: ByteArray,
        isImported: Boolean,
    ): DerivedReceiveAccount =
        when (spec.curve) {
            DerivationCurve.Secp256k1 -> {
                val node = deriveSecp256k1(seed, spec.path)
                val publicKey = secp256k1PublicKey(node.privateKey.toPositiveBigInteger(), compressed = true)
                val uncompressedPublicKey = secp256k1PublicKey(node.privateKey.toPositiveBigInteger(), compressed = false)
                DerivedReceiveAccount(
                    networkId = networkId,
                    address = addressFromPublicKey(spec, publicKey, uncompressedPublicKey),
                    derivationPath = spec.path,
                    derivationName = spec.name,
                    privateKeyHex = node.privateKey.toHex(),
                    publicKeyHex = uncompressedPublicKey.toHex(),
                    compressedPublicKeyHex = publicKey.toHex(),
                    keyFingerprint = hash160(publicKey).take(4).toByteArray().toHex(),
                    isPrimary = spec.isPrimary,
                    addressIndex = spec.addressIndex,
                    isChange = spec.isChange,
                    source = if (isImported) "mnemonic_imported" else "mnemonic_derived",
                )
            }
            DerivationCurve.Ed25519 -> {
                val node = deriveEd25519(seed, spec.path)
                val privateKey = Ed25519PrivateKeyParameters(node.privateKey, 0)
                val publicKey = privateKey.generatePublicKey().encoded
                DerivedReceiveAccount(
                    networkId = networkId,
                    address = addressFromEd25519(spec, node.privateKey, publicKey),
                    derivationPath = spec.path,
                    derivationName = spec.name,
                    privateKeyHex = node.privateKey.toHex(),
                    publicKeyHex = publicKey.toHex(),
                    compressedPublicKeyHex = publicKey.toHex(),
                    keyFingerprint = hash160(publicKey).take(4).toByteArray().toHex(),
                    isPrimary = spec.isPrimary,
                    addressIndex = spec.addressIndex,
                    isChange = spec.isChange,
                    source = if (isImported) "mnemonic_imported" else "mnemonic_derived",
                )
            }
        }

    private fun addressFromPublicKey(
        spec: ReceiveDerivationSpec,
        compressedPublicKey: ByteArray,
        uncompressedPublicKey: ByteArray,
    ): String =
        when (val format = spec.addressFormat) {
            AddressFormat.Evm -> evmAddress(uncompressedPublicKey)
            AddressFormat.Tron -> base58Check(byteArrayOf(0x41) + evmAddressBytes(uncompressedPublicKey))
            is AddressFormat.BitcoinSegwit -> bech32Segwit(format.hrp, hash160(compressedPublicKey))
            is AddressFormat.BitcoinTaproot -> taprootAddress(format.hrp, compressedPublicKey)
            is AddressFormat.BitcoinNestedSegwit -> nestedSegwitAddress(format.p2shPrefix, compressedPublicKey)
            is AddressFormat.Base58P2pkh -> base58Check(byteArrayOf(format.prefix.toByte()) + hash160(compressedPublicKey))
            is AddressFormat.CashAddr -> cashAddr(format.prefix, hash160(compressedPublicKey))
            AddressFormat.Ripple -> rippleAddress(hash160(compressedPublicKey))
            is AddressFormat.Cosmos -> bech32(format.hrp, hash160(compressedPublicKey))
            else -> error("Unsupported secp256k1 address format: ${spec.addressFormat}")
        }

    private fun addressFromEd25519(
        spec: ReceiveDerivationSpec,
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): String =
        when (val format = spec.addressFormat) {
            AddressFormat.Solana -> base58(publicKey)
            AddressFormat.Aptos -> "0x${sha3_256(publicKey + byteArrayOf(0)).toHex()}"
            AddressFormat.NearImplicit -> publicKey.toHex()
            is AddressFormat.Ss58 -> ss58(format.prefix, publicKey)
            AddressFormat.Stellar -> stellarAddress(publicKey)
            AddressFormat.Sui -> "0x${blake2b256(byteArrayOf(0) + publicKey).toHex()}"
            AddressFormat.TonV4R2 -> tonAddress(privateKey)
            else -> error("Unsupported Ed25519 address format: ${spec.addressFormat}")
        }

    private fun deriveSecp256k1(
        seed: ByteArray,
        path: String,
    ): DerivedNode {
        val master = hmacSha512("Bitcoin seed".toByteArray(Charsets.UTF_8), seed)
        var privateKey = master.copyOfRange(0, 32).toPositiveBigInteger()
        var chainCode = master.copyOfRange(32, 64)
        require(privateKey > BigInteger.ZERO && privateKey < Secp256k1N)
        path.parseBip32Path().forEach { child ->
            val data = if (child.hardened) {
                byteArrayOf(0) + privateKey.toFixedBytes(32) + child.indexBytes
            } else {
                secp256k1PublicKey(privateKey, compressed = true) + child.indexBytes
            }
            val digest = hmacSha512(chainCode, data)
            val left = digest.copyOfRange(0, 32).toPositiveBigInteger()
            val right = digest.copyOfRange(32, 64)
            require(left < Secp256k1N)
            privateKey = left.add(privateKey).mod(Secp256k1N)
            require(privateKey != BigInteger.ZERO)
            chainCode = right
        }
        return DerivedNode(privateKey.toFixedBytes(32), chainCode)
    }

    private fun deriveEd25519(
        seed: ByteArray,
        path: String,
    ): DerivedNode {
        val master = hmacSha512("ed25519 seed".toByteArray(Charsets.UTF_8), seed)
        var privateKey = master.copyOfRange(0, 32)
        var chainCode = master.copyOfRange(32, 64)
        path.parseBip32Path().forEach { child ->
            require(child.hardened) {
                "Ed25519 derivation requires hardened path components: $path"
            }
            val digest = hmacSha512(chainCode, byteArrayOf(0) + privateKey + child.indexBytes)
            privateKey = digest.copyOfRange(0, 32)
            chainCode = digest.copyOfRange(32, 64)
        }
        return DerivedNode(privateKey, chainCode)
    }

    private fun evmAddress(uncompressedPublicKey: ByteArray): String =
        "0x${evmAddressBytes(uncompressedPublicKey).toHex()}"

    private fun evmAddressBytes(uncompressedPublicKey: ByteArray): ByteArray =
        keccak256(uncompressedPublicKey.copyOfRange(1, uncompressedPublicKey.size))
            .takeLast(20)
            .toByteArray()

    private fun bech32Segwit(
        hrp: String,
        publicKeyHash: ByteArray,
    ): String =
        bech32Encode(hrp, byteArrayOf(0) + convertBits(publicKeyHash, 8, 5, true), Bech32Checksum.Bech32)

    private fun taprootAddress(
        hrp: String,
        compressedPublicKey: ByteArray,
    ): String {
        val internalPoint = Secp256k1Params.curve.decodePoint(compressedPublicKey).normalize().let { point ->
            if (point.affineYCoord.toBigInteger().testBit(0)) point.negate().normalize() else point
        }
        val internalKey = internalPoint.affineXCoord.encoded
        val tweak = taggedHash("TapTweak", internalKey).toPositiveBigInteger()
        require(tweak < Secp256k1N)
        val outputPoint = internalPoint.add(Secp256k1G.multiply(tweak)).normalize()
        val outputKey = outputPoint.affineXCoord.encoded
        return bech32Encode(hrp, byteArrayOf(1) + convertBits(outputKey, 8, 5, true), Bech32Checksum.Bech32m)
    }

    private fun nestedSegwitAddress(
        p2shPrefix: Int,
        compressedPublicKey: ByteArray,
    ): String {
        val redeemScript = byteArrayOf(0x00, 0x14) + hash160(compressedPublicKey)
        return base58Check(byteArrayOf(p2shPrefix.toByte()) + hash160(redeemScript))
    }

    private fun bech32(
        hrp: String,
        payload: ByteArray,
    ): String =
        bech32Encode(hrp, convertBits(payload, 8, 5, true), Bech32Checksum.Bech32)

    private fun cashAddr(
        prefix: String,
        publicKeyHash: ByteArray,
    ): String {
        val payload = convertBits(byteArrayOf(0) + publicKeyHash, 8, 5, true)
        val checksum = cashAddrCreateChecksum(prefix, payload)
        return "$prefix:${(payload + checksum).joinToString("") { CASHADDR_CHARSET[it.toInt()].toString() }}"
    }

    private fun rippleAddress(accountId: ByteArray): String =
        base58Check(
            payload = byteArrayOf(0) + accountId,
            alphabet = RIPPLE_BASE58_ALPHABET,
        )

    private fun ss58(
        prefix: Int,
        publicKey: ByteArray,
    ): String {
        val data = byteArrayOf(prefix.toByte()) + publicKey
        val checksum = blake2b512("SS58PRE".toByteArray(Charsets.US_ASCII) + data).copyOfRange(0, 2)
        return base58(data + checksum)
    }

    private fun stellarAddress(publicKey: ByteArray): String {
        val payload = byteArrayOf((6 shl 3).toByte()) + publicKey
        val checksum = crc16Xmodem(payload)
        return base32(payload + byteArrayOf((checksum and 0xff).toByte(), ((checksum ushr 8) and 0xff).toByte()))
    }

    private fun tonAddress(privateKey: ByteArray): String {
        val tonPrivateKey = PrivateKeyEd25519(privateKey)
        val address = WalletV4R2Contract.address(tonPrivateKey)
        return address.toString(userFriendly = true, urlSafe = true, testOnly = false, bounceable = false)
    }

    private fun secp256k1PublicKey(
        privateKey: BigInteger,
        compressed: Boolean,
    ): ByteArray =
        Secp256k1G.multiply(privateKey).normalize().getEncoded(compressed)

    private fun hmacSha512(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    private fun hash160(data: ByteArray): ByteArray =
        ripemd160(sha256(data))

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun doubleSha256(data: ByteArray): ByteArray =
        sha256(sha256(data))

    private fun taggedHash(
        tag: String,
        data: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.US_ASCII))
        return sha256(tagHash + tagHash + data)
    }

    private fun ripemd160(data: ByteArray): ByteArray {
        val digest = RIPEMD160Digest()
        digest.update(data, 0, data.size)
        return ByteArray(20).also { output -> digest.doFinal(output, 0) }
    }

    private fun keccak256(data: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output -> digest.doFinal(output, 0) }
    }

    private fun sha3_256(data: ByteArray): ByteArray {
        val digest = SHA3Digest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output -> digest.doFinal(output, 0) }
    }

    private fun blake2b256(data: ByteArray): ByteArray {
        val digest = Blake2bDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { output -> digest.doFinal(output, 0) }
    }

    private fun blake2b512(data: ByteArray): ByteArray {
        val digest = Blake2bDigest(512)
        digest.update(data, 0, data.size)
        return ByteArray(64).also { output -> digest.doFinal(output, 0) }
    }

    private fun base58Check(
        payload: ByteArray,
        alphabet: String = BITCOIN_BASE58_ALPHABET,
    ): String =
        base58(payload + doubleSha256(payload).copyOfRange(0, 4), alphabet)

    private fun base58(
        data: ByteArray,
        alphabet: String = BITCOIN_BASE58_ALPHABET,
    ): String {
        var value = BigInteger(1, data)
        val base = BigInteger.valueOf(58)
        val builder = StringBuilder()
        while (value > BigInteger.ZERO) {
            val divRem = value.divideAndRemainder(base)
            builder.append(alphabet[divRem[1].toInt()])
            value = divRem[0]
        }
        data.takeWhile { it == 0.toByte() }.forEach { _ -> builder.append(alphabet[0]) }
        return builder.reverse().toString()
    }

    private fun base32(data: ByteArray): String {
        var buffer = 0
        var bitsLeft = 0
        val output = StringBuilder()
        data.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                output.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            output.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 31])
        }
        return output.toString()
    }

    private fun bech32Encode(
        hrp: String,
        data: ByteArray,
        checksumType: Bech32Checksum,
    ): String {
        val checksum = bech32CreateChecksum(hrp, data, checksumType)
        return "${hrp}1${(data + checksum).joinToString("") { BECH32_CHARSET[it.toInt()].toString() }}"
    }

    private fun bech32CreateChecksum(
        hrp: String,
        data: ByteArray,
        checksumType: Bech32Checksum,
    ): ByteArray {
        val values = bech32HrpExpand(hrp) + data + ByteArray(6)
        val polymod = bech32Polymod(values) xor checksumType.constant
        return ByteArray(6) { index ->
            ((polymod shr (5 * (5 - index))) and 31).toByte()
        }
    }

    private fun bech32HrpExpand(hrp: String): ByteArray =
        hrp.map { (it.code shr 5).toByte() }.toByteArray() +
            byteArrayOf(0) +
            hrp.map { (it.code and 31).toByte() }.toByteArray()

    private fun bech32Polymod(values: ByteArray): Int {
        var chk = 1
        values.forEach { value ->
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor (value.toInt() and 0xff)
            BECH32_GENERATOR.forEachIndexed { index, generator ->
                if (((top ushr index) and 1) != 0) {
                    chk = chk xor generator
                }
            }
        }
        return chk
    }

    private fun cashAddrCreateChecksum(
        prefix: String,
        payload: ByteArray,
    ): ByteArray {
        val values = prefix.map { (it.code and 0x1f).toByte() }.toByteArray() +
            byteArrayOf(0) +
            payload +
            ByteArray(8)
        val polymod = cashAddrPolymod(values) xor 1
        return ByteArray(8) { index ->
            ((polymod shr (5 * (7 - index))) and 31).toByte()
        }
    }

    private fun cashAddrPolymod(values: ByteArray): Long {
        var checksum = 1L
        values.forEach { value ->
            val top = checksum ushr 35
            checksum = ((checksum and 0x07ffffffffL) shl 5) xor (value.toLong() and 0xff)
            CASHADDR_GENERATOR.forEachIndexed { index, generator ->
                if (((top ushr index) and 1L) != 0L) {
                    checksum = checksum xor generator
                }
            }
        }
        return checksum xor 1
    }

    private fun convertBits(
        data: ByteArray,
        fromBits: Int,
        toBits: Int,
        pad: Boolean,
    ): ByteArray {
        var accumulator = 0
        var bits = 0
        val maxValue = (1 shl toBits) - 1
        val output = mutableListOf<Byte>()
        data.forEach { value ->
            accumulator = (accumulator shl fromBits) or (value.toInt() and ((1 shl fromBits) - 1))
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                output.add(((accumulator shr bits) and maxValue).toByte())
            }
        }
        if (pad && bits > 0) {
            output.add(((accumulator shl (toBits - bits)) and maxValue).toByte())
        }
        return output.toByteArray()
    }

    private fun crc16Xmodem(data: ByteArray): Int {
        var crc = 0
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xff) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xffff
                } else {
                    (crc shl 1) and 0xffff
                }
            }
        }
        return crc and 0xffff
    }

    private fun String.parseBip32Path(): List<Bip32ChildNumber> {
        require(startsWith("m")) { "BIP32 path must start with m." }
        if (this == "m") return emptyList()
        return removePrefix("m/")
            .split("/")
            .filter(String::isNotBlank)
            .map { segment ->
                val hardened = segment.endsWith("'") || segment.endsWith("h") || segment.endsWith("H")
                val index = segment
                    .removeSuffix("'")
                    .removeSuffix("h")
                    .removeSuffix("H")
                    .toLong()
                require(index in 0L..BIP32_MAX_NORMAL_INDEX)
                Bip32ChildNumber(index = index.toInt(), hardened = hardened)
            }
    }

    private fun BigInteger.toFixedBytes(size: Int): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == size -> bytes
            bytes.size == size + 1 && bytes.first() == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
            bytes.size < size -> ByteArray(size - bytes.size) + bytes
            else -> error("Integer does not fit in $size bytes.")
        }
    }

    private fun ByteArray.toPositiveBigInteger(): BigInteger =
        BigInteger(1, this)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private val Secp256k1Params = SECNamedCurves.getByName("secp256k1")
    private val Secp256k1G = Secp256k1Params.g
    private val Secp256k1N = Secp256k1Params.n
    private const val BIP32_HARDENED_OFFSET = 0x80000000L
    private const val BIP32_MAX_NORMAL_INDEX = 0x7fffffffL
    private const val BITCOIN_BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val RIPPLE_BASE58_ALPHABET = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"
    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val CASHADDR_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val BECH32_GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private val CASHADDR_GENERATOR = longArrayOf(
        0x98f2bc8e61L,
        0x79b76d99e2L,
        0xf33e5fb3c4L,
        0xae2eabe2a8L,
        0x1e4f43e470L,
    )

    private data class UtxoScanTemplate(
        val name: String,
        val purpose: Int,
        val coinType: Int,
        val format: AddressFormat,
    )

    private data class DerivedNode(
        val privateKey: ByteArray,
        val chainCode: ByteArray,
    )

    private data class Bip32ChildNumber(
        val index: Int,
        val hardened: Boolean,
    ) {
        val indexBytes: ByteArray
            get() {
                val encoded = if (hardened) index.toLong() + BIP32_HARDENED_OFFSET else index.toLong()
                return byteArrayOf(
                    ((encoded ushr 24) and 0xff).toByte(),
                    ((encoded ushr 16) and 0xff).toByte(),
                    ((encoded ushr 8) and 0xff).toByte(),
                    (encoded and 0xff).toByte(),
                )
            }
    }
}

data class DerivedReceiveAccount(
    val networkId: String,
    val address: String,
    val derivationPath: String?,
    val derivationName: String,
    val privateKeyHex: String,
    val publicKeyHex: String,
    val compressedPublicKeyHex: String,
    val keyFingerprint: String,
    val isPrimary: Boolean,
    val addressIndex: Int,
    val isChange: Boolean = false,
    val source: String,
)

data class ReceiveDerivationSpec(
    val networkId: String,
    val name: String,
    val path: String,
    val curve: DerivationCurve,
    val addressFormat: AddressFormat,
    val isPrimary: Boolean,
    val addressIndex: Int = 0,
    val isChange: Boolean = false,
)

enum class DerivationCurve {
    Secp256k1,
    Ed25519,
}

sealed interface AddressFormat {
    data object Evm : AddressFormat
    data object Tron : AddressFormat
    data object Ripple : AddressFormat
    data object Solana : AddressFormat
    data object Aptos : AddressFormat
    data object NearImplicit : AddressFormat
    data object Stellar : AddressFormat
    data object Sui : AddressFormat
    data object TonV4R2 : AddressFormat
    data class BitcoinSegwit(val hrp: String) : AddressFormat
    data class BitcoinTaproot(val hrp: String) : AddressFormat
    data class BitcoinNestedSegwit(val p2shPrefix: Int) : AddressFormat
    data class Base58P2pkh(val prefix: Int) : AddressFormat
    data class CashAddr(val prefix: String) : AddressFormat
    data class Cosmos(val hrp: String) : AddressFormat
    data class Ss58(val prefix: Int) : AddressFormat
}

private enum class Bech32Checksum(val constant: Int) {
    Bech32(1),
    Bech32m(0x2bc830a3),
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
