#!/usr/bin/env python3
"""Find likely user-visible hardcoded strings in Kotlin and copied English XML values."""

from __future__ import annotations

import argparse
import re
import sys
from html import unescape
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/java/dev/satra/wallet/ui"
BASE_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
RES_ROOT = ROOT / "app/src/main/res"
LOCALES = {
    "zh-Hans": "values-b+zh+Hans",
    "hi": "values-hi",
    "es": "values-es",
    "ar": "values-ar",
    "fr": "values-fr",
    "bn": "values-bn",
    "pt": "values-pt",
    "id": "values-id",
    "ur": "values-ur",
    "ru": "values-ru",
    "de": "values-de",
    "ja": "values-ja",
    "pcm": "values-b+pcm",
    "arz": "values-b+arz",
    "mr": "values-mr",
    "vi": "values-vi",
    "te": "values-te",
    "sw": "values-sw",
    "ha": "values-ha",
    "tr": "values-tr",
    "pnb": "values-b+pnb",
    "fil": "values-fil",
    "ta": "values-ta",
    "yue-Hant": "values-b+yue+Hant",
}

VISIBLE_CONTEXT_RE = re.compile(
    r"(Text\(\s*\"|Toast\.makeText\([^,]+,\s*\"|"
    r"contentDescription\s*=\s*\"|placeholder\s*=\s*\"|"
    r"label\s*=\s*\"|title\s*=\s*\"|body\s*=\s*\"|text\s*=\s*\")"
)
STRING_RE = re.compile(r"<string\s+([^>]*?)>(.*?)</string>", re.DOTALL)
ATTR_RE = re.compile(r"(\w+)=\"(.*?)\"")
FORMAT_RE = re.compile(r"%(?:\d+\$)?[sdif]")

IGNORED_LITERAL_VALUES = {
    "0",
    "receiptStatusChip",
    "receiptStatusDotAlpha",
}
IGNORED_XML_KEY_PATTERNS = (
    re.compile(r"^settings_currency_name_"),
    re.compile(
        r"^settings_language_(english|chinese_simplified|hindi|spanish|french|"
        r"arabic|bengali|portuguese|russian|urdu|indonesian|german|"
        r"japanese|nigerian_pidgin|egyptian_arabic|swahili|marathi|"
        r"telugu|hausa|turkish|western_punjabi|filipino|tamil|"
        r"cantonese|vietnamese|korean|persian|italian|thai|gujarati|"
        r"polish)$"
    ),
    re.compile(r"^settings_country_"),
    re.compile(r"^home_balance_currency_"),
    re.compile(
        r"^wallet_setup_network_(bitcoin|bitcoin_cash|dogecoin|litecoin|ethereum|"
        r"arbitrum|base|optimism|scroll|zksync|polygon|bnb_chain|opbnb|"
        r"avalanche|celo|kava_evm|aptos|polkadot|xrp_ledger|solana|"
        r"stellar|sui|kava)$"
    ),
)
IGNORED_XML_KEYS = {
    "app_name",
    "onboarding_receive_address_preview",
    "send_receipt_footer_brand",
    "wallet_symbol",
}


def is_visible_literal(value: str) -> bool:
    stripped = value.strip()
    if not stripped or stripped in IGNORED_LITERAL_VALUES:
        return False
    if "${" in stripped or "stringResource(" in stripped:
        return False
    if not re.search(r"[A-Za-z]", stripped):
        return False
    return True


def kotlin_findings() -> list[str]:
    findings: list[str] = []
    for path in sorted(UI_ROOT.rglob("*.kt")):
        rel = path.relative_to(ROOT)
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if not VISIBLE_CONTEXT_RE.search(line):
                continue
            for value in re.findall(r'"((?:[^"\\]|\\.)*)"', line):
                if is_visible_literal(value):
                    findings.append(f"{rel}:{line_number}: {value}")
    return findings


def load_strings(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    text = path.read_text(encoding="utf-8")
    for match in STRING_RE.finditer(text):
        attrs = dict(ATTR_RE.findall(match.group(1)))
        if attrs.get("translatable") == "false":
            continue
        value = re.sub(r"<[^>]+>", "", match.group(2))
        values[attrs["name"]] = unescape(value)
    return values


def is_expected_copied_value(key: str, source: str, locale: str) -> bool:
    if key in IGNORED_XML_KEYS:
        return True
    if locale == "pcm":
        return True
    if any(pattern.search(key) for pattern in IGNORED_XML_KEY_PATTERNS):
        return True
    compact = FORMAT_RE.sub("", source).strip()
    if not compact:
        return True
    if re.fullmatch(r"[\W\d_]+", compact):
        return True
    if re.fullmatch(r"[A-Z0-9]{2,8}", compact):
        return True
    if compact in {"Satra", "GitHub", "USD", "ALL", "1D", "1W", "1M", "1Y"}:
        return True
    return False


def copied_english_warnings() -> list[str]:
    base = load_strings(BASE_STRINGS)
    warnings: list[str] = []
    for locale, folder in LOCALES.items():
        localized_path = RES_ROOT / folder / "strings.xml"
        localized = load_strings(localized_path)
        for key, source in base.items():
            value = localized.get(key)
            if value is None or value != source:
                continue
            if is_expected_copied_value(key, source, locale):
                continue
            warnings.append(f"{locale}:{key}: {source}")
    return warnings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fail-on-copied-english",
        action="store_true",
        help="Treat copied English XML values as failures instead of warnings.",
    )
    args = parser.parse_args()

    hardcoded = kotlin_findings()
    copied = copied_english_warnings()
    if hardcoded:
        print("Likely user-visible hardcoded Kotlin strings:")
        print("\n".join(hardcoded))
    if copied:
        print("Localized XML values copied from English:")
        print("\n".join(copied[:200]))
        if len(copied) > 200:
            print(f"...and {len(copied) - 200} more")
    if hardcoded or (args.fail_on_copied_english and copied):
        return 1
    print("No likely user-visible hardcoded Kotlin strings found.")
    if copied:
        print(f"{len(copied)} copied-English XML values remain as warnings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
