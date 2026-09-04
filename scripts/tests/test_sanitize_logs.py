import unittest

from scripts.sanitize_logs import sanitize_text


class SanitizeLogsTests(unittest.TestCase):
    def test_redacts_credentials_and_personal_data(self) -> None:
        raw = (
            'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature '
            'APP_INTERNAL_API_TOKEN=demo-token password="secret" '
            'email=golden_user@example.com phone=13800138000'
        )

        sanitized = sanitize_text(raw)

        for secret in ("eyJhbGci", "demo-token", "secret", "golden_user@example.com", "13800138000"):
            self.assertNotIn(secret, sanitized)
        self.assertGreaterEqual(sanitized.count("[REDACTED]"), 5)


if __name__ == "__main__":
    unittest.main()
