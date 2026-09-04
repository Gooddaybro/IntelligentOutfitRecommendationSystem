from pathlib import Path
import unittest


PROJECT_DIR = Path(__file__).resolve().parents[2]


class GoldenPathConfigurationTests(unittest.TestCase):
    def test_integration_spec_does_not_intercept_browser_http(self) -> None:
        spec = (PROJECT_DIR / "frontend" / "e2e" / "integration" / "golden-path.spec.ts").read_text(
            encoding="utf-8"
        )

        self.assertNotIn("page.route", spec)
        self.assertNotIn("routeFromHAR", spec)
        self.assertIn("/api/assistant/chat/stream", spec)
        self.assertIn("Idempotency-Key", spec)

    def test_cross_platform_runners_enable_integration_provider_and_clean_volumes(self) -> None:
        shell_runner = (PROJECT_DIR / "scripts" / "run-golden-path.sh").read_text(encoding="utf-8")
        windows_runner = (PROJECT_DIR / "scripts" / "run-golden-path.ps1").read_text(encoding="utf-8")

        for runner in (shell_runner, windows_runner):
            self.assertIn("AI_RUNTIME_ENV", runner)
            self.assertIn("AI_DETERMINISTIC_PROVIDER", runner)
            self.assertIn("MYSQL_HOST_PORT", runner)
            self.assertIn("JAVA_BACKEND_HOST_PORT", runner)
            self.assertIn("down", runner)
            self.assertIn("--volumes", runner)


if __name__ == "__main__":
    unittest.main()
