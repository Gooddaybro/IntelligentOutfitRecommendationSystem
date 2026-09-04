"""Remove credentials and basic personal data from CI diagnostic logs."""

import re
import sys


PATTERNS = (
    (re.compile(r"(?i)(authorization\s*:\s*bearer\s+)[^\s,;]+"), r"\1[REDACTED]"),
    (re.compile(r"(?i)([\w.-]*(?:password|secret|token|api[_-]?key)[\w.-]*\s*[:=]\s*[\"']?)[^\"'\s,;}]+"), r"\1[REDACTED]"),
    (re.compile(r"(?i)([a-z][a-z0-9+._-]*://[^:/\s]+:)[^@/\s]+(@)"), r"\1[REDACTED]\2"),
    (re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"), "[REDACTED]"),
    (re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)"), "[REDACTED]"),
    (re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b"), "[REDACTED]"),
)


def sanitize_text(value: str) -> str:
    """Return log text with common secret and personal-data forms removed."""
    for pattern, replacement in PATTERNS:
        value = pattern.sub(replacement, value)
    return value


if __name__ == "__main__":
    sys.stdout.write(sanitize_text(sys.stdin.read()))
