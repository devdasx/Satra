package dev.satra.wallet.scanner

import dev.satra.wallet.wallet.bip39.Bip39MnemonicValidator
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

enum class SatraScanPurpose(val routeSegment: String) {
    Any("any"),
    RecoveryPhrase("recovery-phrase"),
    Address("address"),
    Payment("payment");

    companion object {
        fun fromRoute(routeSegment: String?): SatraScanPurpose =
            entries.firstOrNull { it.routeSegment == routeSegment } ?: Any
    }
}

enum class SatraScanKind {
    RecoveryPhrase,
    PaymentUri,
    Address,
    Raw,
}

data class SatraScanResult(
    val rawValue: String,
    val kind: SatraScanKind,
    val normalizedValue: String,
    val amount: String? = null,
    val scheme: String? = null,
)

object SatraScanParser {
    fun parse(rawValue: String): SatraScanResult? {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val normalizedMnemonic = normalizeMnemonicCandidate(trimmed)
        if (Bip39MnemonicValidator.validate(normalizedMnemonic).isValid) {
            return SatraScanResult(
                rawValue = rawValue,
                kind = SatraScanKind.RecoveryPhrase,
                normalizedValue = normalizedMnemonic,
            )
        }

        parsePaymentUri(trimmed)?.let { paymentResult ->
            return paymentResult
        }

        return if (looksLikeAddress(trimmed)) {
            SatraScanResult(
                rawValue = rawValue,
                kind = SatraScanKind.Address,
                normalizedValue = trimmed,
            )
        } else {
            SatraScanResult(
                rawValue = rawValue,
                kind = SatraScanKind.Raw,
                normalizedValue = trimmed,
            )
        }
    }

    fun parseForPurpose(
        rawValue: String,
        purpose: SatraScanPurpose,
    ): SatraScanResult? {
        val result = parse(rawValue) ?: return null

        return when (purpose) {
            SatraScanPurpose.Any -> result
            SatraScanPurpose.RecoveryPhrase -> result.takeIf {
                it.kind == SatraScanKind.RecoveryPhrase
            }
            SatraScanPurpose.Address -> result.takeIf {
                it.kind == SatraScanKind.Address || it.kind == SatraScanKind.PaymentUri
            }
            SatraScanPurpose.Payment -> result.takeIf {
                it.kind == SatraScanKind.PaymentUri || it.kind == SatraScanKind.Address
            }
        }
    }

    private fun parsePaymentUri(value: String): SatraScanResult? {
        val schemeDivider = value.indexOf(':')
        if (schemeDivider <= 0) {
            return null
        }

        val scheme = value.substring(0, schemeDivider).lowercase(Locale.US)
        if (scheme !in paymentSchemes) {
            return null
        }

        val payload = value.substring(schemeDivider + 1)
        val address = payload.substringBefore('?')
            .removePrefix("//")
            .trim()
        if (address.isBlank()) {
            return null
        }

        val query = payload.substringAfter('?', missingDelimiterValue = "")
        val amount = parseQuery(query)["amount"] ?: parseQuery(query)["value"]

        return SatraScanResult(
            rawValue = value,
            kind = SatraScanKind.PaymentUri,
            normalizedValue = address,
            amount = amount,
            scheme = scheme,
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) {
            return emptyMap()
        }

        return query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=').takeIf(String::isNotBlank) ?: return@mapNotNull null
                val value = part.substringAfter('=', missingDelimiterValue = "")
                decodeUrl(key).lowercase(Locale.US) to decodeUrl(value)
            }
            .toMap()
    }

    private fun decodeUrl(value: String): String =
        runCatching {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        }.getOrDefault(value)

    private fun normalizeMnemonicCandidate(value: String): String {
        val normalized = Normalizer.normalize(
            value.lowercase(Locale.US),
            Normalizer.Form.NFKD,
        )
        return normalized.split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(separator = " ")
    }

    private fun looksLikeAddress(value: String): Boolean {
        if (value.any(Char::isWhitespace) || value.length < MIN_ADDRESS_LENGTH) {
            return false
        }

        return addressPatterns.any { pattern -> pattern.matches(value) }
    }

    private val paymentSchemes = setOf(
        "aptos",
        "arbitrum",
        "avalanche",
        "base",
        "bitcoin",
        "bitcoincash",
        "bnb",
        "celo",
        "dogecoin",
        "ethereum",
        "kava",
        "litecoin",
        "near",
        "optimism",
        "polygon",
        "polkadot",
        "ripple",
        "scroll",
        "solana",
        "stellar",
        "sui",
        "ton",
        "tron",
        "xrp",
        "zksync",
    )

    private val addressPatterns = listOf(
        Regex("^(bc1|tb1|[13mn2])[a-zA-HJ-NP-Z0-9]{20,90}$"),
        Regex("^(bitcoincash:)?(q|p)[a-z0-9]{40,90}$", RegexOption.IGNORE_CASE),
        Regex("^[LM3][a-km-zA-HJ-NP-Z1-9]{26,90}$"),
        Regex("^D{1}[5-9A-HJ-NP-U]{1}[1-9A-HJ-NP-Za-km-z]{32}$"),
        Regex("^0x[a-fA-F0-9]{40}$"),
        Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"),
        Regex("^r[1-9A-HJ-NP-Za-km-z]{24,34}$"),
        Regex("^G[A-Z2-7]{55}$"),
        Regex("^EQ[A-Za-z0-9_-]{46,64}$"),
        Regex("^0x[a-fA-F0-9]{64}$"),
        Regex("^[1-9A-HJ-NP-Za-km-z]{32,64}$"),
    )

    private const val MIN_ADDRESS_LENGTH = 8
}
