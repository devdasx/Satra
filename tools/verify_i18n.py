#!/usr/bin/env python3
"""Verify Android string resource parity for Satra locale folders."""

from __future__ import annotations

import re
import sys
from collections import Counter
from html import unescape
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASE_FILE = ROOT / "app/src/main/res/values/strings.xml"
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
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[sdif]")
STRING_RE = re.compile(r"<string\s+([^>]*?)>(.*?)</string>", re.DOTALL)
ATTR_RE = re.compile(r"(\w+)=\"(.*?)\"")


def load_strings(path: Path, include_untranslatable: bool = False) -> dict[str, str]:
    values: dict[str, str] = {}
    text = path.read_text(encoding="utf-8")
    for match in STRING_RE.finditer(text):
        attrs = dict(ATTR_RE.findall(match.group(1)))
        if not include_untranslatable and attrs.get("translatable") == "false":
            continue
        name = attrs["name"]
        value = re.sub(r"<[^>]+>", "", match.group(2))
        values[name] = unescape(value)
    return values


def placeholders(value: str) -> list[str]:
    return PLACEHOLDER_RE.findall(value)


def main() -> int:
    base = load_strings(BASE_FILE)
    failures: list[str] = []
    for tag, folder in LOCALES.items():
        path = ROOT / "app/src/main/res" / folder / "strings.xml"
        if not path.exists():
            failures.append(f"{tag}: missing {path.relative_to(ROOT)}")
            continue
        localized = load_strings(path, include_untranslatable=True)
        missing = sorted(set(base) - set(localized))
        extra = sorted(set(localized) - set(base))
        if missing:
            failures.append(f"{tag}: missing {len(missing)} keys: {', '.join(missing[:12])}")
        if extra:
            failures.append(f"{tag}: extra {len(extra)} keys: {', '.join(extra[:12])}")
        for name, source in base.items():
            if name not in localized:
                continue
            if Counter(placeholders(source)) != Counter(placeholders(localized[name])):
                failures.append(
                    f"{tag}: placeholder mismatch for {name}: "
                    f"{placeholders(source)} != {placeholders(localized[name])}"
                )
    if failures:
        print("\n".join(failures))
        return 1
    print(f"Verified {len(base)} translatable keys across {len(LOCALES)} locales.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
